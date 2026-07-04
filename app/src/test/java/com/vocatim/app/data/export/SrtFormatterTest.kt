package com.vocatim.app.data.export

import com.vocatim.app.data.db.SegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SrtFormatterTest {

    @Test
    fun `formats segments as subrip`() {
        val segments = listOf(
            SegmentEntity(transcriptId = 1, startMs = 0, endMs = 2_500, text = " Halo dunia."),
            SegmentEntity(transcriptId = 1, startMs = 3_600_123, endMs = 3_661_999, text = "Second line"),
        )

        val srt = SrtFormatter.format(segments)

        val expected = """
            |1
            |00:00:00,000 --> 00:00:02,500
            |Halo dunia.
            |
            |2
            |01:00:00,123 --> 01:01:01,999
            |Second line
            |
        """.trimMargin()
        assertEquals(expected, srt)
    }

    @Test
    fun `empty list yields empty output`() {
        assertEquals("\n", SrtFormatter.format(emptyList()))
    }
}
