package com.vocatim.app.data.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Silero VAD model (ggml, <1 MB) used to pre-filter silence before Whisper.
 * Fetched lazily on first use; transcription proceeds without VAD when it
 * can't be downloaded (e.g. offline), so this must never fail a job.
 */
object VadModel {
    private const val FILE_NAME = "ggml-silero-v5.1.2.bin"
    private const val URL =
        "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v5.1.2.bin"
    private const val SHA256 =
        "29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf"

    fun file(modelsDir: File): File = File(modelsDir, FILE_NAME)

    /** The VAD model file, downloading it first if needed; null on failure. */
    suspend fun ensure(modelsDir: File, client: OkHttpClient): File? =
        withContext(Dispatchers.IO) {
            val target = file(modelsDir)
            if (target.exists()) return@withContext target
            runCatching {
                modelsDir.mkdirs()
                val tmp = File(modelsDir, "$FILE_NAME.part")
                client.newCall(Request.Builder().url(URL).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty body")
                    tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                if (!sha256(tmp).equals(SHA256, ignoreCase = true)) {
                    tmp.delete()
                    throw IOException("VAD model checksum mismatch")
                }
                if (!tmp.renameTo(target)) throw IOException("Couldn't finalize VAD model")
                target
            }.getOrNull()
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
