package com.vocatim.app.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStorageTest {

    @Test
    fun `one minute of headroom needs 1_92 MB`() {
        assertEquals(1_920_000L, RecordingStorage.BYTES_PER_MINUTE)
    }

    @Test
    fun `free bytes convert to whole minutes, rounding down`() {
        assertEquals(0, RecordingStorage.minutesLeft(1_919_999L))
        assertEquals(1, RecordingStorage.minutesLeft(1_920_000L))
        assertEquals(60, RecordingStorage.minutesLeft(115_200_000L))
    }

    @Test
    fun `an empty disk is zero minutes, never negative`() {
        assertEquals(0, RecordingStorage.minutesLeft(0L))
        assertEquals(0, RecordingStorage.minutesLeft(-1L))
    }

    @Test
    fun `the warning threshold sits below the refusal threshold`() {
        assertTrue(RecordingStorage.LOW_MINUTES < RecordingStorage.MIN_START_MINUTES)
    }
}
