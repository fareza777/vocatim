package com.vocatim.app.data.transcribe

import com.vocatim.whisper.WhisperSegment

object SegmentMerger {
    /**
     * Shifts a chunk's segments to absolute time and keeps only those this
     * chunk is responsible for (midpoint inside the accept window).
     * Adjacent chunks see the same overlap audio, so without this filter
     * segments near boundaries would be duplicated.
     */
    fun acceptSegments(chunk: Chunk, segments: List<WhisperSegment>): List<WhisperSegment> =
        segments.asSequence()
            .map { s ->
                WhisperSegment(
                    startMs = s.startMs + chunk.startMs,
                    endMs = s.endMs + chunk.startMs,
                    text = s.text,
                )
            }
            .filter { s ->
                val midpoint = (s.startMs + s.endMs) / 2
                midpoint >= chunk.acceptFromMs && midpoint < chunk.acceptToMs
            }
            .filter { it.text.isNotBlank() }
            .toList()

    fun mergeText(segments: List<WhisperSegment>): String =
        segments.joinToString("") { it.text }.trim()
}
