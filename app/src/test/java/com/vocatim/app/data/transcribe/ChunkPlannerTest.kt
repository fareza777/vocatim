package com.vocatim.app.data.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkPlannerTest {

    private val rate = 16_000L

    @Test
    fun `empty audio yields no chunks`() {
        assertEquals(emptyList<Chunk>(), ChunkPlanner.plan(0))
    }

    @Test
    fun `short audio is a single chunk accepting everything`() {
        val chunks = ChunkPlanner.plan(10 * rate)

        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].startSample)
        assertEquals(10 * rate, chunks[0].endSample)
        assertEquals(0, chunks[0].acceptFromMs)
        assertEquals(Long.MAX_VALUE, chunks[0].acceptToMs)
    }

    @Test
    fun `exactly 30s is a single chunk`() {
        assertEquals(1, ChunkPlanner.plan(30 * rate).size)
    }

    @Test
    fun `chunks overlap by the configured amount`() {
        val chunks = ChunkPlanner.plan(90 * rate)

        assertTrue(chunks.size >= 3)
        for (i in 1 until chunks.size) {
            val overlap = chunks[i - 1].endSample - chunks[i].startSample
            assertEquals(ChunkPlanner.OVERLAP_SECONDS * rate, overlap)
        }
    }

    @Test
    fun `chunks cover all samples with no gaps`() {
        val total = 3600 * rate // 1 hour
        val chunks = ChunkPlanner.plan(total)

        assertEquals(0, chunks.first().startSample)
        assertEquals(total, chunks.last().endSample)
        for (i in 1 until chunks.size) {
            assertTrue(chunks[i].startSample < chunks[i - 1].endSample)
        }
    }

    @Test
    fun `accept windows partition the timeline without gaps or overlap`() {
        val chunks = ChunkPlanner.plan(100 * rate)

        assertEquals(0, chunks.first().acceptFromMs)
        assertEquals(Long.MAX_VALUE, chunks.last().acceptToMs)
        for (i in 1 until chunks.size) {
            assertEquals(chunks[i - 1].acceptToMs, chunks[i].acceptFromMs)
        }
    }

    @Test
    fun `last partial chunk is not lost`() {
        // 75s = 30 + 28 + 17: last chunk is partial.
        val chunks = ChunkPlanner.plan(75 * rate)
        assertEquals(75 * rate, chunks.last().endSample)
    }
}
