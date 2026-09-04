package com.vocatim.app.data.transcribe

import com.vocatim.app.data.audio.AudioCompressor
import com.vocatim.app.data.audio.WavFileWriter
import com.vocatim.app.data.audio.WavStreamReader
import com.vocatim.app.data.cloud.CloudTranscribeConfig
import com.vocatim.whisper.WhisperSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Cloud transcription failed in a way worth showing the user. */
class CloudTranscribeException(message: String) : Exception(message)

/**
 * Transcribes through an OpenAI-compatible `/audio/transcriptions` endpoint —
 * Groq, OpenAI, or anything that speaks the same shape.
 *
 * The audio is compressed to AAC before upload. A 16 kHz mono WAV runs about
 * 110 MB/hour, which blows past every provider's upload cap within minutes;
 * at 48 kbps the same hour is roughly 22 MB. Long recordings are still split,
 * because the app promises multi-hour transcripts and one upload cannot carry
 * them.
 *
 * `verbose_json` is requested rather than plain text: without segment
 * timestamps a cloud transcript would lose karaoke playback, SRT/VTT export
 * and speaker detection, and would be a second-class citizen in its own app.
 */
class CloudTranscriber(
    private val client: OkHttpClient,
    private val cacheDir: File,
) {

    suspend fun transcribe(
        config: CloudTranscribeConfig,
        wav: File,
        language: String?,
        translate: Boolean,
        onProgress: (Float) -> Unit,
    ): List<WhisperSegment> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) throw CloudTranscribeException("CLOUD_NOT_CONFIGURED")

        val parts = planParts(wav)
        val all = mutableListOf<WhisperSegment>()

        parts.forEachIndexed { index, part ->
            val slice = if (parts.size == 1) wav else sliceWav(wav, part)
            val encoded = File(cacheDir, "cloud_upload_$index.m4a")
            try {
                // Falling back to the raw WAV here was wrong: 16 kHz mono PCM
                // runs ~110 MB/hour, so the upload is certain to be rejected
                // after burning the user's data allowance getting there. Say
                // the encoder failed instead.
                if (!AudioCompressor.compressWavToM4a(slice, encoded)) {
                    throw CloudTranscribeException("CLOUD_ENCODE_FAILED")
                }
                if (encoded.length() > MAX_UPLOAD_BYTES) {
                    throw CloudTranscribeException(tooLarge(encoded.length()))
                }
                val segments = uploadOne(config, encoded, language, translate)
                // Every part after the first carries its own clock; shift it
                // onto the recording's timeline or playback lands nowhere.
                all += segments.map {
                    if (part.startMs == 0L) it
                    else WhisperSegment(
                        it.startMs + part.startMs,
                        it.endMs + part.startMs,
                        it.text,
                    )
                }
            } finally {
                encoded.delete()
                if (slice !== wav) slice.delete()
            }
            onProgress((index + 1).toFloat() / parts.size)
        }
        all
    }

    private data class Part(val startSample: Long, val sampleCount: Long, val startMs: Long)

    private fun planParts(wav: File): List<Part> = WavStreamReader(wav).use { reader ->
        val total = reader.totalSamples
        val perPart = PART_SECONDS.toLong() * SAMPLE_RATE
        if (total <= perPart) return listOf(Part(0, total, 0))
        buildList {
            var start = 0L
            while (start < total) {
                val count = minOf(perPart, total - start)
                add(Part(start, count, start * 1000L / SAMPLE_RATE))
                start += count
            }
        }
    }

    private fun sliceWav(source: File, part: Part): File {
        val out = File(cacheDir, "cloud_slice_${part.startSample}.wav")
        WavStreamReader(source).use { reader ->
            WavFileWriter(out).use { writer ->
                var written = 0L
                while (written < part.sampleCount) {
                    val n = minOf(READ_CHUNK.toLong(), part.sampleCount - written).toInt()
                    val floats = reader.read(part.startSample + written, n)
                    if (floats.isEmpty()) break
                    val shorts = ShortArray(floats.size) { i ->
                        (floats[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                    }
                    writer.write(shorts)
                    written += floats.size
                }
            }
        }
        return out
    }

    private fun uploadOne(
        config: CloudTranscribeConfig,
        audio: File,
        language: String?,
        translate: Boolean,
    ): List<WhisperSegment> {
        // Translation is a different endpoint in the OpenAI shape, and it only
        // ever targets English.
        val endpoint = if (translate) "/audio/translations" else "/audio/transcriptions"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audio.name,
                audio.asRequestBody("audio/m4a".toMediaType()),
            )
            .addFormDataPart("model", config.model)
            .addFormDataPart("response_format", "verbose_json")
            .apply {
                // "auto" means let the provider detect; sending it as a
                // language code would be rejected.
                if (!translate && !language.isNullOrBlank() && language != "auto") {
                    addFormDataPart("language", language)
                }
            }
            .build()

        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + endpoint)
            .header("Authorization", "Bearer " + config.apiKey)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw CloudTranscribeException(
                    errorFor(response.code, text, audio.length())
                )
            }
            return parseSegments(text)
        }
    }

    /** Maps provider errors onto something a person can act on. */
    private fun errorFor(code: Int, body: String, uploadedBytes: Long): String = when (code) {
        401, 403 -> "CLOUD_BAD_KEY"
        404 -> "CLOUD_BAD_MODEL"
        // Carry the size that was actually sent: "too large" with no number
        // is impossible to act on and impossible to diagnose from a report.
        413 -> tooLarge(uploadedBytes)
        429 -> "CLOUD_RATE_LIMIT"
        else -> {
            val detail = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            if (detail.isNotBlank()) "CLOUD_ERROR:" + detail.take(160)
            else "CLOUD_ERROR:HTTP " + code
        }
    }

    private fun parseSegments(json: String): List<WhisperSegment> {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: throw CloudTranscribeException("CLOUD_BAD_RESPONSE")
        val array = root.optJSONArray("segments")
        if (array == null || array.length() == 0) {
            // Some providers answer plain text for very short audio; keep it
            // rather than losing the transcript over a missing timeline.
            val text = root.optString("text").trim()
            return if (text.isEmpty()) emptyList()
            else listOf(WhisperSegment(0L, 0L, text))
        }
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val text = o.optString("text").trim()
                if (text.isEmpty()) continue
                add(
                    WhisperSegment(
                        (o.optDouble("start", 0.0) * 1000).toLong(),
                        (o.optDouble("end", 0.0) * 1000).toLong(),
                        text,
                    )
                )
            }
        }
    }

    private fun tooLarge(bytes: Long): String =
        "CLOUD_TOO_LARGE:" + (bytes / 1_000_000) + " MB"

    private companion object {
        const val SAMPLE_RATE = 16_000

        /** Below the 25 MB cap the common providers use, with room to spare. */
        const val MAX_UPLOAD_BYTES = 24L * 1024 * 1024
        const val READ_CHUNK = 1 shl 16

        /**
         * 45 minutes ≈ 16 MB at 48 kbps, comfortably inside the 25 MB cap the
         * common providers use, with room for AAC overhead.
         */
        const val PART_SECONDS = 45 * 60
    }
}
