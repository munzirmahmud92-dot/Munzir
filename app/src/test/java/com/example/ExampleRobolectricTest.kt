package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.Mp3Encoder
import com.example.audio.WavAudioUtils
import com.example.data.local.ConsentOwnerType
import com.example.data.local.SecureVoiceIdStore
import com.example.data.local.StudioDatabase
import com.example.data.local.VoiceProfileEntity
import com.example.data.local.VoiceProviderType
import com.example.data.repository.VoiceProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Munzir voice cloner", appName)
    }

    @Test
    fun `wav roundtrip and waveform extraction produce valid audio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleRate = 24_000
        val sampleCount = sampleRate // 1 second
        val pcmBytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val sample = (sin(2.0 * PI * 440.0 * i / sampleRate) * 12000.0).toInt().toShort()
            pcmBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val wavFile = File(context.cacheDir, "test_tone.wav")
        WavAudioUtils.pcmToWavFile(pcmBytes, sampleRate, 1, 16, wavFile)

        val info = WavAudioUtils.readWavInfo(wavFile)
        assertNotNull(info)
        assertEquals(sampleRate, info!!.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(1000L, info.durationMs)

        val bars = WavAudioUtils.extractWaveformAmplitudes(wavFile, barCount = 24)
        assertEquals(24, bars.size)
        assertTrue(bars.all { it in 0.1f..1.0f })

        val mp3File = File(context.cacheDir, "test_tone.mp3")
        Mp3Encoder.encodeWavToMp3(wavFile, mp3File, bitrateKbps = 192)
        assertTrue(mp3File.exists() && mp3File.length() > 1000L)

        val header = mp3File.inputStream().use { ins ->
            ByteArray(3).also { ins.read(it) }
        }
        val hasId3 = header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()
        val hasSync = (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0
        assertTrue("MP3 must begin with valid MPEG sync or ID3 header", hasId3 || hasSync)

        wavFile.delete()
        mp3File.delete()
    }

    @Test
    fun `room database and repository save list search update and delete cloned voice profiles`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = VoiceProfileRepository(
                voiceProfileDao = db.voiceProfileDao(),
                secureVoiceIdStore = SecureVoiceIdStore(context)
            )

            val audioV1 = File(context.cacheDir, "munzir_sample_v1.wav").apply {
                writeBytes(ByteArray(1024) { 1 })
            }
            val audioV2 = File(context.cacheDir, "munzir_sample_v2.wav").apply {
                writeBytes(ByteArray(2048) { 2 })
            }

            // 1. Save cloned voice profile with name and associated audio file
            val insertedId = repo.saveProfile(
                VoiceProfileEntity(
                    name = "Munzir Studio Voice",
                    providerType = VoiceProviderType.PERSONAL_SAMPLE.name,
                    languageCode = "bn-BD",
                    geminiVoiceName = null,
                    styleDescriptor = "Warm Bangla Narrator",
                    sampleFilePath = audioV1.absolutePath,
                    sampleDurationMs = 3500L,
                    consentConfirmed = true,
                    consentOwnerType = ConsentOwnerType.SELF.name
                )
            )
            assertTrue(insertedId > 0L)

            // 2. List & search cloned voice profiles via Flow
            val listed = repo.personalVoices.first()
            assertEquals(1, listed.size)
            assertEquals("Munzir Studio Voice", listed.first().name)
            assertEquals(audioV1.absolutePath, listed.first().sampleFilePath)

            val searchHit = repo.searchPersonalVoices("Munzir").first()
            assertEquals(1, searchHit.size)
            val searchMiss = repo.searchPersonalVoices("NonExistentSpeaker").first()
            assertTrue(searchMiss.isEmpty())

            // 3. Update profile name, language, and style
            repo.updateProfileDetails(
                id = insertedId,
                newName = "Munzir Pro",
                languageCode = "en-US",
                styleDescriptor = "Crisp Podcast Host"
            )
            val updated = repo.getProfileById(insertedId)!!
            assertEquals("Munzir Pro", updated.name)
            assertEquals("en-US", updated.languageCode)
            assertEquals("Crisp Podcast Host", updated.styleDescriptor)

            // 4. Replace associated audio file & verify old file cleanup
            repo.replaceAudioFile(updated, audioV2.absolutePath, 5200L)
            val withNewAudio = repo.getProfileById(insertedId)!!
            assertEquals(audioV2.absolutePath, withNewAudio.sampleFilePath)
            assertEquals(5200L, withNewAudio.sampleDurationMs)
            assertFalse("Old superseded sample file should be deleted", audioV1.exists())
            assertTrue("New sample file should exist", audioV2.exists())

            // 5. Delete profile & associated audio file
            repo.deleteProfile(withNewAudio)
            assertNull(repo.getProfileById(insertedId))
            assertTrue(repo.personalVoices.first().isEmpty())
            assertFalse("Deleted profile audio file should be removed", audioV2.exists())
        } finally {
            db.close()
        }
    }
}
