package com.vocatim.app.data.model

/**
 * Whisper ggml models hosted on Hugging Face (ggerganov/whisper.cpp).
 * [approxSizeBytes] is only used for UI hints and free-space checks;
 * the authoritative size comes from Content-Length at download time.
 */
enum class WhisperModel(
    val id: String,
    val fileName: String,
    val url: String,
    val approxSizeBytes: Long,
) {
    TINY(
        id = "tiny",
        fileName = "ggml-tiny.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        approxSizeBytes = 78L * 1024 * 1024,
    ),
    BASE(
        id = "base",
        fileName = "ggml-base.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        approxSizeBytes = 148L * 1024 * 1024,
    );

    companion object {
        fun fromId(id: String): WhisperModel? = entries.firstOrNull { it.id == id }
    }
}
