package com.vocatim.app.data.transcribe

import com.vocatim.app.data.audio.WavDecoder

/**
 * Nudges chunk boundaries into nearby silence so whisper never sees a word
 * cut in half at a chunk edge. Deterministic for a given file (the same cuts
 * are found again on resume), and cheap: it reads only a few seconds of
 * audio around each boundary.
 */
object SilenceAligner {
    private const val RATE = WavDecoder.WHISPER_SAMPLE_RATE

    /** Half-width of the search window around each planned cut. */
    private const val WINDOW_SAMPLES = (2.4 * RATE).toInt()

    /** RMS is evaluated per 100ms frame. */
    private const val FRAME_SAMPLES = RATE / 10

    /**
     * @param readSamples reads [count] samples at an absolute position.
     * @return chunks rebuilt around silence-aligned cut points; accept
     *  windows follow the new cuts so every segment still has one owner.
     */
    fun align(
        chunks: List<Chunk>,
        totalSamples: Long,
        readSamples: (startSample: Long, count: Int) -> FloatArray,
    ): List<Chunk> {
        if (chunks.size <= 1) return chunks

        // Original interior boundaries sit at each chunk's accept-window
        // start (the midpoint of the overlap).
        val cuts = chunks.drop(1).map { chunk ->
            val planned = chunk.acceptFromMs * RATE / 1000
            quietestNear(planned, totalSamples, readSamples)
        }

        val overlapHalf = ChunkPlanner.OVERLAP_SECONDS.toLong() * RATE / 2
        val boundaries = listOf(0L) + cuts + listOf(totalSamples)
        return List(chunks.size) { i ->
            val from = boundaries[i]
            val to = boundaries[i + 1]
            Chunk(
                index = i,
                startSample = (from - overlapHalf).coerceAtLeast(0),
                endSample = (to + overlapHalf).coerceAtMost(totalSamples),
                acceptFromMs = if (i == 0) 0 else from * 1000 / RATE,
                acceptToMs = if (i == chunks.size - 1) Long.MAX_VALUE else to * 1000 / RATE,
            )
        }
    }

    /** Center of the quietest 100ms frame within the window around [cut]. */
    private fun quietestNear(
        cut: Long,
        totalSamples: Long,
        readSamples: (Long, Int) -> FloatArray,
    ): Long {
        val from = (cut - WINDOW_SAMPLES).coerceAtLeast(0)
        val to = (cut + WINDOW_SAMPLES).coerceAtMost(totalSamples)
        val window = readSamples(from, (to - from).toInt())
        if (window.size < FRAME_SAMPLES) return cut

        var bestStart = 0
        var bestEnergy = Double.MAX_VALUE
        var bestDistance = Long.MAX_VALUE
        var offset = 0
        while (offset + FRAME_SAMPLES <= window.size) {
            var sum = 0.0
            for (i in offset until offset + FRAME_SAMPLES) {
                sum += window[i] * window[i]
            }
            // Ties (e.g. uniformly loud speech with no pause) keep the cut
            // where it was planned instead of drifting to the window edge.
            val distance = kotlin.math.abs(from + offset + FRAME_SAMPLES / 2 - cut)
            val quieter = sum < bestEnergy - ENERGY_EPSILON
            val tiedButCloser = sum < bestEnergy + ENERGY_EPSILON && distance < bestDistance
            if (quieter || tiedButCloser) {
                bestEnergy = sum
                bestDistance = distance
                bestStart = offset
            }
            offset += FRAME_SAMPLES
        }
        return from + bestStart + FRAME_SAMPLES / 2
    }

    private const val ENERGY_EPSILON = 1e-9
}
