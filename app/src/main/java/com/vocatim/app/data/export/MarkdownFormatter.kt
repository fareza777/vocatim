package com.vocatim.app.data.export

import com.vocatim.app.data.db.SegmentEntity
import java.util.Locale

object MarkdownFormatter {
    fun formatPlain(title: String, text: String): String =
        "# $title\n\n$text\n"

    fun formatWithTimestamps(title: String, segments: List<SegmentEntity>): String = buildString {
        append("# ").append(title).append("\n\n")
        segments.forEach { segment ->
            append("**")
            append(clock(segment.startMs))
            append("** ")
            append(segment.text.trim()).append("\n\n")
        }
    }

    private fun clock(ms: Long): String {
        val m = ms / 60_000
        val s = ms % 60_000 / 1_000
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
