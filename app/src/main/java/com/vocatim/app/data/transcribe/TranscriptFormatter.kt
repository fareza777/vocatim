package com.vocatim.app.data.transcribe

import com.vocatim.app.data.db.SegmentEntity

/**
 * Turns a flat list of Whisper segments into readable prose by inserting
 * paragraph breaks where the speaker paused. Whisper output is otherwise one
 * unbroken wall of text, which is hard to read in long meetings and minutes.
 */
object TranscriptFormatter {
    /** A pause this long (ms) starts a new paragraph. */
    private const val PARAGRAPH_GAP_MS = 1200L
    /** Above this length, a shorter pause is enough to break — avoids walls. */
    private const val SOFT_MAX_CHARS = 600
    private const val SOFT_GAP_MS = 400L

    fun paragraphed(segments: List<SegmentEntity>): String {
        if (segments.isEmpty()) return ""
        val sb = StringBuilder()
        var paragraphLen = 0
        var prevEnd = segments.first().startMs
        segments.forEachIndexed { i, seg ->
            val gap = seg.startMs - prevEnd
            val breakHere = i > 0 && (
                gap >= PARAGRAPH_GAP_MS ||
                    (paragraphLen > SOFT_MAX_CHARS && gap >= SOFT_GAP_MS)
                )
            if (breakHere) {
                while (sb.isNotEmpty() && sb.last() == ' ') sb.deleteCharAt(sb.length - 1)
                sb.append("\n\n")
                paragraphLen = 0
            }
            sb.append(seg.text)
            paragraphLen += seg.text.length
            prevEnd = seg.endMs
        }
        return sb.toString().trim()
    }
}
