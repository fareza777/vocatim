package com.vocatim.app.data.export

import com.vocatim.app.data.db.SegmentEntity
import java.util.Locale

object SrtFormatter {
    /**
     * SubRip format: 1-based index, "HH:MM:SS,mmm --> HH:MM:SS,mmm",
     * text, blank line.
     */
    fun format(segments: List<SegmentEntity>): String = buildString {
        segments.forEachIndexed { i, segment ->
            append(i + 1).append('\n')
            append(timestamp(segment.startMs))
            append(" --> ")
            append(timestamp(segment.endMs)).append('\n')
            append(segment.text.trim()).append('\n')
            append('\n')
        }
    }.trimEnd('\n') + "\n"

    private fun timestamp(ms: Long): String {
        val h = ms / 3_600_000
        val m = ms % 3_600_000 / 60_000
        val s = ms % 60_000 / 1_000
        val millis = ms % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, millis)
    }
}
