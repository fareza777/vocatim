package com.vocatim.app.data.summary

import kotlinx.coroutines.sync.withLock

/**
 * Fully offline translation via the local summary model. Long texts are
 * translated chunk by chunk (translation output is roughly input-sized, so
 * each chunk gets half the context for input and half for output).
 */
class LocalTranslator(
    private val modelManager: SummaryModelManager,
    private val threads: Int,
    private val model: SummaryModel,
    private val ctxCapTokens: Int,
    private val session: LlmSession = LlmSession(),
) {
    suspend fun translate(text: String, targetLanguage: String): String {
        val modelPath = modelManager.modelFile(model)
        if (!modelPath.exists()) throw SummaryException("MODEL_MISSING")

        val cap = minOf(model.maxContextTokens, ctxCapTokens)
        val inputTokens = (cap - OVERHEAD_TOKENS) / 2
        val chunkChars = inputTokens * CHARS_PER_TOKEN
        val chunks = chunk(text.trim(), chunkChars)

        val system = systemPrompt(targetLanguage) +
            if (model.hybridThinking) " /no_think" else ""

        LlmLock.mutex.withLock {
            val engine = session.acquire(threads, modelPath.absolutePath, cap)
            try {
                val parts = mutableListOf<String>()
                for (part in chunks) {
                    parts += engine.chat(
                        system = system,
                        user = part,
                        maxTokens = inputTokens + OUTPUT_SLACK_TOKENS,
                        format = model.promptFormat,
                        temperature = 0.3f,
                        repeatPenalty = 1.05f,
                    ).replace(Regex("(?s)<think>.*?</think>"), "").trim()
                }
                return parts.joinToString("\n\n")
            } finally {
                session.scheduleRelease()
            }
        }
    }

    private fun systemPrompt(targetLanguage: String): String =
        "You are a professional translator. Translate the user's text into " +
            "$targetLanguage. Preserve the meaning, tone, names, and paragraph " +
            "breaks. Output ONLY the translation — no notes, no preamble."

    /** Splits on sentence boundaries so chunks stay under [chunkChars]. */
    private fun chunk(text: String, chunkChars: Int): List<String> {
        if (text.length <= chunkChars) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (current.length + sentence.length > chunkChars && current.isNotEmpty()) {
                chunks.add(current.toString())
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }

    private companion object {
        const val CHARS_PER_TOKEN = 3
        const val OVERHEAD_TOKENS = 250

        /** Some languages run longer than the source; leave headroom. */
        const val OUTPUT_SLACK_TOKENS = 256
    }
}
