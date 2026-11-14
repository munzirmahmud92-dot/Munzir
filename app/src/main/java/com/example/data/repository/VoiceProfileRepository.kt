package com.example.data.repository

import com.example.data.local.SecureVoiceIdStore
import com.example.data.local.VoiceProfileDao
import com.example.data.local.VoiceProfileEntity
import com.example.data.local.VoiceProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Room-backed Repository for saving, listing, searching, and managing
 * cloned/personal voice profiles and their associated local audio files.
 */
class VoiceProfileRepository(
    private val voiceProfileDao: VoiceProfileDao,
    private val secureVoiceIdStore: SecureVoiceIdStore
) {
    val allVoices: Flow<List<VoiceProfileEntity>> = voiceProfileDao.observeAllVoices()

    val personalVoices: Flow<List<VoiceProfileEntity>> = voiceProfileDao.observePersonalVoices()

    fun searchPersonalVoices(query: String): Flow<List<VoiceProfileEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            voiceProfileDao.observePersonalVoices()
        } else {
            voiceProfileDao.searchPersonalVoices(trimmed)
        }
    }

    suspend fun getProfileById(id: Long): VoiceProfileEntity? = withContext(Dispatchers.IO) {
        voiceProfileDao.getVoiceById(id)
    }

    suspend fun saveProfile(profile: VoiceProfileEntity): Long = withContext(Dispatchers.IO) {
        voiceProfileDao.insertVoice(profile)
    }

    suspend fun updateProfile(profile: VoiceProfileEntity) = withContext(Dispatchers.IO) {
        voiceProfileDao.updateVoice(profile)
    }

    suspend fun renameProfile(id: Long, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            voiceProfileDao.renameVoice(id, trimmed)
        }
    }

    suspend fun updateProfileDetails(
        id: Long,
        newName: String,
        languageCode: String,
        styleDescriptor: String
    ) = withContext(Dispatchers.IO) {
        val trimmedName = newName.trim()
        if (trimmedName.isNotEmpty()) {
            voiceProfileDao.updateProfileDetails(
                id = id,
                newName = trimmedName,
                languageCode = languageCode,
                styleDescriptor = styleDescriptor.trim().ifEmpty { "Personal Voice Sample" }
            )
        }
    }

    suspend fun replaceAudioFile(
        profile: VoiceProfileEntity,
        newFilePath: String,
        newDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val oldPath = profile.sampleFilePath
        voiceProfileDao.updateProfileAudioFile(
            id = profile.id,
            filePath = newFilePath,
            durationMs = newDurationMs
        )
        if (!oldPath.isNullOrBlank() && oldPath != newFilePath) {
            runCatching { File(oldPath).delete() }
        }
    }

    suspend fun deleteProfile(profile: VoiceProfileEntity) = withContext(Dispatchers.IO) {
        profile.sampleFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
        secureVoiceIdStore.deleteVoiceId(profile.id)
        voiceProfileDao.deleteVoiceById(profile.id)
    }

    suspend fun deleteAllPersonalVoices() = withContext(Dispatchers.IO) {
        val personal = voiceProfileDao.observePersonalVoices().first()
        personal.forEach { profile ->
            if (profile.providerType != VoiceProviderType.GEMINI_PRESET.name) {
                profile.sampleFilePath?.let { path -> runCatching { File(path).delete() } }
            }
        }
        secureVoiceIdStore.clearAll()
        voiceProfileDao.deleteAllPersonalVoices()
    }
}
