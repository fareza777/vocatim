package com.vocatim.whisper

/** One decoded segment. Timestamps are in milliseconds relative to the audio start. */
data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
