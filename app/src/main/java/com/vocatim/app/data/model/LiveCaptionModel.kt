package com.vocatim.app.data.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Streaming English ASR (k2 zipformer 20M, int8) powering the Live recording
 * mode's word-by-word captions. Small on purpose: captions are a preview —
 * the final transcript still comes from the user's chosen engine.
 */
object LiveCaptionModel {
    const val SPEED_STARS = 5
    const val ACCURACY_STARS = 3

    private const val BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17/resolve/main/"

    data class BundleFile(val fileName: String, val sizeBytes: Long, val sha256: String?)

    val FILES = listOf(
        BundleFile(
            "encoder-epoch-99-avg-1.int8.onnx", 42_845_182L,
            "3810755ce7c3ab26b42a8bcf39d191308fa27fb0f53358823ba46141d03b7eb3",
        ),
        BundleFile(
            "decoder-epoch-99-avg-1.int8.onnx", 539_499L,
            "21e2a2acd961b3ac72f55be2f10f1a285e1b0b0ba010d7c0b6eab141411b163c",
        ),
        BundleFile(
            "joiner-epoch-99-avg-1.int8.onnx", 259_572L,
            "e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e",
        ),
        BundleFile("tokens.txt", 5_048L, null),
    )

    val totalBytes: Long = FILES.sumOf { it.sizeBytes }

    fun url(file: BundleFile): String = BASE_URL + file.fileName

    fun dir(modelsDir: File): File = File(modelsDir, "live-en")

    fun isDownloaded(modelsDir: File): Boolean =
        FILES.all { File(dir(modelsDir), it.fileName).exists() }
}

/** Downloads and manages the live-caption bundle (same shape as Parakeet's). */
class LiveCaptionModelManager(
    private val modelsDir: File,
    private val client: OkHttpClient,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    fun isDownloaded(): Boolean = LiveCaptionModel.isDownloaded(modelsDir)

    private fun initialState(): ModelState =
        if (isDownloaded()) ModelState.Downloaded else ModelState.NotDownloaded

    suspend fun download() = withContext(Dispatchers.IO) {
        if (_state.value is ModelState.Downloading) return@withContext
        if (isDownloaded()) {
            _state.value = ModelState.Downloaded
            return@withContext
        }
        try {
            val dir = LiveCaptionModel.dir(modelsDir)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Couldn't create model directory")
            }
            var doneBytes = LiveCaptionModel.FILES.sumOf { f ->
                File(dir, f.fileName).takeIf { it.exists() }?.length() ?: 0L
            }
            _state.value = ModelState.Downloading(doneBytes, LiveCaptionModel.totalBytes)

            for (file in LiveCaptionModel.FILES) {
                val target = File(dir, file.fileName)
                if (target.exists()) continue
                downloadFile(file, target) { written ->
                    _state.value =
                        ModelState.Downloading(doneBytes + written, LiveCaptionModel.totalBytes)
                }
                doneBytes += target.length()
            }
            _state.value = ModelState.Downloaded
        } catch (e: CancellationException) {
            _state.value = initialState()
            throw e
        } catch (e: Exception) {
            _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    private fun downloadFile(
        file: LiveCaptionModel.BundleFile,
        target: File,
        onProgress: (Long) -> Unit,
    ) {
        val part = File(target.parentFile, target.name + ".part")
        val already = if (part.exists()) part.length() else 0L
        val request = Request.Builder()
            .url(LiveCaptionModel.url(file))
            .apply { if (already > 0) header("Range", "bytes=$already-") }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            val resuming = response.code == 206 && already > 0
            var written = if (resuming) already else 0L
            RandomAccessFile(part, "rw").use { out ->
                if (resuming) out.seek(already) else out.setLength(0)
                val buffer = ByteArray(1 shl 16)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(written)
                    }
                }
            }
        }
        file.sha256?.let { expected ->
            val actual = sha256(part)
            if (!actual.equals(expected, ignoreCase = true)) {
                part.delete()
                throw IOException("Checksum mismatch for ${file.fileName}")
            }
        }
        if (!part.renameTo(target)) throw IOException("Couldn't finalize ${file.fileName}")
    }

    fun delete() {
        LiveCaptionModel.dir(modelsDir).deleteRecursively()
        _state.value = ModelState.NotDownloaded
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
