package com.vocatim.app.data.summary

/**
 * The on-device summarization model (Qwen2.5-1.5B-Instruct, q4_k_m GGUF).
 * Downloaded on demand, like the Whisper models. SHA-256 pinned from the
 * published file.
 */
object SummaryModel {
    const val FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val APPROX_SIZE_BYTES = 1_117_320_736L
    const val SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
}
