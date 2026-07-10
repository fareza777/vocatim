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
 * Downloads and manages Whisper ggml model files.
 *
 * - Streams to a `.part` file and resumes interrupted downloads via HTTP Range.
 * - Verifies the finished file: exact size, ggml magic number, and SHA-256
 *   against the checksum pinned in [WhisperModel].
 */
class ModelManager(
    private val modelsDir: File,
    private val client: OkHttpClient,
    private val urlResolver: (WhisperModel) -> String = { it.url },
    private val sha256Resolver: (WhisperModel) -> String? = { it.sha256 },
) {
    private val states: Map<WhisperModel, MutableStateFlow<ModelState>> =
        WhisperModel.entries.associateWith { model ->
            MutableStateFlow(initialState(model))
        }

    fun state(model: WhisperModel): StateFlow<ModelState> = states.getValue(model).asStateFlow()

    fun modelFile(model: WhisperModel): File = File(modelsDir, model.fileName)

    fun isDownloaded(model: WhisperModel): Boolean = states.getValue(model).value == ModelState.Downloaded

    private fun partFile(model: WhisperModel): File = File(modelsDir, model.fileName + ".part")

    private fun initialState(model: WhisperModel): ModelState =
        if (modelFile(model).exists()) ModelState.Downloaded else ModelState.NotDownloaded

    suspend fun download(model: WhisperModel) = withContext(Dispatchers.IO) {
        val state = states.getValue(model)
        if (state.value is ModelState.Downloading) return@withContext
        if (modelFile(model).exists()) {
            state.value = ModelState.Downloaded
            return@withContext
        }

        try {
            if (!modelsDir.exists() && !modelsDir.mkdirs()) {
                throw IOException("Couldn't create models directory: $modelsDir")
            }
            val part = partFile(model)
            val alreadyDownloaded = if (part.exists()) part.length() else 0L
            checkFreeSpace(model.approxSizeBytes - alreadyDownloaded)
            state.value = ModelState.Downloading(alreadyDownloaded, 0L)

            val request = Request.Builder()
                .url(urlResolver(model))
                .apply { if (alreadyDownloaded > 0) header("Range", "bytes=$alreadyDownloaded-") }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Download failed: empty body")

                // Server may ignore Range and send the whole file (200 instead of 206).
                val resuming = response.code == 206 && alreadyDownloaded > 0
                var written = if (resuming) alreadyDownloaded else 0L
                val totalBytes = written + body.contentLength().coerceAtLeast(0)

                RandomAccessFile(part, "rw").use { out ->
                    if (resuming) out.seek(alreadyDownloaded) else out.setLength(0)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            written += read
                            state.value = ModelState.Downloading(written, totalBytes)
                        }
                    }
                }

                if (totalBytes > 0 && part.length() != totalBytes) {
                    throw IOException(
                        "Incomplete download: got ${part.length()} of $totalBytes bytes"
                    )
                }
                verify(part, expectedSha256 = sha256Resolver(model))
            }

            if (!part.renameTo(modelFile(model))) {
                throw IOException("Couldn't move downloaded model into place")
            }
            state.value = ModelState.Downloaded
        } catch (e: CancellationException) {
            // Keep the .part file so the next attempt resumes.
            state.value = initialState(model)
            throw e
        } catch (e: Exception) {
            state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    fun delete(model: WhisperModel) {
        modelFile(model).delete()
        partFile(model).delete()
        states.getValue(model).value = ModelState.NotDownloaded
    }

    /** Fail fast (before streaming gigabytes) when the disk can't fit it. */
    private fun checkFreeSpace(stillNeededBytes: Long) {
        val free = runCatching {
            android.os.StatFs(modelsDir.absolutePath).availableBytes
        }.getOrNull() ?: return
        val required = stillNeededBytes.coerceAtLeast(0) + SPACE_MARGIN_BYTES
        if (free < required) {
            val shortMb = (required - free) / (1024 * 1024)
            throw IOException("Not enough free storage: about $shortMb MB more needed")
        }
    }

    /**
     * Corrupt model files crash native code, so validate before use:
     * ggml magic in the header and, when known, the full SHA-256.
     * A corrupt file is deleted so the next attempt starts clean.
     */
    private fun verify(file: File, expectedSha256: String?) {
        try {
            val magic = ByteArray(4)
            file.inputStream().use { input ->
                if (input.read(magic) != 4) throw IOException("Model file is truncated")
            }
            // GGML_FILE_MAGIC 0x67676d6c, little-endian on disk => "lmgg".
            val magicOk = magic[0] == 0x6c.toByte() && magic[1] == 0x6d.toByte() &&
                magic[2] == 0x67.toByte() && magic[3] == 0x67.toByte()
            if (!magicOk) throw IOException("Not a ggml model file (bad magic)")

            if (expectedSha256 != null) {
                val actual = sha256(file)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("Checksum mismatch: expected $expectedSha256, got $actual")
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024

        /** Keep this much headroom so a model download can't fill the disk. */
        const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024
    }
}
