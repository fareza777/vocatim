package com.vocatim.app.data.summary

import com.vocatim.llm.PromptFormat

/**
 * On-device summarization models (GGUF, q4_k_m), downloaded on demand.
 * SHA-256 pinned from the published files.
 */
enum class SummaryModel(
    val id: String,
    val fileName: String,
    val url: String,
    val approxSizeBytes: Long,
    val sha256: String,
    val promptFormat: PromptFormat = PromptFormat.CHATML,
    /** Largest context the app will allocate for this model (RAM permitting).
     *  Bounded by KV-cache memory, not the model's native window. */
    val maxContextTokens: Int = 8192,
    /** Minimum device RAM (MB) to offer this model; 0 = any device. */
    val minTotalRamMb: Int = 0,
    /** Sampling tuned per size class: small models need a stronger
     *  repetition penalty to avoid looping; 4B models write better with a
     *  light touch. */
    val temperature: Float = 0.4f,
    val repeatPenalty: Float = 1.2f,
    /** Hybrid-thinking models burn their token budget in a <think> block
     *  unless the prompt carries the /no_think soft switch. */
    val hybridThinking: Boolean = false,
    /** 1..5, shown as stars in the picker. */
    val speedStars: Int = 3,
    val qualityStars: Int = 3,
) {
    QWEN25(
        id = "qwen2.5-1.5b",
        fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/" +
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        approxSizeBytes = 1_117_320_736L,
        sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        // 2 KV heads -> tiny cache; 16K costs only ~240 MB quantized.
        maxContextTokens = 16_384,
        repeatPenalty = 1.25f,
        speedStars = 4,
        qualityStars = 2,
    ),

    /** Gemma 3 1B: the light option — fast summaries on any phone, at the
     *  cost of nuance. */
    GEMMA3_1B(
        id = "gemma3-1b",
        fileName = "gemma-3-1b-it-Q4_K_M.gguf",
        url = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/" +
            "gemma-3-1b-it-Q4_K_M.gguf",
        approxSizeBytes = 806_058_272L,
        sha256 = "8270790f3ab69fdfe860b7b64008d9a19986d8df7e407bb018184caa08798ebd",
        promptFormat = PromptFormat.GEMMA,
        maxContextTokens = 16_384,
        speedStars = 5,
        qualityStars = 2,
    ),

    /** Successor generation: same size class, clearly better multilingual
     *  reasoning. */
    QWEN3(
        id = "qwen3-1.7b",
        fileName = "Qwen3-1.7B-Q4_K_M.gguf",
        url = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/" +
            "Qwen3-1.7B-Q4_K_M.gguf",
        approxSizeBytes = 1_110_000_000L,
        sha256 = "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897",
        // 8 KV heads; 12K quantized KV is ~700 MB, the sane ceiling here.
        maxContextTokens = 12_288,
        hybridThinking = true,
        speedStars = 4,
        qualityStars = 3,
    ),

    /** Qwen3-4B-Instruct-2507: a full quality tier above the 1.x models —
     *  markedly better Indonesian and faithfulness. Instruct-only variant
     *  (no hybrid thinking). Needs a high-RAM device. */
    QWEN3_4B(
        id = "qwen3-4b-2507",
        fileName = "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        url = "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/" +
            "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        approxSizeBytes = 2_497_281_120L,
        sha256 = "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597",
        maxContextTokens = 16_384,
        minTotalRamMb = 6_900,
        temperature = 0.3f,
        repeatPenalty = 1.1f,
        speedStars = 2,
        qualityStars = 5,
    ),

    /** Gemma 3 4B: Google's multilingual workhorse (140+ languages). Uses
     *  Gemma's turn format, not ChatML. Sliding-window attention keeps its
     *  KV cache small even at long contexts. */
    GEMMA3_4B(
        id = "gemma3-4b",
        fileName = "gemma-3-4b-it-Q4_K_M.gguf",
        url = "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF/resolve/main/" +
            "gemma-3-4b-it-Q4_K_M.gguf",
        approxSizeBytes = 2_489_894_016L,
        sha256 = "04a43a22e8d2003deda5acc262f68ec1005fa76c735a9962a8c77042a74a7d19",
        promptFormat = PromptFormat.GEMMA,
        maxContextTokens = 16_384,
        minTotalRamMb = 6_900,
        temperature = 0.3f,
        repeatPenalty = 1.1f,
        speedStars = 2,
        qualityStars = 5,
    ),

    /** Qwen3-8B: flagship-phone territory (12 GB+ RAM). The strongest
     *  writing available on-device; patience required. */
    QWEN3_8B(
        id = "qwen3-8b",
        fileName = "Qwen3-8B-Q4_K_M.gguf",
        url = "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/main/" +
            "Qwen3-8B-Q4_K_M.gguf",
        approxSizeBytes = 5_027_784_512L,
        sha256 = "120307ba529eb2439d6c430d94104dabd578497bc7bfe7e322b5d9933b449bd4",
        // 5 GB of weights leaves little room: keep the context modest.
        maxContextTokens = 8_192,
        minTotalRamMb = 10_800,
        temperature = 0.3f,
        repeatPenalty = 1.1f,
        hybridThinking = true,
        speedStars = 1,
        qualityStars = 5,
    );

    companion object {
        val DEFAULT = QWEN25
        fun fromId(id: String): SummaryModel =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
