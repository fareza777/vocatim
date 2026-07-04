package com.vocatim.app.data.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams 16-bit PCM mono samples into a WAV file. Chunk sizes in the header
 * are placeholders until [close] rewrites them, so a crash mid-recording
 * leaves a file that [WavStreamReader] can still salvage from its length.
 */
class WavFileWriter(
    private val file: File,
    private val sampleRate: Int = WavDecoder.WHISPER_SAMPLE_RATE,
) : Closeable {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        raf.setLength(0)
        raf.write(buildHeader(sampleRate, dataSize = 0))
    }

    fun write(samples: ShortArray, count: Int = samples.size) {
        val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buffer.putShort(samples[i])
        raf.write(buffer.array())
        dataBytes += count * 2L
    }

    val durationMs: Long
        get() = dataBytes / 2 * 1000L / sampleRate

    override fun close() {
        raf.seek(0)
        raf.write(buildHeader(sampleRate, dataBytes))
        raf.close()
    }

    private fun buildHeader(sampleRate: Int, dataSize: Long): ByteArray {
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt((36 + dataSize).toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // byte rate
        buffer.putShort(2) // block align
        buffer.putShort(16) // bits per sample
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize.toInt())
        return buffer.array()
    }
}
