package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

data class WavInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val dataOffset: Int,
    val dataSize: Int,
    val durationMs: Long
)

object WavAudioUtils {

    fun pcmToWavFile(
        pcmBytes: ByteArray,
        sampleRate: Int = 24_000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
        outputFile: File
    ): File {
        outputFile.parentFile?.mkdirs()
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmBytes.size
        val totalAudioLen = totalDataLen + 36

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
        }.array()

        FileOutputStream(outputFile).use { fos ->
            fos.write(header)
            fos.write(pcmBytes)
            fos.flush()
        }
        return outputFile
    }

    fun writeWavHeaderAtStart(
        raf: RandomAccessFile,
        pcmDataBytes: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((pcmDataBytes + 36L).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmDataBytes.toInt())
        }.array()
        raf.seek(0L)
        raf.write(header)
    }

    fun readWavInfo(wavFile: File): WavInfo? {
        if (!wavFile.exists() || wavFile.length() < 44L) return null
        val bytes = wavFile.readBytes()
        if (bytes.size < 44) return null

        val riff = String(bytes, 0, 4, Charsets.US_ASCII)
        val wave = String(bytes, 8, 4, Charsets.US_ASCII)
        if (riff != "RIFF" || wave != "WAVE") return null

        var offset = 12
        var sampleRate = 24_000
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = 44
        var dataSize = (bytes.size - 44).coerceAtLeast(0)

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int
            val payloadStart = offset + 8
            if (chunkSize < 0 || payloadStart + chunkSize > bytes.size) break

            when (chunkId) {
                "fmt " -> if (chunkSize >= 16) {
                    val bb = ByteBuffer.wrap(bytes, payloadStart, chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                    bb.short // audioFormat
                    channels = bb.short.toInt().coerceAtLeast(1)
                    sampleRate = bb.int.coerceAtLeast(8_000)
                    bb.int // byteRate
                    bb.short // blockAlign
                    bitsPerSample = bb.short.toInt().coerceAtLeast(8)
                }
                "data" -> {
                    dataOffset = payloadStart
                    dataSize = chunkSize
                    break
                }
            }
            offset = payloadStart + chunkSize + (chunkSize and 1)
        }

        val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)
        val durationMs = if (bytesPerSecond > 0) (dataSize * 1000L) / bytesPerSecond else 0L
        return WavInfo(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            dataOffset = dataOffset,
            dataSize = dataSize,
            durationMs = durationMs
        )
    }

    fun extractWaveformAmplitudes(wavFile: File, barCount: Int = 44): List<Float> {
        val info = readWavInfo(wavFile) ?: return List(barCount) { 0.15f }
        if (info.dataSize < 2 || info.bitsPerSample != 16) return List(barCount) { 0.2f }
        val bytes = wavFile.readBytes()
        val end = (info.dataOffset + info.dataSize).coerceAtMost(bytes.size)
        val totalSamples = (end - info.dataOffset) / 2
        if (totalSamples <= 0) return List(barCount) { 0.15f }

        val samplesPerBar = (totalSamples / barCount).coerceAtLeast(1)
        val result = ArrayList<Float>(barCount)

        for (bar in 0 until barCount) {
            val startSample = bar * samplesPerBar
            if (startSample >= totalSamples) {
                result.add(0.12f)
                continue
            }
            val endSample = ((bar + 1) * samplesPerBar).coerceAtMost(totalSamples)
            var sumSq = 0.0
            var count = 0
            val step = ((endSample - startSample) / 64).coerceAtLeast(1)
            var idx = startSample
            while (idx < endSample) {
                val byteIdx = info.dataOffset + idx * 2
                if (byteIdx + 1 < end) {
                    val sample = ((bytes[byteIdx + 1].toInt() shl 8) or (bytes[byteIdx].toInt() and 0xFF)).toShort()
                    val norm = abs(sample.toDouble()) / 32768.0
                    sumSq += norm * norm
                    count++
                }
                idx += step
            }
            val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0.1f
            result.add((rms * 2.4f).coerceIn(0.10f, 1.0f))
        }
        return result
    }
}
