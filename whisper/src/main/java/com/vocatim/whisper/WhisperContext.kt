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
        initialPrompt: String? = null,
        beamSize: Int = 0,
        /** Path to a Silero VAD ggml model; enables voice-activity filtering. */
        vadModelPath: String? = null,
    ): WhisperResult = withContext(dispatcher) {
        if (ptr == 0L) throw WhisperException("Whisper context already released")
        val result = WhisperLib.fullTranscribe(
            ptr, numThreads, language, translate, audioData, initialPrompt, beamSize,
            vadModelPath,
        )
        if (result != 0) throw WhisperException("whisper_full failed with code $result")

        val count = WhisperLib.getTextSegmentCount(ptr)
        val segments = (0 until count).map { i ->
            WhisperSegment(
                // Native timestamps are in units of 10 ms.
                startMs = WhisperLib.getTextSegmentT0(ptr, i) * 10,
                endMs = WhisperLib.getTextSegmentT1(ptr, i) * 10,
                text = WhisperLib.getTextSegment(ptr, i),
                words = extractWords(i),
            )
        }
        WhisperResult(segments, WhisperLib.getDetectedLanguage(ptr))
    }

    /**
     * Merges whisper's subword tokens into whole words with time ranges.
     * Tokens starting with a space begin a new word; bracketed specials
     * like "[_BEG_]" are metadata, not speech.
     */
    private fun extractWords(segment: Int): List<WhisperWord> {
        val tokenCount = WhisperLib.getTokenCount(ptr, segment)
        if (tokenCount <= 0) return emptyList()
        val words = mutableListOf<WhisperWord>()
        var text = StringBuilder()
        var start = 0L
        var end = 0L
        for (t in 0 until tokenCount) {
            val tokenText = WhisperLib.getTokenText(ptr, segment, t)
            if (tokenText.startsWith("[_") || tokenText.startsWith("<|")) continue
            val t0 = WhisperLib.getTokenT0(ptr, segment, t) * 10
            val t1 = WhisperLib.getTokenT1(ptr, segment, t) * 10
            if (tokenText.startsWith(" ") && text.isNotEmpty()) {
                words.add(WhisperWord(start, end, text.toString()))
                text = StringBuilder()
            }
            if (text.isEmpty()) start = t0
            text.append(tokenText.trimStart())
            end = t1
        }
        if (text.isNotEmpty()) words.add(WhisperWord(start, end, text.toString()))
        return words
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
