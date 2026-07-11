package com.vocatim.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Active(
        val paused: Boolean,
        val elapsedMs: Long,
        /** RMS level of the latest buffer, 0..1 (speech sits around 0.02-0.2). */
        val amplitude: Float,
    ) : RecordingState
    data class Error(val message: String) : RecordingState
}

/** Bridge between [RecordingService] and the UI. */
class RecordingStateHolder {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Emits the new transcript id when a recording is finished and queued. */
    private val _finished = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val finished: SharedFlow<Long> = _finished.asSharedFlow()

    /** Rolling tail of the live recording, for the on-screen text preview. */
    val previewBuffer = PreviewBuffer()

    /** When true, the service mirrors each captured buffer to [liveAudio]
     *  for the streaming caption engine (Live recording mode). */
    @Volatile var liveTapEnabled = false

    private val _liveAudio = kotlinx.coroutines.flow.MutableSharedFlow<FloatArray>(
        extraBufferCapacity = 32,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val liveAudio: SharedFlow<FloatArray> = _liveAudio.asSharedFlow()

    internal fun emitLiveAudio(samples: ShortArray, count: Int) {
        if (!liveTapEnabled) return
        val floats = FloatArray(count) { samples[it] / 32768f }
        _liveAudio.tryEmit(floats)
    }

    internal fun set(state: RecordingState) {
        _state.value = state
    }

    internal fun emitFinished(transcriptId: Long) {
        _finished.tryEmit(transcriptId)
    }
}

/**
 * Fixed-size circular buffer holding the most recent ~15s of 16kHz mono
 * samples (<0.5 MB). The recorder thread writes; the preview reader takes
 * ordered float snapshots.
 */
class PreviewBuffer(private val capacity: Int = 15 * 16_000) {
    private val buffer = ShortArray(capacity)
    private var writePos = 0
    private var filled = 0

    @Synchronized
    fun write(samples: ShortArray, count: Int) {
        val n = count.coerceAtMost(capacity)
        // If one write exceeds capacity (never in practice), keep the tail.
        val from = count - n
        for (i in 0 until n) {
            buffer[writePos] = samples[from + i]
            writePos = (writePos + 1) % capacity
        }
        filled = (filled + n).coerceAtMost(capacity)
    }

    /** Samples in chronological order, normalized to -1..1. */
    @Synchronized
    fun snapshot(): FloatArray {
        val start = if (filled < capacity) 0 else writePos
        return FloatArray(filled) { i ->
            buffer[(start + i) % capacity] / 32768f
        }
    }

    @Synchronized
    fun clear() {
        writePos = 0
        filled = 0
    }
}
