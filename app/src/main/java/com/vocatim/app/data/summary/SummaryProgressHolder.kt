package com.vocatim.app.data.summary

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory progress of running summary jobs, keyed by transcript id. */
class SummaryProgressHolder {
    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val progress: StateFlow<Map<Long, Float>> = _progress.asStateFlow()

    fun set(transcriptId: Long, fraction: Float) {
        _progress.update { it + (transcriptId to fraction) }
    }

    fun remove(transcriptId: Long) {
        _progress.update { it - transcriptId }
    }
}
