package com.vocatim.app.data.audio

import java.io.File

/**
 * How much longer a recording can run on the space that is left.
 *
 * Recording writes 16 kHz mono PCM16 — a fixed 1.92 MB per minute — so free
 * bytes convert straight into minutes of headroom. Nothing checked this
 * before: when storage filled mid-recording the write simply failed and the
 * recording was lost without a word.
 */
object RecordingStorage {

    /** 16 000 samples/s x 2 bytes x 60 s. */
    const val BYTES_PER_MINUTE = 16_000L * 2 * 60

    /** Refuse to start a recording with less headroom than this. */
    const val MIN_START_MINUTES = 30

    /** Warn from this much headroom down. */
    const val LOW_MINUTES = 10

    fun minutesLeft(freeBytes: Long): Int =
        (freeBytes.coerceAtLeast(0L) / BYTES_PER_MINUTE).toInt()

    fun minutesLeft(dir: File): Int = minutesLeft(dir.usableSpace)
}
