package com.vocatim.app.data.export

import com.vocatim.app.data.db.SegmentEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class VttFormatterTest {
    @Test
    fun `starts with WEBVTT header`() {
        val segments = listOf(
            SegmentEntity(transcriptId = 1, startMs = 0, endMs = 2_500, text = " Hello"),
        )
        val out = VttFormatter.format(segments)
        assertTrue(out.startsWith("WEBVTT"))
        assertTrue(out.contains("00:00:00.000 --> 00:00:02.500"))
    }
}
