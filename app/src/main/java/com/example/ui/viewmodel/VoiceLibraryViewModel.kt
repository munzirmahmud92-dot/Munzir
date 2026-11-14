package com.example.ui.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExportManager
import com.example.audio.AudioPlayerManager
import com.example.audio.PlaybackState
import com.example.audio.RecordingState
import com.example.audio.VoiceRecorderManager
import com.example.data.local.ConsentOwnerType
import com.example.data.local.ReplicationStatus
import com.example.data.local.VoiceProfileEntity
import com.example.data.local.VoiceProviderType
import com.example.data.preferences.SettingsDataStore
import com.example.data.preferences.StudioSettings
import com.example.data.repository.GeminiTtsRepository
import com.example.data.repository.ReplicationResult
import com.example.data.repository.VoiceProfileRepository
import com.example.data.repository.VoiceReplicationRepository
import com.example.network.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceLibraryViewModel(
    private val voiceProfileRepository: VoiceProfileRepository,
    private val voiceRecorderManager: VoiceRecorderManager,
    private val audioPlayerManager: AudioPlayerManager,
    private val audioExportManager: AudioExportManager,
    private val voiceReplicationRepository: VoiceReplicationRepository,
    private val geminiTtsRepository: GeminiTtsRepository,
    settingsDataStore: SettingsDataStore,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val allVoices: StateFlow<List<VoiceProfileEntity>> = voiceProfileRepository.allVoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _languageFilter = MutableStateFlow<String?>(null)
    val languageFilter: StateFlow<String?> = _languageFilter.asStateFlow()

    val filteredPersonalVoices: StateFlow<List<VoiceProfileEntity>> = combine(
        _searchQuery.flatMapLatest { query -> voiceProfileRepository.searchPersonalVoices(query) },
        _languageFilter
    ) { voices, langFilter ->
        if (langFilter.isNullOrBlank()) {
            voices
        } else {
            voices.filter { it.languageCode.equals(langFilter, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recordingState: StateFlow<RecordingState> = voiceRecorderManager.recordingState
    val playbackState: StateFlow<PlaybackState> = audioPlayerManager.playbackState
    val settings: StateFlow<StudioSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioSettings())
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _voiceName = MutableStateFlow("")
    val voiceName: StateFlow<String> = _voiceName.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("bn-BD")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _ownershipType = MutableStateFlow(ConsentOwnerType.SELF)
    val ownershipType: StateFlow<ConsentOwnerType> = _ownershipType.asStateFlow()

    private val _permissionNote = MutableStateFlow("")
    val permissionNote: StateFlow<String> = _permissionNote.asStateFlow()

    private val _consentChecked = MutableStateFlow(false)
    val consentChecked: StateFlow<Boolean> = _consentChecked.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isErrorStatus = MutableStateFlow(false)
    val isErrorStatus: StateFlow<Boolean> = _isErrorStatus.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _timbreAnalysis = MutableStateFlow<String?>(null)
    val timbreAnalysis: StateFlow<String?> = _timbreAnalysis.asStateFlow()

    init {
        viewModelScope.launch {
            geminiTtsRepository.ensureDefaultVoicesSeeded()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateLanguageFilter(languageCode: String?) {
        _languageFilter.value = languageCode
    }

    fun updateVoiceName(name: String) {
        _voiceName.value = name
    }

    fun updateLanguage(code: String) {
        _selectedLanguage.value = code
    }

    fun updateOwnershipType(type: ConsentOwnerType) {
        _ownershipType.value = type
    }

    fun updatePermissionNote(note: String) {
        _permissionNote.value = note
    }

    fun updateConsentChecked(checked: Boolean) {
        _consentChecked.value = checked
    }

    fun startRecording() {
        _statusMessage.value = null
        voiceRecorderManager.startRecording()
    }

    fun stopRecording() {
        voiceRecorderManager.stopRecording()
    }

    fun importAudioSample(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = null
            voiceRecorderManager.importAudioFromUri(uri)
                .onSuccess { (_, durationMs) ->
                    _isErrorStatus.value = false
                    _statusMessage.value = "Imported voice sample (${durationMs / 1000.0}s) ready for consent verification."
                }
                .onFailure { err ->
                    _isErrorStatus.value = true
                    _statusMessage.value = err.localizedMessage ?: "Failed to import audio sample."
                }
            _isProcessing.value = false
        }
    }

    fun replaceProfileAudio(profile: VoiceProfileEntity, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = null
            if (playbackState.value.activeClipId == profile.id) {
                audioPlayerManager.stop()
            }
            voiceRecorderManager.importAudioFromUri(uri)
                .onSuccess { (newPath, newDurationMs) ->
                    voiceProfileRepository.replaceAudioFile(profile, newPath, newDurationMs)
                    voiceRecorderManager.clearRecording()
                    _isErrorStatus.value = false
                    _statusMessage.value = "Updated associated audio file for '${profile.name}'."
                }
                .onFailure { err ->
                    _isErrorStatus.value = true
                    _statusMessage.value = err.localizedMessage ?: "Failed to attach audio file."
                }
            _isProcessing.value = false
        }
    }

    fun previewCurrentSample() {
        val path = recordingState.value.recordedFilePath ?: return
        audioPlayerManager.playFile(path, _voiceName.value.ifBlank { "Voice Sample" })
    }

    fun playProfileSample(profile: VoiceProfileEntity) {
        val path = profile.sampleFilePath ?: return
        if (!File(path).exists()) {
            _isErrorStatus.value = true
            _statusMessage.value = "Associated audio file for '${profile.name}' was not found on device. Tap Replace Audio to attach one."
            return
        }
        audioPlayerManager.playFile(path, profile.name, clipId = profile.id)
    }

    fun pausePlayback() = audioPlayerManager.pause()
    fun stopPlayback() = audioPlayerManager.stop()

    fun buildShareProfileAudioIntent(profile: VoiceProfileEntity): Intent? {
        val path = profile.sampleFilePath ?: return null
        val file = File(path)
        if (!file.exists()) {
            _isErrorStatus.value = true
            _statusMessage.value = "Cannot share: associated audio file is missing."
            return null
        }
        return audioExportManager.buildShareIntent(file, profile.name)
    }

    private fun validateSampleForm(): Pair<String, Long>? {
        val name = _voiceName.value.trim()
        if (name.isEmpty()) {
            _isErrorStatus.value = true
            _statusMessage.value = "Please enter a name for this voice profile."
            return null
        }
        val path = recordingState.value.recordedFilePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            _isErrorStatus.value = true
            _statusMessage.value = "Please record or import a voice sample first."
            return null
        }
        if (settings.value.requireExplicitConsent && !_consentChecked.value) {
            _isErrorStatus.value = true
            _statusMessage.value = "Explicit speaker consent confirmation is mandatory."
            return null
        }
        if (_ownershipType.value == ConsentOwnerType.DOCUMENTED_PERMISSION && _permissionNote.value.trim().isEmpty()) {
            _isErrorStatus.value = true
            _statusMessage.value = "Please provide a permission/authorization reference for a third-party speaker."
            return null
        }
        return path to recordingState.value.recordedDurationMs
    }

    fun saveVoiceProfileLocally(submitToReplicationApi: Boolean) {
        val validated = validateSampleForm() ?: return
        val (samplePath, durationMs) = validated

        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = null

            val profile = VoiceProfileEntity(
                name = _voiceName.value.trim(),
                providerType = VoiceProviderType.PERSONAL_SAMPLE.name,
                languageCode = _selectedLanguage.value,
                geminiVoiceName = null,
                styleDescriptor = if (_ownershipType.value == ConsentOwnerType.SELF) {
                    "Personal Voice Sample"
                } else {
                    "Authorized Speaker Sample"
                },
                sampleFilePath = samplePath,
                sampleDurationMs = durationMs,
                consentConfirmed = _consentChecked.value,
                consentOwnerType = _ownershipType.value.name,
                consentNote = _permissionNote.value.trim(),
                replicationStatus = ReplicationStatus.NOT_ATTEMPTED.name,
                replicationMessage = "",
                hasEncryptedVoiceId = false
            )
            val id = voiceProfileRepository.saveProfile(profile)
            val savedProfile = profile.copy(id = id)

            if (submitToReplicationApi) {
                when (
                    val res = voiceReplicationRepository.replicateVoiceProfile(
                        profile = savedProfile,
                        configuredEndpointUrl = settings.value.replicationEndpointUrl,
                        isOnline = isOnline.value
                    )
                ) {
                    is ReplicationResult.Replicated -> {
                        _isErrorStatus.value = false
                        _statusMessage.value = "${res.message} Encrypted Voice ID stored securely."
                    }
                    is ReplicationResult.Unavailable -> {
                        _isErrorStatus.value = true
                        _statusMessage.value = res.reason
                    }
                }
            } else {
                _isErrorStatus.value = false
                _statusMessage.value = "Saved '${savedProfile.name}' to local Room database with associated audio file."
            }

            _voiceName.value = ""
            _permissionNote.value = ""
            _consentChecked.value = false
            voiceRecorderManager.clearRecording()
            _isProcessing.value = false
        }
    }

    fun retryReplication(profile: VoiceProfileEntity) {
        viewModelScope.launch {
            _isProcessing.value = true
            when (
                val res = voiceReplicationRepository.replicateVoiceProfile(
                    profile = profile,
                    configuredEndpointUrl = settings.value.replicationEndpointUrl,
                    isOnline = isOnline.value
                )
            ) {
                is ReplicationResult.Replicated -> {
                    _isErrorStatus.value = false
                    _statusMessage.value = "${res.message} Encrypted Voice ID stored."
                }
                is ReplicationResult.Unavailable -> {
                    _isErrorStatus.value = true
                    _statusMessage.value = res.reason
                }
            }
            _isProcessing.value = false
        }
    }

    fun analyzeCurrentSampleTimbre() {
        val path = recordingState.value.recordedFilePath ?: run {
            _isErrorStatus.value = true
            _statusMessage.value = "Record or import a voice sample before running timbre analysis."
            return
        }
        if (!isOnline.value) {
            _isErrorStatus.value = true
            _statusMessage.value = "Internet connection is required for Gemini timbre analysis."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            _timbreAnalysis.value = null
            geminiTtsRepository.analyzeSampleTimbre(File(path))
                .onSuccess { summary ->
                    _timbreAnalysis.value = summary
                }
                .onFailure { err ->
                    _isErrorStatus.value = true
                    _statusMessage.value = err.localizedMessage ?: "Timbre analysis failed."
                }
            _isProcessing.value = false
        }
    }

    fun renameVoice(profile: VoiceProfileEntity, newName: String) {
        viewModelScope.launch {
            voiceProfileRepository.renameProfile(profile.id, newName)
        }
    }

    fun updateProfileDetails(
        profile: VoiceProfileEntity,
        newName: String,
        languageCode: String,
        styleDescriptor: String
    ) {
        viewModelScope.launch {
            voiceProfileRepository.updateProfileDetails(
                id = profile.id,
                newName = newName,
                languageCode = languageCode,
                styleDescriptor = styleDescriptor
            )
            _isErrorStatus.value = false
            _statusMessage.value = "Updated '${newName.trim().ifEmpty { profile.name }}' in local database."
        }
    }

    fun deleteVoice(profile: VoiceProfileEntity) {
        viewModelScope.launch {
            if (playbackState.value.activeClipId == profile.id) {
                audioPlayerManager.stop()
            }
            voiceProfileRepository.deleteProfile(profile)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    class Factory(
        private val voiceProfileRepository: VoiceProfileRepository,
        private val voiceRecorderManager: VoiceRecorderManager,
        private val audioPlayerManager: AudioPlayerManager,
        private val audioExportManager: AudioExportManager,
        private val voiceReplicationRepository: VoiceReplicationRepository,
        private val geminiTtsRepository: GeminiTtsRepository,
        private val settingsDataStore: SettingsDataStore,
        private val networkMonitor: NetworkMonitor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VoiceLibraryViewModel(
                voiceProfileRepository,
                voiceRecorderManager,
                audioPlayerManager,
                audioExportManager,
                voiceReplicationRepository,
                geminiTtsRepository,
                settingsDataStore,
                networkMonitor
            ) as T
        }
    }
}
