package com.vocatim.app.data.summary

import com.vocatim.llm.LlamaSummarizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

class SummaryException(message: String) : Exception(message)

/**
 * Transcript summarization on-device via [LlamaSummarizer]. The context is
 * sized dynamically so that most transcripts are summarized in a SINGLE pass
 * over the full text — far more coherent than chunked summarization. Only
 * recordings too long for the device's RAM budget fall back to map-reduce
 * (summarize chunks, then condense the partial summaries).
 */
class Summarizer(
    private val modelManager: SummaryModelManager,
    private val threads: Int,
    private val diagFile: java.io.File? = null,
    private val model: SummaryModel = SummaryModel.DEFAULT,
    /** Device-RAM ceiling for the context, from [ContextBudget.ramCapTokens]. */
    private val ctxCapTokens: Int = 4_096,
) {
    @Volatile private var engine: LlamaSummarizer? = null

    /** Requests cancellation of an in-flight summarization. */
    fun cancel() {
        engine?.cancel()
    }

    private fun diag(msg: String) {
        diagFile?.let { runCatching { it.appendText(msg + "\n") } }
    }

    /**
     * @param onProgress 0f..1f.
     * @param onPartial streams the growing text of the final pass, for live
     *   display. Suspends until the summary is complete.
     */
    suspend fun summarize(
        text: String,
        language: String,
        minutes: Boolean = false,
        onPartial: ((String) -> Unit)? = null,
        onProgress: (Float) -> Unit,
    ): String {
        val cleaned = text.trim()
        if (cleaned.length < MIN_CHARS) {
            throw SummaryException("TOO_SHORT")
        }
        val modelPath = modelManager.modelFile(model)
        if (!modelPath.exists()) throw SummaryException("MODEL_MISSING")

        val outBudget = if (minutes) MINUTES_TOKENS else SUMMARY_TOKENS
        val nCtx = contextFor(cleaned.length, outBudget)
        // Fits when the whole transcript + reply stay inside the context.
        val singlePass = estimateTokens(cleaned.length) + outBudget + PROMPT_OVERHEAD_TOKENS <= nCtx

        // Fresh diagnostic trace for this run.
        diagFile?.let {
            runCatching {
                it.writeText(
                    "summarize:start chars=${cleaned.length} lang=$language " +
                        "ctx=$nCtx singlePass=$singlePass model=${model.id}\n"
                )
            }
        }
        return LlmLock.mutex.withLock { run(cleaned, language, minutes, nCtx, singlePass, onPartial, onProgress) }
    }

    private suspend fun run(
        cleaned: String,
        language: String,
        minutes: Boolean,
        nCtx: Int,
        singlePass: Boolean,
        onPartial: ((String) -> Unit)?,
        onProgress: (Float) -> Unit,
    ): String {
        val modelPath = modelManager.modelFile(model)
        val engine = LlamaSummarizer.create(threads) { diag(it) }.also { this.engine = it }
        try {
            diagFile?.let { engine.setNativeDiagFile(it.absolutePath) }
            engine.loadIfNeeded(modelPath.absolutePath, nCtx)
            onProgress(0.05f)

            val system = systemPrompt(language) +
                // Qwen3 is a hybrid-thinking model: without the soft switch it
                // spends its whole token budget inside a <think> block.
                if (model == SummaryModel.QWEN3) " /no_think" else ""

            val indonesian = language == "id"
            val result = if (singlePass) {
                singlePassResult(engine, system, cleaned, indonesian, language, minutes, onPartial, onProgress)
            } else {
                mapReduceResult(engine, system, cleaned, indonesian, language, minutes, nCtx, onPartial, onProgress)
            }
            onProgress(1f)
            val cleanedResult = stripThink(result)
            if (cleanedResult.isBlank()) throw SummaryException("EMPTY")
            return cleanedResult
        } catch (e: CancellationException) {
            engine.cancel()
            throw e
        } finally {
            engine.release()
            this.engine = null
        }
    }

    /** The whole transcript fits: one generation over the full text. */
    private suspend fun singlePassResult(
        engine: LlamaSummarizer,
        system: String,
        transcript: String,
        indonesian: Boolean,
        language: String,
        minutes: Boolean,
        onPartial: ((String) -> Unit)?,
        onProgress: (Float) -> Unit,
    ): String {
        val transcriptLabel = if (indonesian) "Transkrip" else "Transcript"
        val user = if (minutes) {
            minutesInstruction(indonesian, language, fromTranscript = true) +
                "\n\n$transcriptLabel:\n\n$transcript"
        } else {
            "$transcriptLabel:\n\n$transcript\n\n" + summaryLabel(indonesian, language)
        }
        onProgress(0.15f)
        return engine.chat(
            system = system,
            user = user,
            maxTokens = if (minutes) MINUTES_TOKENS else SUMMARY_TOKENS,
            format = model.promptFormat,
            temperature = model.temperature,
            repeatPenalty = model.repeatPenalty,
            onToken = onPartial?.let { emit -> { partial -> emit(stripThink(partial)) } },
        )
    }

    /** Too long for one pass: summarize chunks, then condense the partials. */
    private suspend fun mapReduceResult(
        engine: LlamaSummarizer,
        system: String,
        transcript: String,
        indonesian: Boolean,
        language: String,
        minutes: Boolean,
        nCtx: Int,
        onPartial: ((String) -> Unit)?,
        onProgress: (Float) -> Unit,
    ): String {
        val transcriptLabel = if (indonesian) "Transkrip" else "Transcript"
        // Chunk to what actually fits the allocated context.
        val chunkChars = (nCtx - MAP_TOKENS - PROMPT_OVERHEAD_TOKENS) * CHARS_PER_TOKEN
        val chunks = chunk(transcript, chunkChars)

        val partials = mutableListOf<String>()
        for ((i, chunkText) in chunks.withIndex()) {
            val partial = engine.chat(
                system = system,
                user = "$transcriptLabel:\n\n$chunkText\n\n" + summaryLabel(indonesian, language),
                maxTokens = MAP_TOKENS,
                format = model.promptFormat,
                temperature = model.temperature,
                repeatPenalty = model.repeatPenalty,
            )
            partials.add(stripThink(partial))
            // Map phase occupies the first 85% of the bar.
            onProgress(0.05f + 0.80f * (i + 1) / chunks.size)
        }

        val onToken = onPartial?.let { emit -> { partial: String -> emit(stripThink(partial)) } }
        return when {
            // Minutes: reshape the partial summaries into a structured
            // document as a final pass.
            minutes -> engine.chat(
                system = system,
                user = minutesInstruction(indonesian, language, fromTranscript = false) +
                    "\n\n" + partials.joinToString("\n\n"),
                maxTokens = MINUTES_TOKENS,
                format = model.promptFormat,
                temperature = model.temperature,
                repeatPenalty = model.repeatPenalty,
                onToken = onToken,
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
                    format = model.promptFormat,
                    temperature = model.temperature,
                    repeatPenalty = model.repeatPenalty,
                    onToken = onToken,
                )
            }
        }
    }

    /** Context size for this run: fit the transcript when the model and the
     *  device RAM budget allow, otherwise the largest affordable window. */
    private fun contextFor(transcriptChars: Int, outBudget: Int): Int {
        val needed = estimateTokens(transcriptChars) + outBudget + PROMPT_OVERHEAD_TOKENS
        val cap = minOf(model.maxContextTokens, ctxCapTokens)
        // Round up so small overshoots don't force a pointless second chunk.
        val rounded = ((needed + 1_023) / 1_024) * 1_024
        return rounded.coerceIn(MIN_CTX, cap)
    }

    private fun estimateTokens(chars: Int): Int = chars / CHARS_PER_TOKEN + 1

    private fun summaryLabel(indonesian: Boolean, language: String): String =
        if (indonesian) {
            "Ringkasan (poin-poin berbeda, bahasa Indonesia):"
        } else {
            "Summary in ${languageName(language)} (distinct bullet points):"
        }

    private fun minutesInstruction(
        indonesian: Boolean,
        language: String,
        fromTranscript: Boolean,
    ): String =
        if (indonesian) {
            (if (fromTranscript) "Susun transkrip berikut" else "Susun poin-poin berikut") +
                " menjadi notulen rapat rapi berformat Markdown " +
                "dalam bahasa Indonesia dengan bagian: # Notulen Rapat, **Topik**, " +
                "**Ringkasan** (2-3 kalimat), ## Poin Pembahasan, ## Keputusan " +
                "(tulis \"Tidak ada\" bila tidak ada), ## Action Item. " +
                "Setia pada isi; jangan menambah informasi:"
        } else {
            "Turn the following " + (if (fromTranscript) "transcript" else "points") +
                " into clean Markdown meeting minutes in " +
                "${languageName(language)} with sections: # Meeting Minutes, **Topic**, " +
                "**Summary** (2-3 sentences), ## Discussion Points, ## Decisions " +
                "(write \"None\" if none), ## Action Items. Be faithful; do not " +
                "invent information:"
        }

    /** Drops Qwen3 reasoning blocks (and stray tags) from model output. */
    private fun stripThink(text: String): String = text
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .replace("<think>", "")
        .replace("</think>", "")
        .trim()

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
        const val MIN_CHARS = 200
        const val MIN_CTX = 4_096
        // Indonesian averages ~3.2 chars/token on these tokenizers; 3 is the
        // safe (overestimating) side.
        const val CHARS_PER_TOKEN = 3
        // ChatML/Gemma scaffolding + system prompt + labels.
        const val PROMPT_OVERHEAD_TOKENS = 300
        const val SUMMARY_TOKENS = 512
        const val MAP_TOKENS = 384
        const val REDUCE_TOKENS = 640
        const val MINUTES_TOKENS = 1_024
    }
}
