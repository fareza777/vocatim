package com.vocatim.app.data.audio

/**
 * Stateful linear resampler for streaming use: feed arbitrary-sized blocks,
 * get 16kHz output. Keeps the last input sample across calls so block
 * boundaries interpolate correctly.
 */
class StreamingResampler(
    private val inputRate: Int,
    private val outputRate: Int = WavDecoder.WHISPER_SAMPLE_RATE,
) {
    init {
        require(inputRate >= 8_000) { "Unsupported input rate $inputRate" }
    }

    private val step = inputRate.toDouble() / outputRate
    /** Position of the next output sample, in input-sample units, relative to
     * the first sample of history (previous last sample + current block). */
    private var srcPos = 0.0
    private var lastSample = 0f
    private var hasLast = false

    fun process(input: FloatArray): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        if (inputRate == outputRate && !hasLast) return input.copyOf()

        // History = [lastSample] + input; index 0 is lastSample when present.
        val historyOffset = if (hasLast) 1 else 0
        val totalLength = input.size + historyOffset
        val out = ArrayList<Float>((input.size / step).toInt() + 2)

        while (srcPos <= totalLength - 1) {
            val idx = srcPos.toInt()
            val frac = (srcPos - idx).toFloat()
            val a = sampleAt(idx, input, historyOffset)
            val b = sampleAt(minOf(idx + 1, totalLength - 1), input, historyOffset)
            out.add(a + (b - a) * frac)
            srcPos += step
        }

        // Rebase position relative to the next block's history.
        srcPos -= (totalLength - 1)
        lastSample = input[input.size - 1]
        hasLast = true
        return out.toFloatArray()
    }

    private fun sampleAt(index: Int, input: FloatArray, historyOffset: Int): Float =
        if (historyOffset == 1 && index == 0) lastSample else input[index - historyOffset]
}
