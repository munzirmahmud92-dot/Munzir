package com.example.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "AudioPlayerManager"

data class PlaybackState(
    val activeClipId: Long? = null,
    val activeTitle: String = "",
    val activeFilePath: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val waveformBars: List<Float> = emptyList(),
    val errorMessage: String? = null
)

class AudioPlayerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun playFile(
        filePath: String,
        title: String,
        clipId: Long? = null,
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0L) {
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isPaused = false,
                errorMessage = "Audio file not found or empty: ${file.name}"
            )
            return
        }

        stopInternal(clearActiveMetadata = false)

        scope.launch {
            val waveform = if (filePath.endsWith(".wav", ignoreCase = true)) {
                WavAudioUtils.extractWaveformAmplitudes(file, barCount = 44)
            } else {
                List(44) { idx ->
                    val phase = kotlin.math.sin(idx * 0.45) * 0.35 + 0.55
                    phase.toFloat().coerceIn(0.15f, 0.95f)
                }
            }

            try {
                val mp = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
                    val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
                    playbackParams = PlaybackParams().apply {
                        this.speed = clampedSpeed
                        this.pitch = clampedPitch
                    }
                    setOnCompletionListener {
                        stopProgressJob()
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            isPaused = false,
                            positionMs = 0L
                        )
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                        stopProgressJob()
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            isPaused = false,
                            errorMessage = "Playback failed (code $what/$extra)."
                        )
                        true
                    }
                    start()
                }
                mediaPlayer = mp
                _playbackState.value = PlaybackState(
                    activeClipId = clipId,
                    activeTitle = title,
                    activeFilePath = filePath,
                    isPlaying = true,
                    isPaused = false,
                    positionMs = 0L,
                    durationMs = mp.duration.toLong().coerceAtLeast(0L),
                    speed = speed.coerceIn(0.5f, 2.0f),
                    pitch = pitch.coerceIn(0.5f, 2.0f),
                    waveformBars = waveform,
                    errorMessage = null
                )
                startProgressJob()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start playback for $filePath", e)
                stopInternal(clearActiveMetadata = false)
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    errorMessage = e.localizedMessage ?: "Unable to play audio file."
                )
            }
        }
    }

    fun pause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                stopProgressJob()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = true,
                    positionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pause failed", e)
        }
    }

    fun resume() {
        val mp = mediaPlayer
        if (mp != null) {
            try {
                mp.start()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = true,
                    isPaused = false,
                    errorMessage = null
                )
                startProgressJob()
            } catch (e: Exception) {
                Log.e(TAG, "Resume failed", e)
            }
        } else {
            val state = _playbackState.value
            val path = state.activeFilePath
            if (path != null) {
                playFile(path, state.activeTitle, state.activeClipId, state.speed, state.pitch)
            }
        }
    }

    fun stop() {
        stopInternal(clearActiveMetadata = true)
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        try {
            val target = positionMs.toInt().coerceIn(0, mp.duration.coerceAtLeast(0))
            mp.seekTo(target)
            _playbackState.value = _playbackState.value.copy(positionMs = target.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Seek failed", e)
        }
    }

    fun updateSpeedAndPitch(speed: Float, pitch: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
        _playbackState.value = _playbackState.value.copy(speed = clampedSpeed, pitch = clampedPitch)
        val mp = mediaPlayer ?: return
        try {
            val wasPlaying = mp.isPlaying
            mp.playbackParams = PlaybackParams().apply {
                this.speed = clampedSpeed
                this.pitch = clampedPitch
            }
            if (!wasPlaying && _playbackState.value.isPaused) {
                mp.pause()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic speed/pitch update failed", e)
        }
    }

    private fun startProgressJob() {
        stopProgressJob()
        progressJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer ?: break
                try {
                    if (mp.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            positionMs = mp.currentPosition.toLong().coerceAtLeast(0L),
                            durationMs = mp.duration.toLong().coerceAtLeast(_playbackState.value.durationMs)
                        )
                    }
                } catch (_: IllegalStateException) {
                    break
                }
                delay(60L)
            }
        }
    }

    private fun stopProgressJob() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun stopInternal(clearActiveMetadata: Boolean) {
        stopProgressJob()
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
        if (clearActiveMetadata) {
            _playbackState.value = PlaybackState()
        } else {
            _playbackState.value = _playbackState.value.copy(isPlaying = false, isPaused = false, positionMs = 0L)
        }
    }

    fun release() {
        stopInternal(clearActiveMetadata = true)
    }
}
