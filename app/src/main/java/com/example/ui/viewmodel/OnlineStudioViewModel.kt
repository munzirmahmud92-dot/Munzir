package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExportManager
import com.example.audio.AudioPlayerManager
import com.example.audio.ExportResult
import com.example.audio.PlaybackState
import com.example.data.local.AudioClipEntity
import com.example.data.local.VoiceProfileEntity
import com.example.data.preferences.ConnectionMode
import com.example.data.preferences.SettingsDataStore
import com.example.data.preferences.StudioSettings
import com.example.data.repository.GeminiPresetVoice
import com.example.data.repository.GeminiTtsRepository
import com.example.data.repository.GeminiTtsResult
import com.example.data.repository.SpeechLanguageOption
import com.example.data.repository.VoiceProfileRepository
import com.example.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OnlineGenUiState {
    data object Idle : OnlineGenUiState
    data object Loading : OnlineGenUiState
    data class Success(val clip: AudioClipEntity) : OnlineGenUiState
    data class Error(val message: String, val isQuotaOrAuth: Boolean = false) : OnlineGenUiState
}

class OnlineStudioViewModel(
    private val geminiTtsRepository: GeminiTtsRepository,
    private val audioExportManager: AudioExportManager,
    private val audioPlayerManager: AudioPlayerManager,
    private val voiceProfileRepository: VoiceProfileRepository,
    settingsDataStore: SettingsDataStore,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val languages: List<SpeechLanguageOption> = geminiTtsRepository.supportedLanguages
    val presetVoices: List<GeminiPresetVoice> = geminiTtsRepository.presetVoices

    val settings: StateFlow<StudioSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioSettings())

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    val playbackState: StateFlow<PlaybackState> = audioPlayerManager.playbackState

    val personalVoices: StateFlow<List<VoiceProfileEntity>> = voiceProfileRepository.personalVoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(languages.first())
    val selectedLanguage: StateFlow<SpeechLanguageOption> = _selectedLanguage.asStateFlow()

    private val _selectedVoice = MutableStateFlow(presetVoices.first())
    val selectedVoice: StateFlow<GeminiPresetVoice> = _selectedVoice.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _genState = MutableStateFlow<OnlineGenUiState>(OnlineGenUiState.Idle)
    val genState: StateFlow<OnlineGenUiState> = _genState.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _personalVoiceNotice = MutableStateFlow<String?>(null)
    val personalVoiceNotice: StateFlow<String?> = _personalVoiceNotice.asStateFlow()

    init {
        viewModelScope.launch {
            geminiTtsRepository.ensureDefaultVoicesSeeded()
        }
    }

    fun updateText(text: String) {
        _inputText.value = text
    }

    fun insertSampleText(isBangla: Boolean) {
        if (isBangla) {
            _selectedLanguage.value = languages.first { it.code == "bn-BD" }
            _inputText.value = "স্বাগতম Munzir voice cloner-এ। আজ আমরা বাংলা এবং ইংরেজি ভাষায় প্রাকৃতিক ও স্পষ্ট অডিও তৈরি করছি।"
        } else {
            _selectedLanguage.value = languages.first { it.code == "en-US" }
            _inputText.value = "Welcome to Munzir voice cloner. This speech is synthesized securely with Gemini Text-to-Speech and mastered on-device."
        }
    }

    fun selectLanguage(option: SpeechLanguageOption) {
        _selectedLanguage.value = option
    }

    fun selectPresetVoice(voice: GeminiPresetVoice) {
        _selectedVoice.value = voice
        _personalVoiceNotice.value = null
    }

    fun selectPresetByName(voiceName: String) {
        presetVoices.firstOrNull { it.voiceName.equals(voiceName, ignoreCase = true) }?.let {
            _selectedVoice.value = it
            _personalVoiceNotice.value = null
        }
    }

    fun notifyPersonalVoiceNotCloned(profile: VoiceProfileEntity) {
        _personalVoiceNotice.value =
            "'${profile.name}' is a local personal voice sample. Standard Gemini TTS only synthesizes prebuilt studio voices (${_selectedVoice.value.voiceName}) and will not disguise a prebuilt voice as your personal clone."
    }

    fun updateSpeed(value: Float) {
        _speed.value = value
        audioPlayerManager.updateSpeedAndPitch(value, _pitch.value)
    }

    fun updatePitch(value: Float) {
        _pitch.value = value
        audioPlayerManager.updateSpeedAndPitch(_speed.value, value)
    }

    fun generateSpeech(emptyErrorMessage: String) {
        val text = _inputText.value.trim()
        if (text.isEmpty()) {
            _genState.value = OnlineGenUiState.Error(emptyErrorMessage)
            return
        }
        if (settings.value.connectionMode == ConnectionMode.OFFLINE_ONLY) {
            _genState.value = OnlineGenUiState.Error(
                "Offline Mode is enabled in Settings. Switch Connection Mode to Auto/Online or use Offline Voice Studio."
            )
            return
        }
        if (!isOnline.value) {
            _genState.value = OnlineGenUiState.Error(
                "No internet connection detected (Airplane Mode or offline). Please reconnect or switch to Offline Voice Studio."
            )
            return
        }

        viewModelScope.launch {
            _genState.value = OnlineGenUiState.Loading
            _exportMessage.value = null
            when (
                val result = geminiTtsRepository.generateSpeech(
                    text = text,
                    languageOption = _selectedLanguage.value,
                    voiceName = _selectedVoice.value.voiceName,
                    speed = _speed.value,
                    pitch = _pitch.value
                )
            ) {
                is GeminiTtsResult.Success -> {
                    _genState.value = OnlineGenUiState.Success(result.clip)
                    result.clip.wavFilePath?.let { path ->
                        audioPlayerManager.playFile(
                            filePath = path,
                            title = result.clip.title,
                            clipId = result.clip.id,
                            speed = _speed.value,
                            pitch = _pitch.value
                        )
                    }
                }
                is GeminiTtsResult.Error -> {
                    _genState.value = OnlineGenUiState.Error(result.message, result.isQuotaOrAuthError)
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
            audioPlayerManager.playFile(path, clip.title, clip.id, _speed.value, _pitch.value)
        }
    }

    fun pausePlayback() = audioPlayerManager.pause()
    fun stopPlayback() = audioPlayerManager.stop()
    fun seekPlayback(ms: Long) = audioPlayerManager.seekTo(ms)

    fun exportCurrentAsWav(successPrefix: String) {
        val current = (_genState.value as? OnlineGenUiState.Success)?.clip ?: return
        viewModelScope.launch {
            _isExporting.value = true
            when (val res = audioExportManager.exportWav(current)) {
                is ExportResult.Success -> {
                    _genState.value = OnlineGenUiState.Success(res.updatedClip)
                    _exportMessage.value = "$successPrefix ${res.exportedFile.name} (${res.verification.details})"
                }
                is ExportResult.Error -> _exportMessage.value = res.message
            }
            _isExporting.value = false
        }
    }

    fun exportCurrentAsMp3(successPrefix: String) {
        val current = (_genState.value as? OnlineGenUiState.Success)?.clip ?: return
        viewModelScope.launch {
            _isExporting.value = true
            val bitrate = settings.value.mp3Quality.bitrateKbps
            when (val res = audioExportManager.exportMp3(current, bitrate)) {
                is ExportResult.Success -> {
                    _genState.value = OnlineGenUiState.Success(res.updatedClip)
                    _exportMessage.value = "$successPrefix ${res.exportedFile.name} (${bitrate} kbps · ${res.verification.details})"
                }
                is ExportResult.Error -> _exportMessage.value = res.message
            }
            _isExporting.value = false
        }
    }

    fun dismissExportMessage() {
        _exportMessage.value = null
    }

    class Factory(
        private val geminiTtsRepository: GeminiTtsRepository,
        private val audioExportManager: AudioExportManager,
        private val audioPlayerManager: AudioPlayerManager,
        private val voiceProfileRepository: VoiceProfileRepository,
        private val settingsDataStore: SettingsDataStore,
        private val networkMonitor: NetworkMonitor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnlineStudioViewModel(
                geminiTtsRepository,
                audioExportManager,
                audioPlayerManager,
                voiceProfileRepository,
                settingsDataStore,
                networkMonitor
            ) as T
        }
    }
}
