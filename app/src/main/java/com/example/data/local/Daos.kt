package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profiles ORDER BY CASE WHEN providerType = 'GEMINI_PRESET' THEN 1 ELSE 0 END, createdAt DESC")
    fun observeAllVoices(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profiles WHERE providerType != 'GEMINI_PRESET' ORDER BY createdAt DESC")
    fun observePersonalVoices(): Flow<List<VoiceProfileEntity>>

    @Query(
        """
        SELECT * FROM voice_profiles
        WHERE providerType != 'GEMINI_PRESET'
          AND (
            name LIKE '%' || :query || '%'
            OR languageCode LIKE '%' || :query || '%'
            OR styleDescriptor LIKE '%' || :query || '%'
          )
        ORDER BY createdAt DESC
        """
    )
    fun searchPersonalVoices(query: String): Flow<List<VoiceProfileEntity>>

    @Query("SELECT COUNT(*) FROM voice_profiles")
    suspend fun getVoiceCount(): Int

    @Query("SELECT * FROM voice_profiles WHERE id = :id")
    suspend fun getVoiceById(id: Long): VoiceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoice(profile: VoiceProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVoices(profiles: List<VoiceProfileEntity>)

    @Update
    suspend fun updateVoice(profile: VoiceProfileEntity)

    @Query("UPDATE voice_profiles SET name = :newName WHERE id = :id")
    suspend fun renameVoice(id: Long, newName: String)

    @Query("UPDATE voice_profiles SET name = :newName, languageCode = :languageCode, styleDescriptor = :styleDescriptor WHERE id = :id")
    suspend fun updateProfileDetails(id: Long, newName: String, languageCode: String, styleDescriptor: String)

    @Query("UPDATE voice_profiles SET sampleFilePath = :filePath, sampleDurationMs = :durationMs WHERE id = :id")
    suspend fun updateProfileAudioFile(id: Long, filePath: String, durationMs: Long)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun deleteVoiceById(id: Long)

    @Query("DELETE FROM voice_profiles WHERE providerType != 'GEMINI_PRESET'")
    suspend fun deleteAllPersonalVoices()
}

@Dao
interface AudioClipDao {
    @Query("SELECT * FROM audio_clips ORDER BY createdAt DESC")
    fun observeAllClips(): Flow<List<AudioClipEntity>>

    @Query("SELECT * FROM audio_clips WHERE isDownloaded = 1 ORDER BY createdAt DESC")
    fun observeDownloadedClips(): Flow<List<AudioClipEntity>>

    @Query("SELECT * FROM audio_clips WHERE id = :id")
    suspend fun getClipById(id: Long): AudioClipEntity?

    @Query("SELECT * FROM audio_clips WHERE isDownloaded = 0")
    suspend fun getUnexportedClips(): List<AudioClipEntity>

    @Query("SELECT * FROM audio_clips")
    suspend fun getAllClipsOnce(): List<AudioClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: AudioClipEntity): Long

    @Update
    suspend fun updateClip(clip: AudioClipEntity)

    @Query("UPDATE audio_clips SET title = :newTitle WHERE id = :id")
    suspend fun renameClip(id: Long, newTitle: String)

    @Query("DELETE FROM audio_clips WHERE id = :id")
    suspend fun deleteClipById(id: Long)

    @Query("DELETE FROM audio_clips WHERE isDownloaded = 0")
    suspend fun deleteUnexportedClips()

    @Query("DELETE FROM audio_clips")
    suspend fun deleteAllClips()
}
