package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "VoiceRecorderManager"
private const val SAMPLE_RATE = 16_000

data class RecordingState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val liveAmplitude: Float = 0f,
    val liveWaveform: List<Float> = emptyList(),
    val recordedFilePath: String? = null,
    val recordedDurationMs: Long = 0L,
    val errorMessage: String? = null
)

class VoiceRecorderManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (_recordingState.value.isRecording) return

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            _recordingState.value = RecordingState(errorMessage = "Audio recording hardware is unavailable on this device.")
            return
        }

        val bufferSize = (minBuf * 2).coerceAtLeast(4096)
        val outDir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val outFile = File(outDir, "sample_${System.currentTimeMillis()}.wav")

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                _recordingState.value = RecordingState(errorMessage = "Unable to initialize microphone recorder.")
                return
            }

            audioRecord = recorder
            recorder.startRecording()

            _recordingState.value = RecordingState(
                isRecording = true,
                elapsedMs = 0L,
                liveAmplitude = 0f,
                liveWaveform = emptyList(),
                recordedFilePath = null,
                recordedDurationMs = 0L,
                errorMessage = null
            )

            recordingJob = scope.launch {
                val startTime = System.currentTimeMillis()
                var totalPcmBytes = 0L
                val shortBuf = ShortArray(bufferSize / 2)
                val byteBuf = ByteArray(bufferSize)
                val waveHistory = ArrayList<Float>(40)

                RandomAccessFile(outFile, "rw").use { raf ->
                    // Reserve 44-byte WAV header
                    raf.write(ByteArray(44))

                    while (isActive && _recordingState.value.isRecording) {
                        val readShorts = recorder.read(shortBuf, 0, shortBuf.size)
                        if (readShorts > 0) {
                            var sumSq = 0.0
                            for (i in 0 until readShorts) {
                                val s = shortBuf[i]
                                byteBuf[i * 2] = (s.toInt() and 0xFF).toByte()
                                byteBuf[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                                val norm = abs(s.toDouble()) / 32768.0
                                sumSq += norm * norm
                            }
                            raf.write(byteBuf, 0, readShorts * 2)
                            totalPcmBytes += readShorts * 2L

                            val rms = sqrt(sumSq / readShorts).toFloat()
                            val normAmp = (rms * 3.5f).coerceIn(0.05f, 1.0f)
                            waveHistory.add(normAmp)
                            if (waveHistory.size > 40) waveHistory.removeAt(0)

                            _recordingState.value = _recordingState.value.copy(
                                elapsedMs = System.currentTimeMillis() - startTime,
                                liveAmplitude = normAmp,
                                liveWaveform = waveHistory.toList()
                            )
                        }
                    }

                    WavAudioUtils.writeWavHeaderAtStart(
                        raf = raf,
                        pcmDataBytes = totalPcmBytes,
                        sampleRate = SAMPLE_RATE,
                        channels = 1,
                        bitsPerSample = 16
                    )
                }

                val durationMs = (totalPcmBytes * 1000L) / (SAMPLE_RATE * 2L)
                _recordingState.value = _recordingState.value.copy(
                    isRecording = false,
                    recordedFilePath = outFile.absolutePath,
                    recordedDurationMs = durationMs
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission missing", e)
            _recordingState.value = RecordingState(errorMessage = "Microphone permission is required to record.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _recordingState.value = RecordingState(errorMessage = e.localizedMessage ?: "Recording failed.")
        }
    }

    fun stopRecording() {
        if (!_recordingState.value.isRecording) return
        _recordingState.value = _recordingState.value.copy(isRecording = false)
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord stop error", e)
        } finally {
            audioRecord?.release()
            audioRecord = null
        }
    }

    suspend fun importAudioFromUri(uri: Uri): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        runCatching {
            val outDir = File(context.filesDir, "voice_samples").apply { mkdirs() }
            val tempRaw = File(outDir, "import_raw_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempRaw).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open selected audio file.")

            if (tempRaw.length() <= 0L) {
                tempRaw.delete()
                throw IllegalStateException("Selected audio file is empty.")
            }

            // If it's already a valid WAV, keep directly; otherwise decode to WAV via MediaExtractor + MediaCodec
            val wavInfo = WavAudioUtils.readWavInfo(tempRaw)
            val finalFile = File(outDir, "imported_${System.currentTimeMillis()}.wav")
            if (wavInfo != null && wavInfo.durationMs > 0L) {
                tempRaw.renameTo(finalFile)
                _recordingState.value = RecordingState(
                    recordedFilePath = finalFile.absolutePath,
                    recordedDurationMs = wavInfo.durationMs
                )
                return@runCatching finalFile.absolutePath to wavInfo.durationMs
            }

            val decoded = decodeAudioToWav(tempRaw, finalFile)
            tempRaw.delete()
            if (!decoded) {
                throw IllegalStateException("Unsupported audio format. Please pick a WAV, MP3, or M4A file.")
            }
            val info = WavAudioUtils.readWavInfo(finalFile)
                ?: throw IllegalStateException("Failed to verify imported WAV file.")
            _recordingState.value = RecordingState(
                recordedFilePath = finalFile.absolutePath,
                recordedDurationMs = info.durationMs
            )
            finalFile.absolutePath to info.durationMs
        }
    }

    private fun decodeAudioToWav(inputFile: File, outputWav: File): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = trackFormat
                    break
                }
            }
            if (audioTrack < 0 || format == null) return false

            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val pcmStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            val timeoutUs = 10_000L

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        if (bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.get(chunk)
                            pcmStream.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }

            val pcmBytes = pcmStream.toByteArray()
            if (pcmBytes.isEmpty()) return false
            WavAudioUtils.pcmToWavFile(
                pcmBytes = pcmBytes,
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = 16,
                outputFile = outputWav
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec decode failed", e)
            false
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    fun clearRecording() {
        stopRecording()
        _recordingState.value = RecordingState()
    }
}
