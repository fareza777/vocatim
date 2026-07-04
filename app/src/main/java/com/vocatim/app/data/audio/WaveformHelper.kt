package com.vocatim.app.data.audio

import java.io.File
import java.util.LinkedHashMap

/** Downsamples audio peaks for waveform UI; results are cached by file path. */
object WaveformHelper {
    private const val MAX_CACHE = 48
    private val cache = object : LinkedHashMap<String, FloatArray>(MAX_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) =
            size > MAX_CACHE
    }

    fun peaks(file: File, buckets: Int = 32): FloatArray? = runCatching {
        val key = "${file.absolutePath}:${file.length()}"
        cache[key] ?: compute(file, buckets).also { cache[key] = it }
    }.getOrNull()

    fun compute(file: File, buckets: Int = 32): FloatArray {
        WavStreamReader(file).use { reader ->
            val total = reader.totalSamples
            if (total <= 0) return FloatArray(buckets)
            val bucketSize = total / buckets
            val probe = minOf(bucketSize, 2_048L).toInt().coerceAtLeast(1)
            return FloatArray(buckets) { i ->
                val samples = reader.read(i * bucketSize, probe)
                var peak = 0f
                for (s in samples) {
                    val a = if (s < 0) -s else s
                    if (a > peak) peak = a
                }
                peak
            }
        }
    }
}
