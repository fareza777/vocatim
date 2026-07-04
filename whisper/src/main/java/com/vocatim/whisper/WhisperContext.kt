package com.vocatim.whisper

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class WhisperException(message: String) : Exception(message)

data class WhisperResult(
    val segments: List<WhisperSegment>,
    /** ISO 639-1 code Whisper detected/used, or null when unavailable. */
    val detectedLanguage: String?,
)

/**
 * Safe Kotlin wrapper around a native whisper_context.
 *
 * whisper.cpp contexts must not be accessed from more than one thread at a
 * time, so every call is confined to a dedicated single-thread dispatcher.
 */
class WhisperContext private constructor(private var ptr: Long) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /**
     * Transcribes 16kHz mono PCM float samples.
     *
     * @param language ISO 639-1 code (e.g. "id", "en") or "auto" for detection.
     * @param translate when true, output is translated to English (Whisper built-in).
     */
    suspend fun transcribe(
        audioData: FloatArray,
        language: String = "auto",
        translate: Boolean = false,
        numThreads: Int = WhisperCpuConfig.preferredThreadCount,
    ): WhisperResult = withContext(dispatcher) {
        if (ptr == 0L) throw WhisperException("Whisper context already released")
        val result = WhisperLib.fullTranscribe(ptr, numThreads, language, translate, audioData)
        if (result != 0) throw WhisperException("whisper_full failed with code $result")

        val count = WhisperLib.getTextSegmentCount(ptr)
        val segments = (0 until count).map { i ->
            WhisperSegment(
                // Native timestamps are in units of 10 ms.
                startMs = WhisperLib.getTextSegmentT0(ptr, i) * 10,
                endMs = WhisperLib.getTextSegmentT1(ptr, i) * 10,
                text = WhisperLib.getTextSegment(ptr, i),
            )
        }
        WhisperResult(segments, WhisperLib.getDetectedLanguage(ptr))
    }

    suspend fun release() = withContext(dispatcher) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0
        }
        // Running task is allowed to finish; the executor stops accepting new work.
        dispatcher.close()
    }

    companion object {
        fun createFromFile(filePath: String): WhisperContext {
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) {
                throw WhisperException("Couldn't create whisper context from $filePath")
            }
            return WhisperContext(ptr)
        }

        fun getSystemInfo(): String = WhisperLib.getSystemInfo()
    }
}
