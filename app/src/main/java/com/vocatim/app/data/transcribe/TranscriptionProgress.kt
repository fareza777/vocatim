package com.vocatim.app.data.transcribe

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TranscriptionProgress(
    val transcriptId: Long,
    /** True while converting an imported file, before transcription. */
    val converting: Boolean = false,
    /** 0..1 within the current phase. */
    val fraction: Float = 0f,
    val chunksDone: Int = 0,
    val totalChunks: Int = 0,
    /** Estimated remaining transcription time; null until known. */
    val etaMs: Long? = null,
)

/** In-memory progress of running transcriptions, keyed by transcript id. */
class TranscriptionProgressHolder {
    private val _progress = MutableStateFlow<Map<Long, TranscriptionProgress>>(emptyMap())
    val progress: StateFlow<Map<Long, TranscriptionProgress>> = _progress.asStateFlow()

    fun update(p: TranscriptionProgress) {
        _progress.update { it + (p.transcriptId to p) }
    }

    fun remove(transcriptId: Long) {
        _progress.update { it - transcriptId }
    }
}
