package com.vocatim.app.data.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceAlignerTest {

    private val rate = 16_000

    /** Loud everywhere except a silent pocket at [silentAtSec]. */
    private fun audioWithSilence(totalSec: Int, silentAtSec: Double): (Long, Int) -> FloatArray {
        val silentStart = (silentAtSec * rate).toLong()
        val silentEnd = silentStart + rate / 2 // 500ms of silence
        return { start, count ->
            FloatArray(count) { i ->
                val pos = start + i
                if (pos in silentStart until silentEnd) 0f else 0.5f
            }
        }
    }

    @Test
    fun `single chunk passes through untouched`() {
        val chunks = ChunkPlanner.plan(10L * rate)
        val aligned = SilenceAligner.align(chunks, 10L * rate) { _, c -> FloatArray(c) }
        assertEquals(chunks, aligned)
    }

    @Test
    fun `boundary moves into the silent pocket`() {
        val totalSec = 60
        val total = totalSec.toLong() * rate
        val base = ChunkPlanner.plan(total)
        // First base cut is at 29s (stride 28 + overlap/2); silence at 30s.
        val aligned = SilenceAligner.align(base, total, audioWithSilence(totalSec, 30.0))

        val cutMs = aligned[1].acceptFromMs
        assertTrue("cut $cutMs should sit inside the 30.0-30.5s silence", cutMs in 30_000..30_500)
        // Chunks stay contiguous and the last one owns the tail.
        assertEquals(cutMs, aligned[0].acceptToMs)
        assertEquals(Long.MAX_VALUE, aligned.last().acceptToMs)
    }

    @Test
    fun `uniform audio keeps cuts where they were planned`() {
        val total = 60L * rate
        val base = ChunkPlanner.plan(total)
        val aligned = SilenceAligner.align(base, total) { _, c -> FloatArray(c) { 0.5f } }
        // No silence anywhere: every tie resolves to the original position
        // (within one 100ms frame).
        base.zip(aligned).drop(1).forEach { (original, adjusted) ->
            val drift = kotlin.math.abs(original.acceptFromMs - adjusted.acceptFromMs)
            assertTrue("boundary drifted ${drift}ms on uniform audio", drift <= 100)
        }
    }

    @Test
    fun `alignment is deterministic`() {
        val total = 90L * rate
        val base = ChunkPlanner.plan(total)
        val audio = audioWithSilence(90, 29.5)
        val first = SilenceAligner.align(base, total, audio)
        val second = SilenceAligner.align(base, total, audio)
        assertEquals(first, second)
    }

    @Test
    fun `chunks never exceed the audio bounds`() {
        val total = 125L * rate
        val aligned = SilenceAligner.align(ChunkPlanner.plan(total), total) { _, c ->
            FloatArray(c) { 0.3f }
        }
        aligned.forEach { chunk ->
            assertTrue(chunk.startSample >= 0)
            assertTrue(chunk.endSample <= total)
            assertTrue(chunk.startSample < chunk.endSample)
        }
    }
}
