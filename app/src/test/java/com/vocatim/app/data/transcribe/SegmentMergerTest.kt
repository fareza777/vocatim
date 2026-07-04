package com.vocatim.app.data.transcribe

import com.vocatim.whisper.WhisperSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentMergerTest {

    @Test
    fun `shifts segment timestamps by chunk offset`() {
        val chunk = chunkAt(startSample = 448_000, acceptFromMs = 29_000, acceptToMs = 57_000)
        // 448000 samples = 28s.
        val segments = listOf(WhisperSegment(startMs = 2_000, endMs = 4_000, text = " halo"))

        val accepted = SegmentMerger.acceptSegments(chunk, segments)

        assertEquals(1, accepted.size)
        assertEquals(30_000, accepted[0].startMs)
        assertEquals(32_000, accepted[0].endMs)
    }

    @Test
    fun `drops segments outside the accept window`() {
        val chunk = chunkAt(startSample = 448_000, acceptFromMs = 29_000, acceptToMs = 57_000)
        val segments = listOf(
            // Midpoint at 28.5s absolute — belongs to the previous chunk.
            WhisperSegment(startMs = 0, endMs = 1_000, text = " duplikat"),
            // Midpoint at 31s absolute — belongs here.
            WhisperSegment(startMs = 2_000, endMs = 4_000, text = " milik chunk ini"),
            // Midpoint at 57.5s absolute — belongs to the next chunk.
            WhisperSegment(startMs = 29_000, endMs = 30_000, text = " berikutnya"),
        )

        val accepted = SegmentMerger.acceptSegments(chunk, segments)

        assertEquals(listOf(" milik chunk ini"), accepted.map { it.text })
    }

    @Test
    fun `drops blank segments`() {
        val chunk = chunkAt(startSample = 0, acceptFromMs = 0, acceptToMs = Long.MAX_VALUE)
        val segments = listOf(
            WhisperSegment(0, 1_000, "  "),
            WhisperSegment(1_000, 2_000, " teks"),
        )

        assertEquals(1, SegmentMerger.acceptSegments(chunk, segments).size)
    }

    @Test
    fun `mergeText joins and trims`() {
        val segments = listOf(
            WhisperSegment(0, 1_000, " Halo"),
            WhisperSegment(1_000, 2_000, " dunia."),
        )
        assertEquals("Halo dunia.", SegmentMerger.mergeText(segments))
    }

    private fun chunkAt(startSample: Long, acceptFromMs: Long, acceptToMs: Long) = Chunk(
        index = 1,
        startSample = startSample,
        endSample = startSample + 480_000,
        acceptFromMs = acceptFromMs,
        acceptToMs = acceptToMs,
    )
}
