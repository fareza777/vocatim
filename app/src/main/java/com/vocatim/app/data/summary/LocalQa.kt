package com.vocatim.app.data.summary

import com.vocatim.llm.LlamaSummarizer
import kotlinx.coroutines.sync.withLock

/**
 * Grounded question-answering over one transcript using the on-device model.
 * One-shot: loads the engine, answers, releases — Ask AI is occasional, and
 * keeping 1-2.5 GB of weights resident between questions isn't worth it.
 */
class LocalQa(
    private val modelManager: SummaryModelManager,
    private val threads: Int,
    private val model: SummaryModel,
    private val ctxCapTokens: Int,
) {
    suspend fun answer(transcript: String, question: String, language: String): String {
        val modelPath = modelManager.modelFile(model)
        if (!modelPath.exists()) throw SummaryException("MODEL_MISSING")

        val cap = minOf(model.maxContextTokens, ctxCapTokens)
        // Clip the transcript to what fits alongside the answer budget.
        val maxTranscriptChars = (cap - ANSWER_TOKENS - OVERHEAD_TOKENS) * CHARS_PER_TOKEN
        val clipped = transcript.take(maxTranscriptChars)
        val needed = clipped.length / CHARS_PER_TOKEN + ANSWER_TOKENS + OVERHEAD_TOKENS
        val nCtx = (((needed + 1_023) / 1_024) * 1_024).coerceIn(4_096, cap)

        val system = systemPrompt(language) +
            if (model.hybridThinking) " /no_think" else ""
        val indonesian = language == "id"
        val user = if (indonesian) {
            "Transkrip:\n\n$clipped\n\nPertanyaan: $question"
        } else {
            "Transcript:\n\n$clipped\n\nQuestion: $question"
        }

        LlmLock.mutex.withLock {
            val engine = LlamaSummarizer.create(threads)
            try {
                engine.loadIfNeeded(modelPath.absolutePath, nCtx)
                return engine.chat(
                    system = system,
                    user = user,
                    maxTokens = ANSWER_TOKENS,
                    format = model.promptFormat,
                    temperature = model.temperature,
                    repeatPenalty = model.repeatPenalty,
                )
            } finally {
                engine.release()
            }
        }
    }

    private fun systemPrompt(language: String): String = if (language == "id") {
        "Anda menjawab pertanyaan tentang sebuah transkrip. Jawab singkat dan " +
            "akurat dalam bahasa Indonesia, HANYA berdasarkan isi transkrip. " +
            "Bila jawabannya tidak ada di transkrip, katakan begitu."
    } else {
        "You answer questions about a transcript. Answer briefly and accurately, " +
            "based ONLY on the transcript content, in the same language as the " +
            "question. If the transcript doesn't contain the answer, say so."
    }

    private companion object {
        const val CHARS_PER_TOKEN = 3
        const val ANSWER_TOKENS = 384
        const val OVERHEAD_TOKENS = 250
    }
}
