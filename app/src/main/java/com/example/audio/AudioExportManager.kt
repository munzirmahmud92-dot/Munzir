package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "AudioExportManager"

data class AudioVerificationResult(
    val isValid: Boolean,
    val formatLabel: String,
    val durationMs: Long,
    val sampleRate: Int,
    val details: String
)

data class StorageStats(
    val historyBytes: Long,
    val exportsBytes: Long,
    val voiceSamplesBytes: Long
) {
    val totalBytes: Long get() = historyBytes + exportsBytes + voiceSamplesBytes
}

sealed interface ExportResult {
    data class Success(
        val updatedClip: AudioClipEntity,
        val exportedFile: File,
        val mediaStoreUri: String?,
        val verification: AudioVerificationResult
    ) : ExportResult

    data class Error(val message: String) : ExportResult
}

class AudioExportManager(
    private val context: Context,
    private val audioClipDao: AudioClipDao
) {
    private val exportsDir: File
        get() = File(context.filesDir, "exports").apply { mkdirs() }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").trim('_')
        return cleaned.ifBlank { "studio_audio" }.take(40)
    }

    suspend fun exportWav(clip: AudioClipEntity): ExportResult = withContext(Dispatchers.IO) {
        try {
            val sourcePath = clip.wavFilePath
                ?: return@withContext ExportResult.Error("No source WAV audio found for this clip.")
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists() || sourceFile.length() < 44L) {
                return@withContext ExportResult.Error("Source WAV file is missing or corrupted.")
            }

            val baseName = "${sanitizeFileName(clip.title)}_${clip.id}"
            val exportFile = File(exportsDir, "$baseName.wav")
            sourceFile.copyTo(exportFile, overwrite = true)

            val verification = verifyPlayableAudio(exportFile)
            if (!verification.isValid) {
                exportFile.delete()
                return@withContext ExportResult.Error("WAV verification failed: ${verification.details}")
            }

            val mediaUri = saveToMediaStoreOrMusicDir(exportFile, "$baseName.wav", "audio/wav")
            val updated = clip.copy(
                isDownloaded = true,
                wavFilePath = exportFile.absolutePath,
                mediaStoreWavUri = mediaUri,
                fileSizeBytes = exportFile.length(),
                isVerifiedPlayable = true,
                durationMs = verification.durationMs.takeIf { it > 0 } ?: clip.durationMs
            )
            audioClipDao.updateClip(updated)
            ExportResult.Success(updated, exportFile, mediaUri, verification)
        } catch (e: Exception) {
            Log.e(TAG, "WAV export failed", e)
            ExportResult.Error("WAV export failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    suspend fun exportMp3(clip: AudioClipEntity, bitrateKbps: Int): ExportResult = withContext(Dispatchers.Default) {
        try {
            val sourcePath = clip.wavFilePath
                ?: return@withContext ExportResult.Error("No source WAV audio found to encode to MP3.")
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists() || sourceFile.length() < 44L) {
                return@withContext ExportResult.Error("Source WAV file is missing or corrupted.")
            }

            val baseName = "${sanitizeFileName(clip.title)}_${clip.id}"
            val mp3File = File(exportsDir, "$baseName.mp3")

            Mp3Encoder.encodeWavToMp3(sourceFile, mp3File, bitrateKbps)

            val verification = verifyPlayableAudio(mp3File)
            if (!verification.isValid) {
                mp3File.delete()
                return@withContext ExportResult.Error("MP3 verification failed: ${verification.details}")
            }

            val mediaUri = withContext(Dispatchers.IO) {
                saveToMediaStoreOrMusicDir(mp3File, "$baseName.mp3", "audio/mpeg")
            }

            val updated = clip.copy(
                isDownloaded = true,
                mp3FilePath = mp3File.absolutePath,
                mediaStoreMp3Uri = mediaUri,
                fileSizeBytes = mp3File.length(),
                isVerifiedPlayable = true,
                durationMs = verification.durationMs.takeIf { it > 0 } ?: clip.durationMs
            )
            withContext(Dispatchers.IO) {
                audioClipDao.updateClip(updated)
            }
            ExportResult.Success(updated, mp3File, mediaUri, verification)
        } catch (e: Exception) {
            Log.e(TAG, "MP3 export failed", e)
            ExportResult.Error("MP3 encoding failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    fun verifyPlayableAudio(file: File): AudioVerificationResult {
        if (!file.exists() || file.length() <= 0L) {
            return AudioVerificationResult(false, "Unknown", 0L, 0, "File does not exist or is empty.")
        }

        if (file.extension.equals("wav", ignoreCase = true)) {
            val wavInfo = WavAudioUtils.readWavInfo(file)
                ?: return AudioVerificationResult(false, "WAV", 0L, 0, "Invalid RIFF/WAVE header.")
            if (wavInfo.durationMs <= 0L) {
                return AudioVerificationResult(false, "WAV", 0L, wavInfo.sampleRate, "WAV audio duration is 0 ms.")
            }
            return AudioVerificationResult(
                isValid = true,
                formatLabel = "WAV PCM ${wavInfo.bitsPerSample}-bit",
                durationMs = wavInfo.durationMs,
                sampleRate = wavInfo.sampleRate,
                details = "${wavInfo.sampleRate} Hz · ${wavInfo.channels}ch · ${file.length() / 1024} KB"
            )
        }

        // Verify MP3 via header check + MediaExtractor / MediaMetadataRetriever
        val header = ByteArray(10)
        file.inputStream().use { it.read(header) }
        val hasId3 = header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()
        val hasMpegSync = (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0
        if (!hasId3 && !hasMpegSync) {
            return AudioVerificationResult(false, "MP3", 0L, 0, "Missing MPEG frame sync or ID3 header.")
        }

        var sampleRate = 24_000
        var hasAudioTrack = false
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    hasAudioTrack = true
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor verify warning", e)
        } finally {
            extractor.release()
        }

        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever verify warning", e)
        } finally {
            runCatching { retriever.release() }
        }

        if (!hasAudioTrack || durationMs <= 0L) {
            return AudioVerificationResult(false, "MP3", durationMs, sampleRate, "MP3 stream is not playable.")
        }

        return AudioVerificationResult(
            isValid = true,
            formatLabel = "MP3 MPEG Layer III",
            durationMs = durationMs,
            sampleRate = sampleRate,
            details = "$sampleRate Hz · ${file.length() / 1024} KB"
        )
    }

    private fun saveToMediaStoreOrMusicDir(file: File, displayName: String, mimeType: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Munzir voice cloner")
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } else {
                val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?.let { File(it, "Munzir voice cloner").apply { mkdirs() } }
                    ?: return null
                val dest = File(musicDir, displayName)
                file.copyTo(dest, overwrite = true)
                dest.absolutePath
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore write warning (app-specific export remains valid)", e)
            null
        }
    }

    fun buildShareIntent(file: File, title: String): Intent {
        val mime = if (file.extension.equals("mp3", ignoreCase = true)) "audio/mpeg" else "audio/wav"
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun computeStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        StorageStats(
            historyBytes = dirSize(File(context.filesDir, "audio")),
            exportsBytes = dirSize(File(context.filesDir, "exports")),
            voiceSamplesBytes = dirSize(File(context.filesDir, "voice_samples"))
        )
    }

    suspend fun clearUnexportedCache() = withContext(Dispatchers.IO) {
        val unexported = audioClipDao.getUnexportedClips()
        unexported.forEach { clip ->
            clip.wavFilePath?.let { File(it).delete() }
            clip.mp3FilePath?.let { File(it).delete() }
        }
        audioClipDao.deleteUnexportedClips()
    }

    suspend fun deleteAllLocalAudioFiles() = withContext(Dispatchers.IO) {
        listOf("audio", "exports", "voice_samples").forEach { folder ->
            File(context.filesDir, folder).deleteRecursively()
        }
        audioClipDao.deleteAllClips()
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
