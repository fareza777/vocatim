package com.vocatim.app.data.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Re-encodes a finished 16kHz mono PCM16 WAV recording as M4A/AAC
 * (~15 MB/hour instead of ~110), for long-term storage.
 */
object AudioCompressor {
    private const val SAMPLE_RATE = WavDecoder.WHISPER_SAMPLE_RATE
    private const val BIT_RATE = 48_000
    private const val CHUNK_SAMPLES = 4096
    private const val TIMEOUT_US = 10_000L

    /** @return true when [out] was fully written. */
    fun compressWavToM4a(wav: File, out: File): Boolean = runCatching {
        WavStreamReader(wav).use { reader ->
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1
            ).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var trackIndex = -1
            var muxerStarted = false
            var inputDone = false
            var samplePos = 0L
            val total = reader.totalSamples
            val info = MediaCodec.BufferInfo()

            try {
                while (true) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex) ?: continue
                            val remaining = (total - samplePos).toInt()
                            if (remaining <= 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                val count = minOf(CHUNK_SAMPLES, remaining)
                                val samples = reader.read(samplePos, count)
                                val bytes = ByteBuffer.allocate(samples.size * 2)
                                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                samples.forEach { f ->
                                    bytes.putShort(
                                        (f.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                                    )
                                }
                                bytes.flip()
                                inBuf.clear()
                                inBuf.put(bytes)
                                val ptsUs = samplePos * 1_000_000L / SAMPLE_RATE
                                codec.queueInputBuffer(inIndex, 0, samples.size * 2, ptsUs, 0)
                                samplePos += count
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIndex >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIndex)
                            if (outBuf != null && info.size > 0 && muxerStarted &&
                                (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                muxer.writeSampleData(trackIndex, outBuf, info)
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
                if (muxerStarted) runCatching { muxer.stop() }
                muxer.release()
            }
        }
        out.exists() && out.length() > 0
    }.getOrDefault(false).also { ok ->
        if (!ok) out.delete()
    }
}
