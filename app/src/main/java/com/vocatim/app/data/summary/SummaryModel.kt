package com.vocatim.app.data.summary

/**
 * On-device summarization models (GGUF, q4_k_m), downloaded on demand.
 * SHA-256 pinned from the published files. Both use the ChatML template
 * the llama JNI builds manually.
 */
enum class SummaryModel(
    val id: String,
    val fileName: String,
    val url: String,
    val approxSizeBytes: Long,
    val sha256: String,
) {
    QWEN25(
        id = "qwen2.5-1.5b",
        fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/" +
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        approxSizeBytes = 1_117_320_736L,
        sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
    ),

    /** Successor generation: same size class, clearly better multilingual
     *  reasoning. Hybrid-thinking model — prompts must disable think mode. */
    QWEN3(
        id = "qwen3-1.7b",
        fileName = "Qwen3-1.7B-Q4_K_M.gguf",
        url = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/" +
            "Qwen3-1.7B-Q4_K_M.gguf",
        approxSizeBytes = 1_110_000_000L,
        sha256 = "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897",
    );

    companion object {
        val DEFAULT = QWEN25
        fun fromId(id: String): SummaryModel =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
