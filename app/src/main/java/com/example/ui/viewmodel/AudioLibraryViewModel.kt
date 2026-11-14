package com.example.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExportManager
import com.example.audio.AudioPlayerManager
import com.example.audio.ExportResult
import com.example.audio.PlaybackState
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import com.example.data.preferences.SettingsDataStore
import com.example.data.preferences.StudioSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class DownloadFormatFilter {
    ALL,
    WAV,
    MP3
}

class AudioLibraryViewModel(
    private val audioClipDao: AudioClipDao,
    private val audioExportManager: AudioExportManager,
    private val audioPlayerManager: AudioPlayerManager,
    settingsDataStore: SettingsDataStore
) : ViewModel() {

    val allClips: StateFlow<List<AudioClipEntity>> = audioClipDao.observeAllClips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedClips: StateFlow<List<AudioClipEntity>> = audioClipDao.observeDownloadedClips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playbackState: StateFlow<PlaybackState> = audioPlayerManager.playbackState

    val settings: StateFlow<StudioSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioSettings())

    private val _formatFilter = MutableStateFlow(DownloadFormatFilter.ALL)
    val formatFilter: StateFlow<DownloadFormatFilter> = _formatFilter.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _busyClipId = MutableStateFlow<Long?>(null)
    val busyClipId: StateFlow<Long?> = _busyClipId.asStateFlow()

    fun setFormatFilter(filter: DownloadFormatFilter) {
        _formatFilter.value = filter
    }

    fun playClip(clip: AudioClipEntity, preferMp3: Boolean = false) {
        val current = playbackState.value
        if (current.activeClipId == clip.id && current.isPaused) {
            audioPlayerManager.resume()
            return
        }
        val path = if (preferMp3 && clip.mp3FilePath != null) {
            clip.mp3FilePath
        } else {
            clip.wavFilePath ?: clip.mp3FilePath
        } ?: run {
            _actionMessage.value = "Audio file is unavailable on disk."
            return
        }
        audioPlayerManager.playFile(path, clip.title, clip.id, 1.0f, 1.0f)
    }

    fun pausePlayback() = audioPlayerManager.pause()
    fun stopPlayback() = audioPlayerManager.stop()
    fun seekPlayback(ms: Long) = audioPlayerManager.seekTo(ms)

    fun exportWav(clip: AudioClipEntity, successPrefix: String) {
        viewModelScope.launch {
            _busyClipId.value = clip.id
            when (val res = audioExportManager.exportWav(clip)) {
                is ExportResult.Success -> {
                    _actionMessage.value = "$successPrefix ${res.exportedFile.name} (${res.verification.details})"
                }
                is ExportResult.Error -> _actionMessage.value = res.message
            }
            _busyClipId.value = null
        }
    }

    fun exportMp3(clip: AudioClipEntity, successPrefix: String) {
        viewModelScope.launch {
            _busyClipId.value = clip.id
            val bitrate = settings.value.mp3Quality.bitrateKbps
            when (val res = audioExportManager.exportMp3(clip, bitrate)) {
                is ExportResult.Success -> {
                    _actionMessage.value = "$successPrefix ${res.exportedFile.name} (${bitrate} kbps · ${res.verification.details})"
                }
                is ExportResult.Error -> _actionMessage.value = res.message
            }
            _busyClipId.value = null
        }
    }

    fun verifyClip(clip: AudioClipEntity) {
        val targetPath = clip.mp3FilePath ?: clip.wavFilePath
        if (targetPath == null) {
            _actionMessage.value = "No exported file path found."
            return
        }
        val result = audioExportManager.verifyPlayableAudio(File(targetPath))
        _actionMessage.value = if (result.isValid) {
            "Verified ${result.formatLabel}: ${result.details}, duration ${result.durationMs / 1000.0}s"
        } else {
            "Verification failed: ${result.details}"
        }
    }

    fun buildShareIntent(clip: AudioClipEntity): Intent? {
        val file = (clip.mp3FilePath?.let { File(it) }?.takeIf { it.exists() })
            ?: (clip.wavFilePath?.let { File(it) }?.takeIf { it.exists() })
            ?: return null
        return audioExportManager.buildShareIntent(file, clip.title)
    }

    fun renameClip(clip: AudioClipEntity, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            audioClipDao.renameClip(clip.id, trimmed)
        }
    }

    fun deleteClip(clip: AudioClipEntity) {
        viewModelScope.launch {
            if (playbackState.value.activeClipId == clip.id) {
                audioPlayerManager.stop()
            }
            clip.wavFilePath?.let { File(it).delete() }
            clip.mp3FilePath?.let { File(it).delete() }
            audioClipDao.deleteClipById(clip.id)
        }
    }

    fun dismissMessage() {
        _actionMessage.value = null
    }

    class Factory(
        private val audioClipDao: AudioClipDao,
        private val audioExportManager: AudioExportManager,
        private val audioPlayerManager: AudioPlayerManager,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AudioLibraryViewModel(
                audioClipDao,
                audioExportManager,
                audioPlayerManager,
                settingsDataStore
            ) as T
        }
    }
}
