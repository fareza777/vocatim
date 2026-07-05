package com.vocatim.app.data.summary

import com.vocatim.app.data.model.ModelState
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
 * Downloads and manages the single summarization GGUF model. Mirrors
 * [com.vocatim.app.data.model.ModelManager]: streams to a `.part` file,
 * resumes via HTTP Range, verifies the GGUF magic and (when pinned) SHA-256.
 */
class SummaryModelManager(
    private val modelsDir: File,
    private val client: OkHttpClient,
    private val url: String = SummaryModel.URL,
    private val expectedSha256: String? = SummaryModel.SHA256.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) },
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val modelFile: File get() = File(modelsDir, SummaryModel.FILE_NAME)
    private val partFile: File get() = File(modelsDir, SummaryModel.FILE_NAME + ".part")

    fun isDownloaded(): Boolean = modelFile.exists()

    private fun initialState(): ModelState =
        if (modelFile.exists()) ModelState.Downloaded else ModelState.NotDownloaded

    suspend fun download() = withContext(Dispatchers.IO) {
        if (_state.value is ModelState.Downloading) return@withContext
        if (modelFile.exists()) {
            _state.value = ModelState.Downloaded
            return@withContext
        }
        try {
            if (!modelsDir.exists() && !modelsDir.mkdirs()) {
                throw IOException("Couldn't create models directory")
            }
            val already = if (partFile.exists()) partFile.length() else 0L
            _state.value = ModelState.Downloading(already, 0L)

            val request = Request.Builder()
                .url(url)
                .apply { if (already > 0) header("Range", "bytes=$already-") }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty body")
                val resuming = response.code == 206 && already > 0
                var written = if (resuming) already else 0L
                val total = written + body.contentLength().coerceAtLeast(0)

                RandomAccessFile(partFile, "rw").use { out ->
                    if (resuming) out.seek(already) else out.setLength(0)
                    val buffer = ByteArray(1 shl 16)
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            written += read
                            _state.value = ModelState.Downloading(written, total)
                        }
                    }
                }
                if (total > 0 && partFile.length() != total) {
                    throw IOException("Incomplete download")
                }
                verify(partFile)
            }
            if (!partFile.renameTo(modelFile)) throw IOException("Couldn't finalize model")
            _state.value = ModelState.Downloaded
        } catch (e: CancellationException) {
            _state.value = initialState()
            throw e
        } catch (e: Exception) {
            _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    fun delete() {
        modelFile.delete()
        partFile.delete()
        _state.value = ModelState.NotDownloaded
    }

    private fun verify(file: File) {
        try {
            val magic = ByteArray(4)
            file.inputStream().use { if (it.read(magic) != 4) throw IOException("Truncated file") }
            // GGUF magic is the ASCII "GGUF".
            if (!(magic[0] == 'G'.code.toByte() && magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() && magic[3] == 'F'.code.toByte())
            ) {
                throw IOException("Not a GGUF model file")
            }
            if (expectedSha256 != null) {
                val actual = sha256(file)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("Checksum mismatch")
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
