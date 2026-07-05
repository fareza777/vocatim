package com.vocatim.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class LlamaException(message: String) : Exception(message)

/**
 * On-device summarizer backed by a GGUF instruct model (Qwen2.5). All native
 * calls are confined to one thread — llama contexts are not thread-safe.
 * Prompts use Qwen's ChatML format directly (no jinja dependency).
 */
class LlamaSummarizer private constructor(private val nThreads: Int) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    @Volatile private var loaded = false

    suspend fun loadIfNeeded(modelPath: String) = withContext(dispatcher) {
        if (loaded) return@withContext
        if (!LlamaLib.loadModel(modelPath, nThreads, CONTEXT_TOKENS)) {
            throw LlamaException("Couldn't load summary model")
        }
        loaded = true
    }

    /**
     * @param system role instruction, @param user the content to act on.
     * @return the model's reply text.
     */
    suspend fun chat(system: String, user: String, maxTokens: Int): String =
        withContext(dispatcher) {
            if (!loaded) throw LlamaException("Summary model not loaded")
            val prompt = buildString {
                append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
                append("<|im_start|>user\n").append(user).append("<|im_end|>\n")
                append("<|im_start|>assistant\n")
            }
            LlamaLib.complete(prompt, maxTokens).trim()
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
        /** Roughly 3000 words of transcript per chunk plus room for output. */
        const val CONTEXT_TOKENS = 4096

        fun create(nThreads: Int): LlamaSummarizer =
            LlamaSummarizer(nThreads.coerceIn(2, 6))
    }
}
