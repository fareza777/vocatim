package com.vocatim.app.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavDecodeException(message: String) : Exception(message)

data class DecodedAudio(
    /** Mono PCM samples in [-1, 1] at [WavDecoder.WHISPER_SAMPLE_RATE]. */
    val samples: FloatArray,
) {
    val durationMs: Long get() = samples.size * 1000L / WavDecoder.WHISPER_SAMPLE_RATE

    override fun equals(other: Any?): Boolean =
        other is DecodedAudio && samples.contentEquals(other.samples)

    override fun hashCode(): Int = samples.contentHashCode()
}

/**
 * Decodes PCM 16-bit WAV files into the format Whisper needs:
 * 16kHz mono float. Stereo is mixed down; other sample rates are
 * linearly resampled. Compressed WAV variants are rejected.
 */
object WavDecoder {
    const val WHISPER_SAMPLE_RATE = 16_000

    fun decode(bytes: ByteArray): DecodedAudio {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 12 ||
            buffer.getInt(0) != RIFF_MAGIC ||
            buffer.getInt(8) != WAVE_MAGIC
        ) {
            throw WavDecodeException("Not a WAV file (missing RIFF/WAVE header)")
        }

        var format = -1
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0

        // Walk the chunk list: chunks other than fmt/data (LIST, fact, ...) are skipped.
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId = buffer.getInt(pos)
            val chunkSize = buffer.getInt(pos + 4)
            if (chunkSize < 0) throw WavDecodeException("Corrupt WAV chunk size")
            val chunkStart = pos + 8
            when (chunkId) {
                FMT_MAGIC -> {
                    if (chunkStart + 16 > bytes.size) throw WavDecodeException("Truncated fmt chunk")
                    format = buffer.getShort(chunkStart).toInt() and 0xFFFF
                    channels = buffer.getShort(chunkStart + 2).toInt() and 0xFFFF
                    sampleRate = buffer.getInt(chunkStart + 4)
                    bitsPerSample = buffer.getShort(chunkStart + 14).toInt() and 0xFFFF
                }
                DATA_MAGIC -> {
                    dataOffset = chunkStart
                    dataSize = minOf(chunkSize, bytes.size - chunkStart)
                }
            }
            // Chunks are word-aligned: odd sizes are padded with one byte.
            pos = chunkStart + chunkSize + (chunkSize and 1)
        }

        if (format == -1) throw WavDecodeException("WAV has no fmt chunk")
        if (dataOffset == -1) throw WavDecodeException("WAV has no data chunk")
        if (format != FORMAT_PCM) {
            throw WavDecodeException("Unsupported WAV format $format (only PCM is supported)")
        }
        if (bitsPerSample != 16) {
            throw WavDecodeException("Unsupported bit depth $bitsPerSample (only 16-bit is supported)")
        }
        if (channels !in 1..2) {
            throw WavDecodeException("Unsupported channel count $channels")
        }
        if (sampleRate < 8_000) {
            throw WavDecodeException("Unsupported sample rate $sampleRate")
        }

        val frameCount = dataSize / (2 * channels)
        if (frameCount == 0) throw WavDecodeException("WAV contains no audio data")

        val mono = FloatArray(frameCount)
        for (i in 0 until frameCount) {
            val base = dataOffset + i * 2 * channels
            var sum = 0f
            for (c in 0 until channels) {
                sum += buffer.getShort(base + c * 2) / 32768f
            }
            mono[i] = sum / channels
        }

        val samples = if (sampleRate == WHISPER_SAMPLE_RATE) mono else resample(mono, sampleRate)
        return DecodedAudio(samples)
    }

    private fun resample(input: FloatArray, fromRate: Int): FloatArray {
        val outLength = (input.size.toLong() * WHISPER_SAMPLE_RATE / fromRate).toInt()
        val output = FloatArray(outLength)
        val step = fromRate.toDouble() / WHISPER_SAMPLE_RATE
        for (i in 0 until outLength) {
            val srcPos = i * step
            val idx = srcPos.toInt()
            val frac = (srcPos - idx).toFloat()
            val a = input[idx.coerceAtMost(input.size - 1)]
            val b = input[(idx + 1).coerceAtMost(input.size - 1)]
            output[i] = a + (b - a) * frac
        }
        return output
    }

    // Chunk ids as little-endian int32 of their ASCII bytes.
    private const val RIFF_MAGIC = 0x46464952 // "RIFF"
    private const val WAVE_MAGIC = 0x45564157 // "WAVE"
    private const val FMT_MAGIC = 0x20746d66  // "fmt "
    private const val DATA_MAGIC = 0x61746164 // "data"
    private const val FORMAT_PCM = 1
}
