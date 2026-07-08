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
        minutes: Boolean = false,
        onProgress: (Float) -> Unit,
    ): String {
        val cleaned = text.trim()
        if (cleaned.length < MIN_CHARS) {
            throw SummaryException("TOO_SHORT")
        }
        val modelPath = modelManager.modelFile
        if (!modelPath.exists()) throw SummaryException("MODEL_MISSING")

        // Fresh diagnostic trace for this run.
        diagFile?.let {
            runCatching {
                it.writeText("summarize:start chars=${cleaned.length} lang=$language\n")
            }
        }
        val engine = LlamaSummarizer.create(threads) { diag(it) }.also { this.engine = it }
        try {
            diagFile?.let { engine.setNativeDiagFile(it.absolutePath) }
            engine.loadIfNeeded(modelPath.absolutePath)
            onProgress(0.05f)

            val system = systemPrompt(language)
            val chunks = chunk(cleaned)

            val indonesian = language == "id"
            val transcriptLabel = if (indonesian) "Transkrip" else "Transcript"
            val summaryLabel = if (indonesian) {
                "Ringkasan (poin-poin berbeda, bahasa Indonesia):"
            } else {
                "Summary in ${languageName(language)} (distinct bullet points):"
            }

            val partials = mutableListOf<String>()
            for ((i, chunkText) in chunks.withIndex()) {
                val partial = engine.chat(
                    system = system,
                    user = "$transcriptLabel:\n\n$chunkText\n\n$summaryLabel",
                    maxTokens = MAP_TOKENS,
                )
                partials.add(partial)
                // Map phase occupies the first 85% of the bar.
                onProgress(0.05f + 0.80f * (i + 1) / chunks.size)
            }

            val result = when {
                // Minutes: reshape the (partial) summaries into a structured
                // document. A 1.5B model handles this best as a final pass.
                minutes -> engine.chat(
                    system = system,
                    user = minutesInstruction(indonesian, language) + "\n\n" +
                        partials.joinToString("\n\n"),
                    maxTokens = MINUTES_TOKENS,
                )
                partials.size == 1 -> partials.first()
                else -> {
                    val reduceInstruction = if (indonesian) {
                        "Gabungkan ringkasan-ringkasan bagian ini menjadi satu set poin " +
                            "akhir yang berbeda dan tidak tumpang tindih, dalam bahasa Indonesia:"
                    } else {
                        "Combine these partial summaries into one final set of distinct, " +
                            "non-overlapping bullet points, written in ${languageName(language)}:"
                    }
                    engine.chat(
                        system = system,
                        user = "$reduceInstruction\n\n" + partials.joinToString("\n\n"),
                        maxTokens = REDUCE_TOKENS,
                    )
                }
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

    private fun minutesInstruction(indonesian: Boolean, language: String): String =
        if (indonesian) {
            "Susun poin-poin berikut menjadi notulen rapat rapi berformat Markdown " +
                "dalam bahasa Indonesia dengan bagian: # Notulen Rapat, **Topik**, " +
                "**Ringkasan** (2-3 kalimat), ## Poin Pembahasan, ## Keputusan " +
                "(tulis \"Tidak ada\" bila tidak ada), ## Action Item. " +
                "Setia pada isi; jangan menambah informasi:"
        } else {
            "Turn the following points into clean Markdown meeting minutes in " +
                "${languageName(language)} with sections: # Meeting Minutes, **Topic**, " +
                "**Summary** (2-3 sentences), ## Discussion Points, ## Decisions " +
                "(write \"None\" if none), ## Action Items. Be faithful; do not " +
                "invent information:"
        }

    /** English display name of a language code, e.g. "id" -> "Indonesian". */
    private fun languageName(code: String): String =
        java.util.Locale(code).getDisplayLanguage(java.util.Locale.ENGLISH)
            .ifBlank { "the same language as the transcript" }

    // A small model follows the language of its instructions, so Indonesian
    // (the primary market) gets a fully-localized prompt. Other languages get
    // an English prompt with an explicit, strongly-worded target-language line.
    private fun systemPrompt(language: String): String = if (language == "id") {
        "Anda meringkas transkrip rapat dan catatan suara. Tulis 3 sampai 6 poin " +
            "singkat dalam bahasa Indonesia. Setiap poin harus berisi gagasan yang " +
            "BERBEDA — jangan pernah mengulang atau memparafrase poin sebelumnya. " +
            "Setia pada isi; jangan mengarang. Sertakan keputusan dan action item bila " +
            "ada. Berhenti setelah poin terakhir yang berbeda."
    } else {
        val name = languageName(language)
        "You summarize meeting and voice-note transcripts. Write 3 to 6 short bullet " +
            "points. Write the ENTIRE summary in $name — do not use any other language. " +
            "Each bullet must express a DIFFERENT idea — never repeat or rephrase a " +
            "previous point. Keep it faithful; do not invent facts. Include decisions and " +
            "action items if present. Stop after the last distinct point."
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
        const val MINUTES_TOKENS = 700
    }
}
