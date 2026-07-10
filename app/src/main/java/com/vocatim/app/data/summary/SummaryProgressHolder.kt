package com.vocatim.app.data.summary

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory progress of running summary jobs, keyed by transcript id. */
class SummaryProgressHolder {
    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val progress: StateFlow<Map<Long, Float>> = _progress.asStateFlow()

    /** Live partial text of the summary being generated, for streaming UI. */
    private val _partialText = MutableStateFlow<Map<Long, String>>(emptyMap())
    val partialText: StateFlow<Map<Long, String>> = _partialText.asStateFlow()

    fun set(transcriptId: Long, fraction: Float) {
        _progress.update { it + (transcriptId to fraction) }
    }

    fun setPartial(transcriptId: Long, text: String) {
        _partialText.update { it + (transcriptId to text) }
    }

    fun remove(transcriptId: Long) {
        _progress.update { it - transcriptId }
        _partialText.update { it - transcriptId }
    }
}
