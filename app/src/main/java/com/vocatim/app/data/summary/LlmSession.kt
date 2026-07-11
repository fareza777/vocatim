package com.vocatim.app.data.summary

import com.vocatim.llm.LlamaSummarizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the llama engine warm for a few minutes after use, so a follow-up
 * Ask-AI question or a regenerate skips the multi-gigabyte model reload.
 *
 * Contract: [acquire] and the returned engine must only be used while
 * holding [LlmLock]; call [scheduleRelease] (still under the lock) when done
 * instead of releasing the engine yourself.
 */
class LlmSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var engine: LlamaSummarizer? = null
    private var loadedPath: String? = null
    private var loadedCtx: Int = 0
    private var releaseJob: Job? = null

    /** Reuses the warm engine when the same model is loaded with a context
     *  at least as large as requested; otherwise swaps it out. */
    suspend fun acquire(
        threads: Int,
        modelPath: String,
        nCtx: Int,
        onDiag: (String) -> Unit = {},
    ): LlamaSummarizer {
        releaseJob?.cancel()
        releaseJob = null
        engine?.let { warm ->
            if (loadedPath == modelPath && nCtx <= loadedCtx) {
                onDiag("session:warm-hit ctx=$loadedCtx")
                return warm
            }
            warm.release()
            engine = null
        }
        val created = LlamaSummarizer.create(threads, onDiag)
        created.loadIfNeeded(modelPath, nCtx)
        engine = created
        loadedPath = modelPath
        loadedCtx = nCtx
        return created
    }

    /** Starts the keep-warm countdown; the next [acquire] cancels it. */
    fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(KEEP_WARM_MS)
            LlmLock.mutex.withLock {
                engine?.release()
                engine = null
                loadedPath = null
                loadedCtx = 0
            }
        }
    }

    private companion object {
        /** Long enough for a follow-up question, short enough for the RAM. */
        const val KEEP_WARM_MS = 3 * 60_000L
    }
}
