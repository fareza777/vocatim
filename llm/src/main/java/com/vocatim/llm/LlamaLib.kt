package com.vocatim.llm

/** Receives UTF-8-safe increments of generated text as they are decoded. */
fun interface TokenSink {
    fun onText(bytes: ByteArray)
}

internal class LlamaLib {
    companion object {
        init {
            System.loadLibrary("vocatim_llm")
        }

        external fun setDiagFile(path: String)
        external fun loadModel(path: String, nThreads: Int, nCtx: Int): Boolean
        external fun complete(
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            repeatPenalty: Float,
            sink: TokenSink?,
        ): String
        external fun requestCancel()
        external fun freeModel()
    }
}
