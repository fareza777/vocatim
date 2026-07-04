package com.vocatim.app.data.model

/**
 * Whisper ggml models hosted on Hugging Face (ggerganov/whisper.cpp).
 * [approxSizeBytes] is only used for UI hints and free-space checks;
 * the authoritative size comes from Content-Length at download time.
 * [sha256] is pinned from the published files (verified 2026-07-04);
 * bump these when the upstream model files change.
 */
enum class WhisperModel(
    val id: String,
    val fileName: String,
    val url: String,
    val approxSizeBytes: Long,
    val sha256: String,
) {
    TINY(
        id = "tiny",
        fileName = "ggml-tiny.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        approxSizeBytes = 77_691_713L,
        sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
    ),
    BASE(
        id = "base",
        fileName = "ggml-base.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        approxSizeBytes = 147_951_465L,
        sha256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
    );

    companion object {
        fun fromId(id: String): WhisperModel? = entries.firstOrNull { it.id == id }
    }
}
