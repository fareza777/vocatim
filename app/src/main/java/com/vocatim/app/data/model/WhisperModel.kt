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
    /** 1..5, shown as stars — the whole speed/accuracy story at a glance. */
    val speedStars: Int,
    val accuracyStars: Int,
) {
    TINY(
        id = "tiny",
        fileName = "ggml-tiny.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        approxSizeBytes = 77_691_713L,
        sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
        speedStars = 5,
        accuracyStars = 2,
    ),
    BASE(
        id = "base",
        fileName = "ggml-base.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        approxSizeBytes = 147_951_465L,
        sha256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
        speedStars = 4,
        accuracyStars = 3,
    ),
    SMALL(
        id = "small",
        fileName = "ggml-small.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
        approxSizeBytes = 487_601_967L,
        sha256 = "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
        speedStars = 2,
        accuracyStars = 4,
    ),
    SMALL_Q5(
        id = "small-q5_1",
        fileName = "ggml-small-q5_1.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        approxSizeBytes = 190_085_487L,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        speedStars = 3,
        accuracyStars = 4,
    ),

    /** Classic medium: a solid accuracy step above small for languages the
     *  distilled turbo occasionally fumbles; the slowest option here. */
    MEDIUM_Q5(
        id = "medium-q5_0",
        fileName = "ggml-medium-q5_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin",
        approxSizeBytes = 539_212_467L,
        sha256 = "19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f",
        speedStars = 1,
        accuracyStars = 4,
    ),

    /** large-v3-turbo: near large-v3 accuracy with a distilled 4-layer
     *  decoder. The best quality available; noticeably slower than small. */
    LARGE_TURBO_Q5(
        id = "large-v3-turbo-q5_0",
        fileName = "ggml-large-v3-turbo-q5_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        approxSizeBytes = 574_041_195L,
        sha256 = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        speedStars = 2,
        accuracyStars = 5,
    ),

    /** Same turbo at 8-bit: measurably sharper than q5_0 on hard audio,
     *  at a 300 MB premium. */
    LARGE_TURBO_Q8(
        id = "large-v3-turbo-q8_0",
        fileName = "ggml-large-v3-turbo-q8_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q8_0.bin",
        approxSizeBytes = 874_188_075L,
        sha256 = "317eb69c11673c9de1e1f0d459b253999804ec71ac4c23c17ecf5fbe24e259a1",
        speedStars = 1,
        accuracyStars = 5,
    );

    companion object {
        fun fromId(id: String): WhisperModel? = entries.firstOrNull { it.id == id }
    }
}
