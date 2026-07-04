package com.vocatim.app.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

class AudioImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Decodes any audio the device can play (mp3, m4a, ogg/opus, wav, audio
 * track of mp4, ...) into a 16kHz mono PCM16 WAV via MediaCodec.
 * Fully streaming: memory use is constant regardless of input length.
 */
class AudioImporter(private val context: Context) {

    data class ImportResult(val wavFile: File, val durationMs: Long)

    suspend fun import(
        uri: Uri,
        outFile: File,
        onProgress: (Float) -> Unit = {},
    ): ImportResult = withContext(Dispatchers.Default) {
        // Cloud providers (Drive, Gmail, ...) serve non-seekable streams that
        // MediaExtractor can't handle. Stage the source into a local temp
        // file first; openInputStream works for all providers.
        val staged = File(context.cacheDir, "import_staging_${System.currentTimeMillis()}")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var writer: WavFileWriter? = null
        try {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Content provider returned no stream")
            } catch (e: Exception) {
                throw AudioImportException(
                    "Couldn't read the selected file (cloud files need a connection)", e
                )
            }
            if (staged.length() == 0L) {
                throw AudioImportException("The selected file is empty")
            }

            try {
                extractor.setDataSource(staged.absolutePath)
            } catch (e: IOException) {
                throw AudioImportException("Couldn't open the selected file", e)
            }

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw AudioImportException("No audio track found in this file")

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs =
                if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION)
                else -1L

            codec = try {
                MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }
            } catch (e: Exception) {
                throw AudioImportException("This audio format ($mime) isn't supported by the device", e)
            }

            writer = WavFileWriter(outFile)
            decodeLoop(extractor, codec, writer, durationUs, onProgress)

            val durationMs = writer.durationMs
            writer.close()
            writer = null
            if (durationMs <= 0) throw AudioImportException("The file contains no audio data")
            ImportResult(outFile, durationMs)
        } catch (e: Exception) {
            runCatching { writer?.close() }
            writer = null
            outFile.delete()
            throw e
        } finally {
            runCatching { writer?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            staged.delete()
        }
    }

    private suspend fun decodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        writer: WavFileWriter,
        durationUs: Long,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        // Output PCM parameters may only be authoritative after the first
        // INFO_OUTPUT_FORMAT_CHANGED, so the resampler is (re)built there.
        var sampleRate = 0
        var channels = 0
        var pcmFloat = false
        var resampler: StreamingResampler? = null

        fun applyFormat(fmt: MediaFormat) {
            val newRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val newChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            pcmFloat = fmt.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                fmt.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            if (newRate != sampleRate) {
                sampleRate = newRate
                resampler = StreamingResampler(sampleRate)
            }
            channels = newChannels
        }

        while (!outputDone) {
            ensureActive()

            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(
                            inIndex, 0, sampleSize, extractor.sampleTime, 0
                        )
                        if (durationUs > 0) {
                            onProgress(
                                (extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f)
                            )
                        }
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    applyFormat(codec.outputFormat)

                outIndex >= 0 -> {
                    if (resampler == null) applyFormat(codec.outputFormat)
                    if (bufferInfo.size > 0) {
                        val outBuffer = codec.getOutputBuffer(outIndex)!!
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val mono = if (pcmFloat) {
                            val fb = outBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
                            mixdownFloats(fb.let { b -> FloatArray(b.remaining()).also(b::get) }, channels)
                        } else {
                            val sb = outBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                            mixdownShorts(ShortArray(sb.remaining()).also(sb::get), channels)
                        }

                        val resampled = resampler!!.process(mono)
                        writer.write(floatsToPcm16(resampled))
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }
        onProgress(1f)
    }

    private fun mixdownShorts(interleaved: ShortArray, channels: Int): FloatArray {
        if (channels <= 1) return FloatArray(interleaved.size) { interleaved[it] / 32768f }
        val frames = interleaved.size / channels
        return FloatArray(frames) { frame ->
            var sum = 0f
            for (c in 0 until channels) sum += interleaved[frame * channels + c] / 32768f
            sum / channels
        }
    }

    private fun mixdownFloats(interleaved: FloatArray, channels: Int): FloatArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        return FloatArray(frames) { frame ->
            var sum = 0f
            for (c in 0 until channels) sum += interleaved[frame * channels + c]
            sum / channels
        }
    }

    private fun floatsToPcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) {
            (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
