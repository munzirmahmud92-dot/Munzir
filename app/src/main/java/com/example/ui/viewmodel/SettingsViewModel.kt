package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExportManager
import com.example.audio.AudioPlayerManager
import com.example.audio.StorageStats
import com.example.data.preferences.AudioExportFormat
import com.example.data.preferences.ConnectionMode
import com.example.data.preferences.Mp3Quality
import com.example.data.preferences.SettingsDataStore
import com.example.data.preferences.StudioSettings
import com.example.data.repository.VoiceProfileRepository
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val audioExportManager: AudioExportManager,
    private val audioPlayerManager: AudioPlayerManager,
    private val voiceProfileRepository: VoiceProfileRepository
) : ViewModel() {

    val settings: StateFlow<StudioSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioSettings())

    private val _storageStats = MutableStateFlow(StorageStats(0L, 0L, 0L))
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = audioExportManager.computeStorageStats()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsDataStore.setAppLanguage(language) }
    }

    fun setMp3Quality(quality: Mp3Quality) {
        viewModelScope.launch { settingsDataStore.setMp3Quality(quality) }
    }

    fun setDefaultFormat(format: AudioExportFormat) {
        viewModelScope.launch { settingsDataStore.setDefaultFormat(format) }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        viewModelScope.launch { settingsDataStore.setConnectionMode(mode) }
    }

    fun setRequireExplicitConsent(required: Boolean) {
        viewModelScope.launch { settingsDataStore.setRequireExplicitConsent(required) }
    }

    fun setAllowTimbreAnalysis(allowed: Boolean) {
        viewModelScope.launch { settingsDataStore.setAllowAcousticTimbreAnalysis(allowed) }
    }

    fun setReplicationEndpointUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setReplicationEndpointUrl(url)
            _statusMessage.value = "Replication endpoint updated."
        }
    }

    fun clearUnexportedCache() {
        viewModelScope.launch {
            audioPlayerManager.stop()
            audioExportManager.clearUnexportedCache()
            refreshStorageStats()
            _statusMessage.value = "Cleared unexported history cache."
        }
    }

    fun resetAllLocalData() {
        viewModelScope.launch {
            audioPlayerManager.stop()
            audioExportManager.deleteAllLocalAudioFiles()
            voiceProfileRepository.deleteAllPersonalVoices()
            refreshStorageStats()
            _statusMessage.value = "Deleted all local voice samples, clips, and exports."
        }
    }

    fun dismissMessage() {
        _statusMessage.value = null
    }

    class Factory(
        private val settingsDataStore: SettingsDataStore,
        private val audioExportManager: AudioExportManager,
        private val audioPlayerManager: AudioPlayerManager,
        private val voiceProfileRepository: VoiceProfileRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                settingsDataStore,
                audioExportManager,
                audioPlayerManager,
                voiceProfileRepository
            ) as T
        }
    }
}
