package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VoiceProviderType {
    GEMINI_PRESET,
    PERSONAL_SAMPLE,
    REPLICATED_API
}

enum class ConsentOwnerType {
    SELF,
    DOCUMENTED_PERMISSION
}

enum class ReplicationStatus {
    NOT_ATTEMPTED,
    UNAVAILABLE,
    REPLICATED
}

enum class AudioSourceType {
    ONLINE_GEMINI,
    OFFLINE_TTS
}

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val providerType: String, // VoiceProviderType.name
    val languageCode: String, // e.g., "bn-BD", "en-US"
    val geminiVoiceName: String?, // Prebuilt Gemini voice name when GEMINI_PRESET
    val styleDescriptor: String,
    val sampleFilePath: String?,
    val sampleDurationMs: Long = 0L,
    val consentConfirmed: Boolean = false,
    val consentOwnerType: String = ConsentOwnerType.SELF.name,
    val consentNote: String = "",
    val replicationStatus: String = ReplicationStatus.NOT_ATTEMPTED.name,
    val replicationMessage: String = "",
    val hasEncryptedVoiceId: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audio_clips")
data class AudioClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val textPrompt: String,
    val languageCode: String,
    val voiceLabel: String,
    val sourceType: String, // AudioSourceType.name
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val durationMs: Long = 0L,
    val wavFilePath: String? = null,
    val mp3FilePath: String? = null,
    val isDownloaded: Boolean = false,
    val mediaStoreWavUri: String? = null,
    val mediaStoreMp3Uri: String? = null,
    val fileSizeBytes: Long = 0L,
    val isVerifiedPlayable: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
