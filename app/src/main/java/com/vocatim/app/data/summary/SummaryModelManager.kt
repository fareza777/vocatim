package com.vocatim.app.data.summary

import com.vocatim.app.data.model.ModelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Downloads and manages the summarization GGUF models. Mirrors
 * [com.vocatim.app.data.model.ModelManager]: streams to a `.part` file,
 * resumes via HTTP Range, verifies the GGUF magic and the pinned SHA-256.
 */
class SummaryModelManager(
    private val modelsDir: File,
    private val client: OkHttpClient,
) {
    private val states: Map<SummaryModel, MutableStateFlow<ModelState>> =
        SummaryModel.entries.associateWith { MutableStateFlow(initialState(it)) }

    fun state(model: SummaryModel): StateFlow<ModelState> = states.getValue(model)

    fun modelFile(model: SummaryModel): File = File(modelsDir, model.fileName)
    private fun partFile(model: SummaryModel): File =
        File(modelsDir, model.fileName + ".part")

    fun isDownloaded(model: SummaryModel): Boolean = modelFile(model).exists()

    private fun initialState(model: SummaryModel): ModelState =
        if (modelFile(model).exists()) ModelState.Downloaded else ModelState.NotDownloaded

    suspend fun download(model: SummaryModel) = withContext(Dispatchers.IO) {
        val state = states.getValue(model)
        if (state.value is ModelState.Downloading) return@withContext
        val modelFile = modelFile(model)
        val partFile = partFile(model)
        if (modelFile.exists()) {
            state.value = ModelState.Downloaded
            return@withContext
        }
        try {
            if (!modelsDir.exists() && !modelsDir.mkdirs()) {
                throw IOException("Couldn't create models directory")
            }
            val already = if (partFile.exists()) partFile.length() else 0L
            state.value = ModelState.Downloading(already, 0L)

            val request = Request.Builder()
                .url(model.url)
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
                            state.value = ModelState.Downloading(written, total)
                        }
                    }
                }
                if (total > 0 && partFile.length() != total) {
                    throw IOException("Incomplete download")
                }
                verify(partFile, model.sha256)
            }
            if (!partFile.renameTo(modelFile)) throw IOException("Couldn't finalize model")
            state.value = ModelState.Downloaded
        } catch (e: CancellationException) {
            state.value = initialState(model)
            throw e
        } catch (e: Exception) {
            state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    fun delete(model: SummaryModel) {
        modelFile(model).delete()
        partFile(model).delete()
        states.getValue(model).value = ModelState.NotDownloaded
    }

    private fun verify(file: File, expectedSha256: String) {
        try {
            val magic = ByteArray(4)
            file.inputStream().use { if (it.read(magic) != 4) throw IOException("Truncated file") }
            // GGUF magic is the ASCII "GGUF".
            if (!(magic[0] == 'G'.code.toByte() && magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() && magic[3] == 'F'.code.toByte())
            ) {
                throw IOException("Not a GGUF model file")
            }
            val actual = sha256(file)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                throw IOException("Checksum mismatch")
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
