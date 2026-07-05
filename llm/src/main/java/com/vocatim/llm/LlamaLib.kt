package com.vocatim.llm

internal class LlamaLib {
    companion object {
        init {
            System.loadLibrary("vocatim_llm")
        }

        external fun setDiagFile(path: String)
        external fun loadModel(path: String, nThreads: Int, nCtx: Int): Boolean
        external fun complete(prompt: String, maxTokens: Int): String
        external fun requestCancel()
        external fun freeModel()
    }
}
