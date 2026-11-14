package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.audio.WavAudioUtils
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioSourceType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.UUID

private const val TAG = "OfflineTtsRepository"

data class OfflineVoiceInfo(
    val name: String,
    val localeTag: String,
    val displayLanguage: String,
    val isNetworkRequired: Boolean,
    val quality: Int
)

data class LanguageAvailability(
    val localeTag: String,
    val labelEn: String,
    val labelBn: String,
    val isAvailableOffline: Boolean,
    val statusCode: Int
)

data class OfflineTtsStatus(
    val isReady: Boolean = false,
    val initError: String? = null,
    val enginePackage: String? = null,
    val installedVoices: List<OfflineVoiceInfo> = emptyList(),
    val languageAvailability: List<LanguageAvailability> = emptyList()
)

sealed interface OfflineSynthesisResult {
    data class Success(val clip: AudioClipEntity) : OfflineSynthesisResult
    data class Error(val message: String, val isLanguageMissing: Boolean = false) : OfflineSynthesisResult
}

class OfflineTtsRepository(
    private val context: Context,
    private val audioClipDao: AudioClipDao
) {
    private var tts: TextToSpeech? = null
    private var initDeferred = CompletableDeferred<Boolean>()

    private val _status = MutableStateFlow(OfflineTtsStatus())
    val status: StateFlow<OfflineTtsStatus> = _status.asStateFlow()

    init {
        initializeEngine()
    }

    fun initializeEngine() {
        tts?.shutdown()
        initDeferred = CompletableDeferred()
        tts = TextToSpeech(context.applicationContext) { result ->
            val ok = result == TextToSpeech.SUCCESS
            if (ok) {
                refreshInstalledVoices()
            } else {
                _status.value = OfflineTtsStatus(
                    isReady = false,
                    initError = "Android TextToSpeech engine failed to initialize (code $result)."
                )
            }
            if (!initDeferred.isCompleted) {
                initDeferred.complete(ok)
            }
        }
    }

    fun refreshInstalledVoices() {
        val engine = tts ?: return
        val targetLocales = listOf(
            Triple("bn-BD", "Bangla (Bangladesh)", "বাংলা (বাংলাদেশ)"),
            Triple("bn-IN", "Bangla (India)", "বাংলা (ভারত)"),
            Triple("en-US", "English (US)", "ইংরেজি (যুক্তরাষ্ট্র)"),
            Triple("en-GB", "English (UK)", "ইংরেজি (যুক্তরাজ্য)"),
            Triple("en-IN", "English (India)", "ইংরেজি (ভারত)")
        )

        val availability = targetLocales.map { (tag, en, bn) ->
            val locale = Locale.forLanguageTag(tag)
            val code = runCatching { engine.isLanguageAvailable(locale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            val available = code >= TextToSpeech.LANG_AVAILABLE
            LanguageAvailability(
                localeTag = tag,
                labelEn = en,
                labelBn = bn,
                isAvailableOffline = available,
                statusCode = code
            )
        }

        val voices: List<OfflineVoiceInfo> = runCatching {
            engine.voices.orEmpty()
                .filter { v ->
                    val lang = v.locale?.language?.lowercase(Locale.ROOT)
                    lang == "bn" || lang == "en"
                }
                .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.locale.toLanguageTag() }.thenBy { it.name })
                .map { v ->
                    OfflineVoiceInfo(
                        name = v.name,
                        localeTag = v.locale.toLanguageTag(),
                        displayLanguage = v.locale.displayName,
                        isNetworkRequired = v.isNetworkConnectionRequired,
                        quality = v.quality
                    )
                }
        }.getOrDefault(emptyList())

        _status.value = OfflineTtsStatus(
            isReady = true,
            initError = null,
            enginePackage = engine.defaultEngine,
            installedVoices = voices,
            languageAvailability = availability
        )
    }

    suspend fun synthesizeOffline(
        text: String,
        localeTag: String,
        voiceName: String?,
        speed: Float,
        pitch: Float
    ): OfflineSynthesisResult = withContext(Dispatchers.IO) {
        val ready = runCatching { withTimeout(5_000L) { initDeferred.await() } }.getOrDefault(false)
        val engine = tts
        if (!ready || engine == null) {
            return@withContext OfflineSynthesisResult.Error(
                "Offline TextToSpeech engine is not ready on this device."
            )
        }

        val locale = Locale.forLanguageTag(localeTag)
        val langCheck = engine.isLanguageAvailable(locale)
        if (langCheck == TextToSpeech.LANG_MISSING_DATA || langCheck == TextToSpeech.LANG_NOT_SUPPORTED) {
            return@withContext OfflineSynthesisResult.Error(
                "Offline voice data for $localeTag is not installed on this device.",
                isLanguageMissing = true
            )
        }

        engine.language = locale
        if (!voiceName.isNullOrBlank()) {
            val matchedVoice = engine.voices?.firstOrNull { it.name == voiceName }
            if (matchedVoice != null) {
                engine.voice = matchedVoice
            }
        }
        engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val outFile = File(audioDir, "offline_${localeTag}_$timestamp.wav")
        val utteranceId = UUID.randomUUID().toString()
        val doneDeferred = CompletableDeferred<Result<Unit>>()

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && !doneDeferred.isCompleted) {
                    doneDeferred.complete(Result.success(Unit))
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && !doneDeferred.isCompleted) {
                    doneDeferred.complete(Result.failure(IllegalStateException("Offline TTS synthesis failed.")))
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && !doneDeferred.isCompleted) {
                    doneDeferred.complete(
                        Result.failure(IllegalStateException("Offline TTS synthesis error code: $errorCode"))
                    )
                }
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val enqueueResult = engine.synthesizeToFile(text, params, outFile, utteranceId)
        if (enqueueResult != TextToSpeech.SUCCESS) {
            return@withContext OfflineSynthesisResult.Error(
                "Could not queue offline speech synthesis (status $enqueueResult)."
            )
        }

        val synthOutcome = runCatching {
            withTimeout(45_000L) { doneDeferred.await() }
        }.getOrElse { ex ->
            Log.e(TAG, "Offline synthesis timed out or failed", ex)
            Result.failure(ex)
        }

        if (synthOutcome.isFailure) {
            outFile.delete()
            return@withContext OfflineSynthesisResult.Error(
                synthOutcome.exceptionOrNull()?.localizedMessage ?: "Offline synthesis failed."
            )
        }

        val wavInfo = WavAudioUtils.readWavInfo(outFile)
        if (!outFile.exists() || outFile.length() < 44L || wavInfo == null) {
            outFile.delete()
            return@withContext OfflineSynthesisResult.Error(
                "Offline TTS engine produced an invalid or empty audio file."
            )
        }

        val snippet = text.trim().replace("\n", " ").take(42).let {
            if (text.trim().length > 42) "$it…" else it
        }
        val label = voiceName?.substringAfterLast("-")?.take(16) ?: localeTag
        val entity = AudioClipEntity(
            title = "Offline · $snippet",
            textPrompt = text.trim(),
            languageCode = localeTag,
            voiceLabel = "System TTS ($label)",
            sourceType = AudioSourceType.OFFLINE_TTS.name,
            speed = speed,
            pitch = pitch,
            durationMs = wavInfo.durationMs,
            wavFilePath = outFile.absolutePath,
            mp3FilePath = null,
            isDownloaded = false,
            fileSizeBytes = outFile.length(),
            isVerifiedPlayable = wavInfo.durationMs > 0L,
            createdAt = timestamp
        )
        val id = audioClipDao.insertClip(entity)
        OfflineSynthesisResult.Success(entity.copy(id = id))
    }

    fun createInstallVoiceDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createTtsSettingsIntent(): Intent {
        return Intent("com.android.settings.TTS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
