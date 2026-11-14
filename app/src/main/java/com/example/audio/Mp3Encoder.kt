package com.example.audio

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.PsyModel
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.MPGLib
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Pure-JVM on-device LAME MP3 encoder powered by Jump3r without desktop javax.sound.sampled dependencies.
 */
object Mp3Encoder {

    fun encodeWavToMp3(
        wavFile: File,
        mp3File: File,
        bitrateKbps: Int = 192
    ): File {
        val wavInfo = WavAudioUtils.readWavInfo(wavFile)
            ?: throw IllegalArgumentException("Invalid source WAV file: ${wavFile.name}")
        if (wavInfo.bitsPerSample != 16) {
            throw IllegalArgumentException("Only 16-bit PCM WAV files are supported for MP3 encoding.")
        }

        mp3File.parentFile?.mkdirs()

        val ga = GainAnalysis()
        val bs = BitStream()
        val presets = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val psy = PsyModel()
        val mpg = MPGLib()
        val lame = Lame()

        lame.setModules(ga, bs, presets, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        presets.setModules(lame)
        qupvt.setModules(tak, rv, psy)
        qu.setModules(bs, rv, qupvt, tak)
        vbr.setModules(lame, bs, ver)
        id3.setModules(bs, ver)
        tak.setModules(qupvt)
        rv.setModules(bs)

        val gfp = lame.lame_init()
        id3.id3tag_init(gfp)
        gfp.num_channels = wavInfo.channels.coerceIn(1, 2)
        gfp.in_samplerate = wavInfo.sampleRate
        gfp.out_samplerate = wavInfo.sampleRate
        gfp.brate = bitrateKbps
        gfp.quality = 2 // High quality
        gfp.VBR = VbrMode.vbr_off
        gfp.mode = if (gfp.num_channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO

        val initResult = lame.lame_init_params(gfp)
        if (initResult < 0) {
            throw IllegalStateException("LAME initialization failed with code $initResult")
        }

        val samplesPerChunk = 1152 * 4
        val bytesPerSampleFrame = 2 * gfp.num_channels
        val pcmBuffer = ByteArray(samplesPerChunk * bytesPerSampleFrame)
        val leftInt = IntArray(samplesPerChunk)
        val rightInt = IntArray(samplesPerChunk)
        val mp3Buffer = ByteArray((1.25 * samplesPerChunk + 7200).toInt())

        try {
            FileInputStream(wavFile).use { fis ->
                var skipped = 0L
                while (skipped < wavInfo.dataOffset) {
                    val s = fis.skip(wavInfo.dataOffset - skipped)
                    if (s <= 0) break
                    skipped += s
                }

                FileOutputStream(mp3File).use { fos ->
                    var remainingDataBytes = wavInfo.dataSize
                    while (remainingDataBytes > 0) {
                        val toRead = minOf(pcmBuffer.size, remainingDataBytes)
                        val bytesRead = fis.read(pcmBuffer, 0, toRead)
                        if (bytesRead <= 0) break
                        remainingDataBytes -= bytesRead

                        val sampleCount = bytesRead / bytesPerSampleFrame
                        if (sampleCount <= 0) continue

                        if (gfp.num_channels == 1) {
                            for (i in 0 until sampleCount) {
                                val lo = pcmBuffer[i * 2].toInt() and 0xFF
                                val hi = pcmBuffer[i * 2 + 1].toInt() shl 8
                                val sample16 = (hi or lo).toShort()
                                // Jump3r lame_encode_buffer_int scales by 1/65536
                                val scaled = sample16.toInt() shl 16
                                leftInt[i] = scaled
                                rightInt[i] = scaled
                            }
                        } else {
                            for (i in 0 until sampleCount) {
                                val base = i * 4
                                val lLo = pcmBuffer[base].toInt() and 0xFF
                                val lHi = pcmBuffer[base + 1].toInt() shl 8
                                val rLo = pcmBuffer[base + 2].toInt() and 0xFF
                                val rHi = pcmBuffer[base + 3].toInt() shl 8
                                leftInt[i] = ((lHi or lLo).toShort().toInt()) shl 16
                                rightInt[i] = ((rHi or rLo).toShort().toInt()) shl 16
                            }
                        }

                        val encodedBytes = lame.lame_encode_buffer_int(
                            gfp,
                            leftInt,
                            rightInt,
                            sampleCount,
                            mp3Buffer,
                            0,
                            mp3Buffer.size
                        )
                        if (encodedBytes < 0) {
                            throw IllegalStateException("LAME encode error code $encodedBytes")
                        }
                        if (encodedBytes > 0) {
                            fos.write(mp3Buffer, 0, encodedBytes)
                        }
                    }

                    val flushed = lame.lame_encode_flush(gfp, mp3Buffer, 0, mp3Buffer.size)
                    if (flushed > 0) {
                        fos.write(mp3Buffer, 0, flushed)
                    }
                    fos.flush()
                }
            }
        } finally {
            lame.lame_close(gfp)
        }

        if (!mp3File.exists() || mp3File.length() == 0L) {
            throw IllegalStateException("MP3 output file is empty after encoding.")
        }
        return mp3File
    }
}
