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
 * NVIDIA Parakeet TDT 0.6B (int8 ONNX via sherpa-onnx): English-only engine
 * with top-of-leaderboard accuracy at several times whisper's speed. Ships as
 * a multi-file bundle, unlike the single-file whisper ggml models.
 */
object ParakeetModel {
    /** Sentinel model id stored on transcripts and in prefs. */
    const val ID = "parakeet-en"

    const val SPEED_STARS = 5
    const val ACCURACY_STARS = 5

    private const val BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/"

    data class BundleFile(val fileName: String, val sizeBytes: Long, val sha256: String?)

    val FILES = listOf(
        BundleFile(
            "encoder.int8.onnx", 652_184_296L,
            "a32b12d17bbbc309d0686fbbcc2987b5e9b8333a7da83fa6b089f0a2acd651ab",
        ),
        BundleFile(
            "decoder.int8.onnx", 7_257_753L,
            "b6bb64963457237b900e496ee9994b59294526439fbcc1fecf705b31a15c6b4e",
        ),
        BundleFile(
            "joiner.int8.onnx", 1_739_080L,
            "7946164367946e7f9f29a122407c3252b680dbae9a51343eb2488d057c3c43d2",
        ),
        // Tiny text file; not stored as LFS upstream, so no pinned hash.
        BundleFile("tokens.txt", 9_384L, null),
    )

    val totalBytes: Long = FILES.sumOf { it.sizeBytes }

    fun url(file: BundleFile): String = BASE_URL + file.fileName

    /** All bundle files live in their own subdirectory of the models dir. */
    fun dir(modelsDir: File): File = File(modelsDir, "parakeet-en")

    fun isDownloaded(modelsDir: File): Boolean =
        FILES.all { File(dir(modelsDir), it.fileName).exists() }
}

/**
 * Downloads and manages the Parakeet bundle. Mirrors [ModelManager]'s
 * behavior (streaming, HTTP-Range resume, SHA-256 verification) across the
 * bundle's files, reporting one combined progress.
 */
class ParakeetModelManager(
    private val modelsDir: File,
    private val client: OkHttpClient,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private fun bundleDir() = ParakeetModel.dir(modelsDir)

    fun isDownloaded(): Boolean = ParakeetModel.isDownloaded(modelsDir)

    private fun initialState(): ModelState =
        if (isDownloaded()) ModelState.Downloaded else ModelState.NotDownloaded

    suspend fun download() = withContext(Dispatchers.IO) {
        if (_state.value is ModelState.Downloading) return@withContext
        if (isDownloaded()) {
            _state.value = ModelState.Downloaded
            return@withContext
        }
        try {
            val dir = bundleDir()
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Couldn't create model directory")
            }
            checkFreeSpace(dir)

            var doneBytes = ParakeetModel.FILES.sumOf { f ->
                File(dir, f.fileName).takeIf { it.exists() }?.length() ?: 0L
            }
            _state.value = ModelState.Downloading(doneBytes, ParakeetModel.totalBytes)

            for (file in ParakeetModel.FILES) {
                val target = File(dir, file.fileName)
                if (target.exists()) continue
                downloadFile(file, target) { written ->
                    _state.value =
                        ModelState.Downloading(doneBytes + written, ParakeetModel.totalBytes)
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
        file: ParakeetModel.BundleFile,
        target: File,
        onProgress: (Long) -> Unit,
    ) {
        val part = File(target.parentFile, target.name + ".part")
        val already = if (part.exists()) part.length() else 0L
        val request = Request.Builder()
            .url(ParakeetModel.url(file))
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
        bundleDir().deleteRecursively()
        _state.value = ModelState.NotDownloaded
    }

    private fun checkFreeSpace(dir: File) {
        val free = runCatching {
            android.os.StatFs(dir.absolutePath).availableBytes
        }.getOrNull() ?: return
        val stillNeeded = ParakeetModel.FILES.sumOf { f ->
            val existing = File(dir, f.fileName).takeIf { it.exists() }?.length() ?: 0L
            (f.sizeBytes - existing).coerceAtLeast(0)
        } + SPACE_MARGIN_BYTES
        if (free < stillNeeded) {
            val shortMb = (stillNeeded - free) / (1024 * 1024)
            throw IOException("Not enough free storage: about $shortMb MB more needed")
        }
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

    private companion object {
        const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024
    }
}
