package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.audio.WavAudioUtils
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioSourceType
import com.example.data.local.VoiceProfileDao
import com.example.data.local.VoiceProfileEntity
import com.example.data.local.VoiceProviderType
import com.example.data.preferences.SettingsDataStore
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

private const val TAG = "GeminiTtsRepository"
private const val GEMINI_TTS_MODEL = "gemini-3.1-flash-tts-preview"
private const val GEMINI_ANALYSIS_MODEL = "gemini-3.8-flash"

data class GeminiPresetVoice(
    val voiceName: String,
    val genderLabel: String,
    val toneEn: String,
    val toneBn: String
)

data class SpeechLanguageOption(
    val code: String,
    val explicitApiCode: String?, // null lets Gemini auto-detect bn-BD naturally
    val labelEn: String,
    val labelBn: String
)

sealed interface GeminiTtsResult {
    data class Success(val clip: AudioClipEntity) : GeminiTtsResult
    data class Error(val message: String, val isQuotaOrAuthError: Boolean = false) : GeminiTtsResult
}

class GeminiTtsRepository(
    private val context: Context,
    private val audioClipDao: AudioClipDao,
    private val voiceProfileDao: VoiceProfileDao,
    private val settingsDataStore: SettingsDataStore
) {
    val supportedLanguages = listOf(
        SpeechLanguageOption("bn-BD", null, "Bangla (Bangladesh)", "বাংলা (বাংলাদেশ)"),
        SpeechLanguageOption("bn-IN", "bn-IN", "Bangla (India)", "বাংলা (ভারত)"),
        SpeechLanguageOption("en-US", "en-US", "English (US)", "ইংরেজি (যুক্তরাষ্ট্র)"),
        SpeechLanguageOption("en-GB", "en-GB", "English (UK)", "ইংরেজি (যুক্তরাজ্য)"),
        SpeechLanguageOption("en-IN", "en-IN", "English (India)", "ইংরেজি (ভারত)")
    )

    val presetVoices = listOf(
        GeminiPresetVoice("Kore", "Female", "Firm & Studio Clear", "দৃঢ় ও স্পষ্ট"),
        GeminiPresetVoice("Zephyr", "Female", "Bright & Expressive", "উজ্জ্বল ও প্রাণবন্ত"),
        GeminiPresetVoice("Sulafat", "Female", "Warm & Conversational", "আন্তরিক ও সাবলীল"),
        GeminiPresetVoice("Leda", "Female", "Youthful & Crisp", "তরুণ ও সতেজ"),
        GeminiPresetVoice("Aoede", "Female", "Breezy & Melodic", "মধুর ও সহজ"),
        GeminiPresetVoice("Puck", "Male", "Upbeat & Energetic", "উৎসাহী ও চটপটে"),
        GeminiPresetVoice("Charon", "Male", "Informative & Calm", "তথ্যপূর্ণ ও শান্ত"),
        GeminiPresetVoice("Orus", "Male", "Firm & Authoritative", "গভীর ও স্পষ্ট"),
        GeminiPresetVoice("Fenrir", "Male", "Dynamic & Dramatic", "উত্তেজনাপূর্ণ ও নাটকীয়"),
        GeminiPresetVoice("Iapetus", "Male", "Clear Narrator", "পরিষ্কার বর্ণনাকারী"),
        GeminiPresetVoice("Schedar", "Male", "Balanced & Even", "সুষম ও স্থির"),
        GeminiPresetVoice("Erinome", "Female", "Clear Documentary", "প্রামাণ্য ও পরিষ্কার")
    )

    suspend fun ensureDefaultVoicesSeeded() = withContext(Dispatchers.IO) {
        if (voiceProfileDao.getVoiceCount() == 0) {
            val presets = presetVoices.take(6).map { preset ->
                VoiceProfileEntity(
                    name = "${preset.voiceName} · ${preset.toneEn}",
                    providerType = VoiceProviderType.GEMINI_PRESET.name,
                    languageCode = "bn-BD / en-US",
                    geminiVoiceName = preset.voiceName,
                    styleDescriptor = "${preset.genderLabel} · ${preset.toneEn}",
                    sampleFilePath = null,
                    consentConfirmed = true,
                    consentOwnerType = "PRESET",
                    consentNote = "Built-in Gemini TTS studio voice preset"
                )
            }
            voiceProfileDao.insertVoices(presets)
        }
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun generateSpeech(
        text: String,
        languageOption: SpeechLanguageOption,
        voiceName: String,
        speed: Float,
        pitch: Float
    ): GeminiTtsResult = withContext(Dispatchers.IO) {
        try {
            val config = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = if (languageOption.explicitApiCode != null) {
                    SpeechConfig(
                        voice = Voice(voiceName),
                        languageCode = languageOption.explicitApiCode
                    )
                } else {
                    SpeechConfig(voice = Voice(voiceName))
                }
            }

            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = GEMINI_TTS_MODEL,
                generationConfig = config
            )

            val paceDirective = when {
                speed <= 0.75f -> "at a slow, deliberate pace"
                speed < 0.92f -> "at a slightly relaxed pace"
                speed > 1.35f -> "at a brisk, fast pace"
                speed > 1.08f -> "at a slightly lively pace"
                else -> "at a natural pace"
            }
            val pitchDirective = when {
                pitch <= 0.85f -> "with a slightly deeper, warmer vocal tone"
                pitch >= 1.15f -> "with a slightly brighter, higher vocal tone"
                else -> "with a natural vocal tone"
            }

            val langHint = if (languageOption.code.startsWith("bn")) {
                "Speak naturally in Bengali (Bangla)"
            } else {
                "Speak naturally in English"
            }

            val prompt = "$langHint $paceDirective and $pitchDirective. Read the exact text below aloud without adding extra commentary:\n\n$text"

            settingsDataStore.incrementGeminiRequestCount()

            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }

            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val audioPart = parts.filterIsInstance<InlineDataPart>().firstOrNull {
                it.mimeType.startsWith("audio/") || it.inlineData.isNotEmpty()
            }

            if (audioPart == null || audioPart.inlineData.isEmpty()) {
                return@withContext GeminiTtsResult.Error(
                    "Gemini TTS returned no audio bytes. Please verify your text and try again."
                )
            }

            val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val wavFile = File(audioDir, "gemini_${voiceName.lowercase()}_$timestamp.wav")

            // Gemini TTS returns 24kHz, 16-bit, mono raw PCM (or wrapped WAV if audio/wav)
            val rawBytes = audioPart.inlineData
            val isAlreadyWav = rawBytes.size >= 12 &&
                String(rawBytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(rawBytes, 8, 4, Charsets.US_ASCII) == "WAVE"

            if (isAlreadyWav) {
                wavFile.writeBytes(rawBytes)
            } else {
                WavAudioUtils.pcmToWavFile(
                    pcmBytes = rawBytes,
                    sampleRate = 24_000,
                    channels = 1,
                    bitsPerSample = 16,
                    outputFile = wavFile
                )
            }

            val wavInfo = WavAudioUtils.readWavInfo(wavFile)
            val durationMs = wavInfo?.durationMs ?: ((rawBytes.size * 1000L) / (24_000L * 2L))
            val snippet = text.trim().replace("\n", " ").take(42).let {
                if (text.trim().length > 42) "$it…" else it
            }

            val entity = AudioClipEntity(
                title = "$voiceName · $snippet",
                textPrompt = text.trim(),
                languageCode = languageOption.code,
                voiceLabel = "Gemini · $voiceName",
                sourceType = AudioSourceType.ONLINE_GEMINI.name,
                speed = speed,
                pitch = pitch,
                durationMs = durationMs,
                wavFilePath = wavFile.absolutePath,
                mp3FilePath = null,
                isDownloaded = false,
                fileSizeBytes = wavFile.length(),
                isVerifiedPlayable = wavInfo != null && durationMs > 0L,
                createdAt = timestamp
            )
            val insertedId = audioClipDao.insertClip(entity)
            GeminiTtsResult.Success(entity.copy(id = insertedId))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini TTS timed out", e)
            GeminiTtsResult.Error("Gemini TTS timed out after 60 seconds. Check your connection and retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini TTS failed", e)
            val rawMsg = e.localizedMessage ?: e.javaClass.simpleName
            val isQuotaOrAuth = rawMsg.contains("429", ignoreCase = true) ||
                rawMsg.contains("quota", ignoreCase = true) ||
                rawMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                rawMsg.contains("403", ignoreCase = true) ||
                rawMsg.contains("App Check", ignoreCase = true)
            val friendlyMsg = when {
                rawMsg.contains("429", ignoreCase = true) ||
                    rawMsg.contains("quota", ignoreCase = true) ||
                    rawMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                    "Gemini API quota limit reached (HTTP 429 / RESOURCE_EXHAUSTED). Please wait and try again later, or use Offline Voice Studio."
                rawMsg.contains("403", ignoreCase = true) ->
                    "Firebase App Check or API authorization failed (HTTP 403): $rawMsg"
                rawMsg.contains("UnknownHost", ignoreCase = true) ||
                    rawMsg.contains("Unable to resolve host", ignoreCase = true) ||
                    rawMsg.contains("network", ignoreCase = true) ->
                    "Network connection failed while reaching Gemini TTS. Switch to Offline Voice Studio or check your internet."
                else -> "Gemini TTS error: $rawMsg"
            }
            GeminiTtsResult.Error(friendlyMsg, isQuotaOrAuthError = isQuotaOrAuth)
        }
    }

    suspend fun analyzeSampleTimbre(wavFile: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!wavFile.exists() || wavFile.length() == 0L) {
                throw IllegalStateException("Voice sample file is missing.")
            }
            if (wavFile.length() > 18L * 1024L * 1024L) {
                throw IllegalStateException("Voice sample exceeds 18 MB inline analysis limit.")
            }

            val model by lazy {
                Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(GEMINI_ANALYSIS_MODEL)
            }
            val bytes = wavFile.readBytes()
            settingsDataStore.incrementGeminiRequestCount()

            val response = withTimeout(45_000L) {
                model.generateContent(
                    content {
                        inlineData(bytes, "audio/wav")
                        text(
                            "Briefly describe this speaker's vocal pitch, pace, and warmth in 2 sentences, " +
                                "and suggest the closest prebuilt Gemini TTS voice from [Kore, Zephyr, Sulafat, Leda, Aoede, Puck, Charon, Orus, Fenrir, Iapetus]. " +
                                "Explicitly note that prebuilt voices are studio presets, not a clone of the speaker."
                        )
                    }
                )
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No timbre analysis text returned.")
        }
    }
}
