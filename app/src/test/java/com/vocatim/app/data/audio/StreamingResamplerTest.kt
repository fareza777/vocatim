package com.vocatim.app.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StreamingResamplerTest {

    @Test
    fun `passthrough at 16kHz`() {
        val resampler = StreamingResampler(16_000)
        val input = FloatArray(1_000) { it / 1000f }
        val out = resampler.process(input)
        assertEquals(1_000, out.size)
        assertEquals(input[500], out[500], 1e-6f)
    }

    @Test
    fun `downsamples 48kHz to a third of the samples`() {
        val resampler = StreamingResampler(48_000)
        var total = 0
        repeat(10) { total += resampler.process(FloatArray(4_800)).size }
        // 48000 input samples -> ~16000 output samples.
        assertTrue("got $total", abs(total - 16_000) <= 2)
    }

    @Test
    fun `block size does not change the output`() {
        val signal = FloatArray(44_100) { i ->
            kotlin.math.sin(2 * Math.PI * 440 * i / 44_100).toFloat()
        }

        val oneShot = StreamingResampler(44_100).process(signal)

        val chunked = StreamingResampler(44_100)
        val pieces = mutableListOf<Float>()
        var offset = 0
        val blockSizes = listOf(1_000, 3_777, 12_345, 999, 26_979)
        for (size in blockSizes) {
            val end = minOf(offset + size, signal.size)
            pieces.addAll(chunked.process(signal.copyOfRange(offset, end)).toList())
            offset = end
        }

        assertEquals(oneShot.size, pieces.size)
        for (i in oneShot.indices) {
            assertEquals("sample $i", oneShot[i], pieces[i], 1e-5f)
        }
    }
}
