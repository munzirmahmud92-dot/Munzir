package com.example.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExportManager
import com.example.audio.AudioPlayerManager
import com.example.audio.ExportResult
import com.example.audio.PlaybackState
import com.example.data.local.AudioClipEntity
import com.example.data.preferences.SettingsDataStore
import com.example.data.preferences.StudioSettings
import com.example.data.repository.OfflineSynthesisResult
import com.example.data.repository.OfflineTtsRepository
import com.example.data.repository.OfflineTtsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OfflineGenUiState {
    data object Idle : OfflineGenUiState
    data object Loading : OfflineGenUiState
    data class Success(val clip: AudioClipEntity) : OfflineGenUiState
    data class Error(val message: String, val isLanguageMissing: Boolean = false) : OfflineGenUiState
}

class OfflineStudioViewModel(
    private val offlineTtsRepository: OfflineTtsRepository,
    private val audioExportManager: AudioExportManager,
    private val audioPlayerManager: AudioPlayerManager,
    settingsDataStore: SettingsDataStore
) : ViewModel() {

    val ttsStatus: StateFlow<OfflineTtsStatus> = offlineTtsRepository.status
    val playbackState: StateFlow<PlaybackState> = audioPlayerManager.playbackState
    val settings: StateFlow<StudioSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioSettings())

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedLocale = MutableStateFlow("en-US")
    val selectedLocale: StateFlow<String> = _selectedLocale.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow<String?>(null)
    val selectedVoiceName: StateFlow<String?> = _selectedVoiceName.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _genState = MutableStateFlow<OfflineGenUiState>(OfflineGenUiState.Idle)
    val genState: StateFlow<OfflineGenUiState> = _genState.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    fun updateText(text: String) {
        _inputText.value = text
    }

    fun insertSample(isBangla: Boolean) {
        if (isBangla) {
            _selectedLocale.value = "bn-BD"
            _selectedVoiceName.value = ttsStatus.value.installedVoices
                .firstOrNull { it.localeTag.startsWith("bn", ignoreCase = true) }?.name
            _inputText.value = "ইন্টারনেট ছাড়াই অ্যান্ড্রয়েড টেক্সট-টু-স্পিচ ব্যবহার করে এই বাংলা অডিও তৈরি করা হয়েছে।"
        } else {
            _selectedLocale.value = "en-US"
            _selectedVoiceName.value = ttsStatus.value.installedVoices
                .firstOrNull { it.localeTag.startsWith("en", ignoreCase = true) }?.name
            _inputText.value = "This audio was synthesized locally on your Android device with zero internet connection."
        }
    }

    fun selectLocale(localeTag: String) {
        _selectedLocale.value = localeTag
        _selectedVoiceName.value = ttsStatus.value.installedVoices
            .firstOrNull { it.localeTag.equals(localeTag, ignoreCase = true) }?.name
    }

    fun selectVoice(voiceName: String?) {
        _selectedVoiceName.value = voiceName
    }

    fun updateSpeed(value: Float) {
        _speed.value = value
    }

    fun updatePitch(value: Float) {
        _pitch.value = value
    }

    fun refreshVoices() {
        offlineTtsRepository.refreshInstalledVoices()
    }

    fun generateOfflineSpeech(emptyErrorMessage: String) {
        val text = _inputText.value.trim()
        if (text.isEmpty()) {
            _genState.value = OfflineGenUiState.Error(emptyErrorMessage)
            return
        }

        viewModelScope.launch {
            _genState.value = OfflineGenUiState.Loading
            _exportMessage.value = null
            when (
                val result = offlineTtsRepository.synthesizeOffline(
                    text = text,
                    localeTag = _selectedLocale.value,
                    voiceName = _selectedVoiceName.value,
                    speed = _speed.value,
                    pitch = _pitch.value
                )
            ) {
                is OfflineSynthesisResult.Success -> {
                    _genState.value = OfflineGenUiState.Success(result.clip)
                    result.clip.wavFilePath?.let { path ->
                        audioPlayerManager.playFile(path, result.clip.title, result.clip.id, 1.0f, 1.0f)
                    }
                }
                is OfflineSynthesisResult.Error -> {
                    _genState.value = OfflineGenUiState.Error(result.message, result.isLanguageMissing)
                }
            }
        }
    }

    fun playOrResumeClip(clip: AudioClipEntity) {
        val current = playbackState.value
        if (current.activeClipId == clip.id && current.isPaused) {
            audioPlayerManager.resume()
        } else {
            val path = clip.wavFilePath ?: clip.mp3FilePath ?: return
            audioPlayerManager.playFile(path, clip.title, clip.id, 1.0f, 1.0f)
        }
    }

    fun pausePlayback() = audioPlayerManager.pause()
    fun stopPlayback() = audioPlayerManager.stop()
    fun seekPlayback(ms: Long) = audioPlayerManager.seekTo(ms)

    fun exportWav(successPrefix: String) {
        val clip = (_genState.value as? OfflineGenUiState.Success)?.clip ?: return
        viewModelScope.launch {
            _isExporting.value = true
            when (val res = audioExportManager.exportWav(clip)) {
                is ExportResult.Success -> {
                    _genState.value = OfflineGenUiState.Success(res.updatedClip)
                    _exportMessage.value = "$successPrefix ${res.exportedFile.name}"
                }
                is ExportResult.Error -> _exportMessage.value = res.message
            }
            _isExporting.value = false
        }
    }

    fun exportMp3(successPrefix: String) {
        val clip = (_genState.value as? OfflineGenUiState.Success)?.clip ?: return
        viewModelScope.launch {
            _isExporting.value = true
            val bitrate = settings.value.mp3Quality.bitrateKbps
            when (val res = audioExportManager.exportMp3(clip, bitrate)) {
                is ExportResult.Success -> {
                    _genState.value = OfflineGenUiState.Success(res.updatedClip)
                    _exportMessage.value = "$successPrefix ${res.exportedFile.name} (${bitrate} kbps)"
                }
                is ExportResult.Error -> _exportMessage.value = res.message
            }
            _isExporting.value = false
        }
    }

    fun getInstallVoiceDataIntent(): Intent = offlineTtsRepository.createInstallVoiceDataIntent()
    fun getTtsSettingsIntent(): Intent = offlineTtsRepository.createTtsSettingsIntent()

    fun dismissExportMessage() {
        _exportMessage.value = null
    }

    class Factory(
        private val offlineTtsRepository: OfflineTtsRepository,
        private val audioExportManager: AudioExportManager,
        private val audioPlayerManager: AudioPlayerManager,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OfflineStudioViewModel(
                offlineTtsRepository,
                audioExportManager,
                audioPlayerManager,
                settingsDataStore
            ) as T
        }
    }
}
