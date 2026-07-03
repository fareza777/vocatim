package com.vocatim.app.data.transcribe

import android.os.SystemClock
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.model.WhisperModel
import com.vocatim.whisper.WhisperContext
import com.vocatim.whisper.WhisperSegment
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileNotFoundException

data class TranscriptionResult(
    val segments: List<WhisperSegment>,
    val processingTimeMs: Long,
) {
    val text: String get() = segments.joinToString("") { it.text }.trim()
}

/**
 * Owns the native Whisper context. Loading a model takes seconds and hundreds
 * of MB of RAM, so a single context is kept and only swapped when the
 * requested model changes.
 */
class WhisperTranscriber(
    private val modelManager: ModelManager,
) {
    private val mutex = Mutex()
    private var loadedModel: WhisperModel? = null
    private var context: WhisperContext? = null

    suspend fun transcribe(
        model: WhisperModel,
        samples: FloatArray,
        language: String,
        translate: Boolean = false,
    ): TranscriptionResult = mutex.withLock {
        val ctx = loadContextLocked(model)
        val startedAt = SystemClock.elapsedRealtime()
        val segments = ctx.transcribe(samples, language, translate)
        TranscriptionResult(
            segments = segments,
            processingTimeMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    suspend fun release() = mutex.withLock {
        context?.release()
        context = null
        loadedModel = null
    }

    private suspend fun loadContextLocked(model: WhisperModel): WhisperContext {
        context?.let { existing ->
            if (loadedModel == model) return existing
            existing.release()
            context = null
            loadedModel = null
        }

        val file = modelManager.modelFile(model)
        if (!file.exists()) {
            throw FileNotFoundException("Model ${model.id} is not downloaded")
        }
        val created = WhisperContext.createFromFile(file.absolutePath)
        context = created
        loadedModel = model
        return created
    }
}
