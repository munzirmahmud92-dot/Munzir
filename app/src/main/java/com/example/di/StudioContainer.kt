package com.example.di

import android.content.Context
import com.example.audio.AudioExportManager
import com.example.audio.AudioPlayerManager
import com.example.audio.VoiceRecorderManager
import com.example.data.local.SecureVoiceIdStore
import com.example.data.local.StudioDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.GeminiTtsRepository
import com.example.data.repository.OfflineTtsRepository
import com.example.data.repository.VoiceProfileRepository
import com.example.data.repository.VoiceReplicationRepository
import com.example.network.NetworkMonitor

class StudioContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: StudioDatabase = StudioDatabase.getInstance(appContext)
    val voiceProfileDao = database.voiceProfileDao()
    val audioClipDao = database.audioClipDao()
    val settingsDataStore = SettingsDataStore(appContext)
    val secureVoiceIdStore = SecureVoiceIdStore(appContext)
    val voiceProfileRepository = VoiceProfileRepository(
        voiceProfileDao = voiceProfileDao,
        secureVoiceIdStore = secureVoiceIdStore
    )
    val networkMonitor = NetworkMonitor(appContext)
    val audioPlayerManager = AudioPlayerManager()
    val voiceRecorderManager = VoiceRecorderManager(appContext)
    val audioExportManager = AudioExportManager(appContext, audioClipDao)
    val geminiTtsRepository = GeminiTtsRepository(
        context = appContext,
        audioClipDao = audioClipDao,
        voiceProfileDao = voiceProfileDao,
        settingsDataStore = settingsDataStore
    )
    val offlineTtsRepository = OfflineTtsRepository(
        context = appContext,
        audioClipDao = audioClipDao
    )
    val voiceReplicationRepository = VoiceReplicationRepository(
        voiceProfileDao = voiceProfileDao,
        secureVoiceIdStore = secureVoiceIdStore
    )

    fun release() {
        audioPlayerManager.release()
        offlineTtsRepository.shutdown()
        networkMonitor.unregister()
    }
}
