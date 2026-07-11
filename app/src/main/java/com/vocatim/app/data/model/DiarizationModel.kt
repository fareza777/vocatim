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
 * Speaker diarization bundle: pyannote segmentation 3.0 (who-spoke-when) +
 * NVIDIA TitaNet-small speaker embeddings (who-is-who), clustered by
 * sherpa-onnx. Speaker traits are language-agnostic, so this works on any
 * recording even though the embedder was trained on English.
 */
object DiarizationModel {
    const val SEGMENTATION_FILE = "pyannote-segmentation-3.onnx"
    const val EMBEDDING_FILE = "nemo_en_titanet_small.onnx"

    data class BundleFile(
        val fileName: String,
        val url: String,
        val sizeBytes: Long,
        val sha256: String?,
    )

    val FILES = listOf(
        BundleFile(
            SEGMENTATION_FILE,
            "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/main/model.onnx",
            5_992_913L,
            "220ad67ca923bef2fa91f2390c786097bf305bceb5e261d4af67b38e938e1079",
        ),
        BundleFile(
            EMBEDDING_FILE,
            "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/main/nemo_en_titanet_small.onnx",
            40_257_283L,
            "ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e",
        ),
    )

    val totalBytes: Long = FILES.sumOf { it.sizeBytes }

    fun dir(modelsDir: File): File = File(modelsDir, "diarize")

    fun isDownloaded(modelsDir: File): Boolean =
        FILES.all { File(dir(modelsDir), it.fileName).exists() }
}

/** Downloads and manages the diarization bundle (same shape as Parakeet's). */
class DiarizationModelManager(
    private val modelsDir: File,
    private val client: OkHttpClient,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    fun isDownloaded(): Boolean = DiarizationModel.isDownloaded(modelsDir)

    private fun initialState(): ModelState =
        if (isDownloaded()) ModelState.Downloaded else ModelState.NotDownloaded

    suspend fun download() = withContext(Dispatchers.IO) {
        if (_state.value is ModelState.Downloading) return@withContext
        if (isDownloaded()) {
            _state.value = ModelState.Downloaded
            return@withContext
        }
        try {
            val dir = DiarizationModel.dir(modelsDir)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Couldn't create model directory")
            }
            var doneBytes = DiarizationModel.FILES.sumOf { f ->
                File(dir, f.fileName).takeIf { it.exists() }?.length() ?: 0L
            }
            _state.value = ModelState.Downloading(doneBytes, DiarizationModel.totalBytes)

            for (file in DiarizationModel.FILES) {
                val target = File(dir, file.fileName)
                if (target.exists()) continue
                downloadFile(file, target) { written ->
                    _state.value =
                        ModelState.Downloading(doneBytes + written, DiarizationModel.totalBytes)
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
        file: DiarizationModel.BundleFile,
        target: File,
        onProgress: (Long) -> Unit,
    ) {
        val part = File(target.parentFile, target.name + ".part")
        val already = if (part.exists()) part.length() else 0L
        val request = Request.Builder()
            .url(file.url)
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
        DiarizationModel.dir(modelsDir).deleteRecursively()
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
