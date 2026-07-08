package com.vocatim.whisper

/** One word with its spoken time range, milliseconds from the audio start. */
data class WhisperWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** One decoded segment. Timestamps are in milliseconds relative to the audio start. */
data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    /** Word-level timings for playback highlighting; may be empty. */
    val words: List<WhisperWord> = emptyList(),
)
