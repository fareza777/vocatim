package com.vocatim.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class LlamaException(message: String) : Exception(message)

/** Chat prompt formats the summarizer can build (no jinja dependency). */
enum class PromptFormat {
    /** ChatML, used by the Qwen family. */
    CHATML,

    /** Gemma's turn format; it has no system role, so the system text is
     *  prepended to the first user turn. */
    GEMMA,
}

/**
 * On-device summarizer backed by a GGUF instruct model. All native calls are
 * confined to one thread — llama contexts are not thread-safe.
 */
class LlamaSummarizer private constructor(
    private val nThreads: Int,
    private val onDiag: (String) -> Unit,
) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    @Volatile private var loaded = false

    /** Points the native diagnostic log at [path] (a file the app can read). */
    fun setNativeDiagFile(path: String) = LlamaLib.setDiagFile(path)

    /** @param nCtx context size in tokens; sized by the caller to fit the
     *  transcript (long contexts cost KV-cache memory). */
    suspend fun loadIfNeeded(modelPath: String, nCtx: Int) = withContext(dispatcher) {
        if (loaded) return@withContext
        onDiag("load:start threads=$nThreads ctx=$nCtx")
        val ok = LlamaLib.loadModel(modelPath, nThreads, nCtx)
        onDiag("load:returned=$ok")
        if (!ok) throw LlamaException("Couldn't load summary model")
        loaded = true
    }

    /**
     * @param system role instruction, @param user the content to act on.
     * @param onToken streams the accumulated reply text as it is generated.
     * @return the model's reply text.
     */
    suspend fun chat(
        system: String,
        user: String,
        maxTokens: Int,
        format: PromptFormat = PromptFormat.CHATML,
        temperature: Float = 0.4f,
        repeatPenalty: Float = 1.2f,
        onToken: ((String) -> Unit)? = null,
    ): String = withContext(dispatcher) {
        if (!loaded) throw LlamaException("Summary model not loaded")
        val prompt = buildPrompt(system, user, format)
        onDiag("chat:start promptChars=${prompt.length} maxTokens=$maxTokens")
        val sink = onToken?.let { emit ->
            val acc = StringBuilder()
            TokenSink { bytes ->
                acc.append(String(bytes, Charsets.UTF_8))
                emit(acc.toString())
            }
        }
        val out = LlamaLib.complete(prompt, maxTokens, temperature, repeatPenalty, sink).trim()
        onDiag("chat:returned chars=${out.length}")
        out
    }

    private fun buildPrompt(system: String, user: String, format: PromptFormat): String =
        when (format) {
            PromptFormat.CHATML -> buildString {
                append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
                append("<|im_start|>user\n").append(user).append("<|im_end|>\n")
                append("<|im_start|>assistant\n")
            }
            PromptFormat.GEMMA -> buildString {
                append("<start_of_turn>user\n")
                append(system).append("\n\n").append(user)
                append("<end_of_turn>\n<start_of_turn>model\n")
            }
        }

    fun cancel() {
        // Safe to call from any thread; flips a native atomic flag.
        LlamaLib.requestCancel()
    }

    suspend fun release() = withContext(dispatcher) {
        if (loaded) {
            LlamaLib.freeModel()
            loaded = false
        }
        scope.cancel()
        dispatcher.close()
    }

    companion object {
        fun create(nThreads: Int, onDiag: (String) -> Unit = {}): LlamaSummarizer =
            LlamaSummarizer(nThreads.coerceIn(2, 6), onDiag)
    }
}
