package com.vocatim.app.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavDecoderTest {

    @Test
    fun `decodes 16kHz mono pcm16`() {
        val samples = shortArrayOf(0, 16384, -16384, 32767, -32768)
        val wav = buildWav(samples, sampleRate = 16_000, channels = 1)

        val decoded = WavDecoder.decode(wav)

        assertEquals(5, decoded.samples.size)
        assertEquals(0f, decoded.samples[0], 1e-4f)
        assertEquals(0.5f, decoded.samples[1], 1e-4f)
        assertEquals(-0.5f, decoded.samples[2], 1e-4f)
        assertEquals(1f, decoded.samples[3], 1e-2f)
        assertEquals(-1f, decoded.samples[4], 1e-4f)
    }

    @Test
    fun `mixes stereo down to mono`() {
        // Interleaved L/R frames: (1000, 3000) and (-2000, 2000).
        val samples = shortArrayOf(1000, 3000, -2000, 2000)
        val wav = buildWav(samples, sampleRate = 16_000, channels = 2)

        val decoded = WavDecoder.decode(wav)

        assertEquals(2, decoded.samples.size)
        assertEquals(2000 / 32768f, decoded.samples[0], 1e-4f)
        assertEquals(0f, decoded.samples[1], 1e-4f)
    }

    @Test
    fun `resamples 8kHz to 16kHz doubling sample count`() {
        val samples = ShortArray(8_000) { (it % 100).toShort() }
        val wav = buildWav(samples, sampleRate = 8_000, channels = 1)

        val decoded = WavDecoder.decode(wav)

        assertEquals(16_000, decoded.samples.size)
        assertEquals(1000L, decoded.durationMs)
    }

    @Test
    fun `computes duration from sample count`() {
        val samples = ShortArray(16_000 * 3)
        val wav = buildWav(samples, sampleRate = 16_000, channels = 1)

        assertEquals(3000L, WavDecoder.decode(wav).durationMs)
    }

    @Test
    fun `skips unknown chunks before data`() {
        val samples = shortArrayOf(100, 200, 300)
        val wav = buildWav(samples, sampleRate = 16_000, channels = 1, extraChunk = true)

        assertEquals(3, WavDecoder.decode(wav).samples.size)
    }

    @Test
    fun `rejects non-wav bytes`() {
        val ex = assertThrows(WavDecodeException::class.java) {
            WavDecoder.decode(ByteArray(64) { 1 })
        }
        assertTrue(ex.message!!.contains("RIFF"))
    }

    @Test
    fun `rejects non-pcm format`() {
        val wav = buildWav(shortArrayOf(1, 2), sampleRate = 16_000, channels = 1, format = 3)
        val ex = assertThrows(WavDecodeException::class.java) { WavDecoder.decode(wav) }
        assertTrue(ex.message!!.contains("PCM"))
    }

    @Test
    fun `rejects empty data chunk`() {
        val wav = buildWav(ShortArray(0), sampleRate = 16_000, channels = 1)
        assertThrows(WavDecodeException::class.java) { WavDecoder.decode(wav) }
    }

    private fun buildWav(
        samples: ShortArray,
        sampleRate: Int,
        channels: Int,
        format: Int = 1,
        extraChunk: Boolean = false,
    ): ByteArray {
        val dataSize = samples.size * 2
        val extraSize = if (extraChunk) 8 + 4 else 0
        val buffer = ByteBuffer.allocate(44 + extraSize + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + extraSize + dataSize)
        buffer.put("WAVE".toByteArray())

        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(format.toShort())
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * 2)
        buffer.putShort((channels * 2).toShort())
        buffer.putShort(16)

        if (extraChunk) {
            buffer.put("LIST".toByteArray())
            buffer.putInt(4)
            buffer.put("INFO".toByteArray())
        }

        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        samples.forEach { buffer.putShort(it) }

        return buffer.array()
    }
}
