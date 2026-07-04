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
        /** Peak amplitude of the latest buffer, 0..1. */
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

    internal fun set(state: RecordingState) {
        _state.value = state
    }

    internal fun emitFinished(transcriptId: Long) {
        _finished.tryEmit(transcriptId)
    }
}
