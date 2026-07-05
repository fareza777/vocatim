package com.vocatim.app.data.summary

import com.vocatim.llm.LlamaSummarizer
import kotlinx.coroutines.CancellationException

class SummaryException(message: String) : Exception(message)

/**
 * Map-reduce summarization over a transcript. Long transcripts are split into
 * chunks small enough for the model's context; each is summarized ("map"),
 * then the partial summaries are condensed into one ("reduce"). Runs entirely
 * on-device via [LlamaSummarizer].
 */
class Summarizer(
    private val modelManager: SummaryModelManager,
    private val threads: Int,
    private val diagFile: java.io.File? = null,
) {
    @Volatile private var engine: LlamaSummarizer? = null

    /** Requests cancellation of an in-flight summarization. */
    fun cancel() {
        engine?.cancel()
    }

    private fun diag(msg: String) {
        diagFile?.let { runCatching { it.appendText(msg + "\n") } }
    }

    /** @param onProgress 0f..1f. Suspends until the summary is complete. */
    suspend fun summarize(
        text: String,
        language: String,
        onProgress: (Float) -> Unit,
    ): String {
        val cleaned = text.trim()
        if (cleaned.length < MIN_CHARS) {
            throw SummaryException("TOO_SHORT")
        }
        val modelPath = modelManager.modelFile
        if (!modelPath.exists()) throw SummaryException("MODEL_MISSING")

        // Fresh diagnostic trace for this run.
        diagFile?.let { runCatching { it.writeText("summarize:start chars=${cleaned.length}\n") } }
        val engine = LlamaSummarizer.create(threads) { diag(it) }.also { this.engine = it }
        try {
            diagFile?.let { engine.setNativeDiagFile(it.absolutePath) }
            engine.loadIfNeeded(modelPath.absolutePath)
            onProgress(0.05f)

            val system = systemPrompt(language)
            val chunks = chunk(cleaned)

            val partials = mutableListOf<String>()
            for ((i, chunkText) in chunks.withIndex()) {
                val partial = engine.chat(
                    system = system,
                    user = "Ringkas transkrip berikut:\n\n$chunkText",
                    maxTokens = MAP_TOKENS,
                )
                partials.add(partial)
                // Map phase occupies the first 85% of the bar.
                onProgress(0.05f + 0.80f * (i + 1) / chunks.size)
            }

            val result = if (partials.size == 1) {
                partials.first()
            } else {
                engine.chat(
                    system = system,
                    user = "Gabungkan ringkasan-ringkasan bagian berikut menjadi satu " +
                        "ringkasan akhir yang runut dan tidak berulang:\n\n" +
                        partials.joinToString("\n\n"),
                    maxTokens = REDUCE_TOKENS,
                )
            }
            onProgress(1f)
            if (result.isBlank()) throw SummaryException("EMPTY")
            return result.trim()
        } catch (e: CancellationException) {
            engine.cancel()
            throw e
        } finally {
            engine.release()
            this.engine = null
        }
    }

    private fun systemPrompt(language: String): String {
        val langLine = when (language) {
            "en" -> "Write the summary in English."
            "id" -> "Tulis ringkasan dalam bahasa Indonesia."
            else -> "Write the summary in the same language as the transcript."
        }
        return "You are a helpful assistant that writes concise, faithful summaries " +
            "of meeting and voice-note transcripts. Capture the key points, decisions, " +
            "and any action items as short bullet points. Do not invent information. " +
            langLine
    }

    /** Splits on sentence boundaries so chunks stay under the token budget. */
    private fun chunk(text: String): List<String> {
        if (text.length <= CHUNK_CHARS) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (current.length + sentence.length > CHUNK_CHARS && current.isNotEmpty()) {
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
        const val MIN_CHARS = 200
        // ~4 chars/token; ~2200 tokens per chunk leaves room for the reply.
        const val CHUNK_CHARS = 8_800
        const val MAP_TOKENS = 320
        const val REDUCE_TOKENS = 480
    }
}
