package com.vocatim.app.data.transcribe

import com.vocatim.app.data.audio.WavDecoder

/**
 * A chunk of audio to transcribe, in samples, and the window (in ms,
 * relative to the whole audio) whose segments this chunk is responsible for.
 *
 * Chunks overlap so words cut at a boundary appear whole in at least one
 * chunk; [acceptFromMs]/[acceptToMs] split the overlap halfway so every
 * segment is claimed by exactly one chunk.
 */
data class Chunk(
    val index: Int,
    val startSample: Long,
    val endSample: Long,
    val acceptFromMs: Long,
    val acceptToMs: Long,
) {
    val startMs: Long get() = startSample * 1000 / WavDecoder.WHISPER_SAMPLE_RATE
    val sampleCount: Int get() = (endSample - startSample).toInt()
}

object ChunkPlanner {
    const val CHUNK_SECONDS = 30
    const val OVERLAP_SECONDS = 2

    private const val RATE = WavDecoder.WHISPER_SAMPLE_RATE
    private const val CHUNK_SAMPLES = CHUNK_SECONDS.toLong() * RATE
    private const val OVERLAP_SAMPLES = OVERLAP_SECONDS.toLong() * RATE
    private const val STRIDE_SAMPLES = CHUNK_SAMPLES - OVERLAP_SAMPLES

    fun plan(totalSamples: Long): List<Chunk> {
        if (totalSamples <= 0) return emptyList()
        if (totalSamples <= CHUNK_SAMPLES) {
            return listOf(
                Chunk(0, 0, totalSamples, acceptFromMs = 0, acceptToMs = Long.MAX_VALUE)
            )
        }

        val chunks = mutableListOf<Chunk>()
        var start = 0L
        var index = 0
        while (start < totalSamples) {
            val end = minOf(start + CHUNK_SAMPLES, totalSamples)
            val isFirst = index == 0
            val isLast = end == totalSamples
            chunks.add(
                Chunk(
                    index = index,
                    startSample = start,
                    endSample = end,
                    acceptFromMs = if (isFirst) 0 else (start + OVERLAP_SAMPLES / 2) * 1000 / RATE,
                    acceptToMs = if (isLast) Long.MAX_VALUE else (end - OVERLAP_SAMPLES / 2) * 1000 / RATE,
                )
            )
            if (isLast) break
            start += STRIDE_SAMPLES
            index++
        }
        return chunks
    }
}
