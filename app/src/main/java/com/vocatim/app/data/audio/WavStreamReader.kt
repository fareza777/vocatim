package com.vocatim.app.data.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Random-access reader for the app's own 16kHz mono PCM16 WAV files.
 * Reads sample ranges without loading the whole file — required for
 * multi-hour recordings. The data length is derived from the actual file
 * size, so files whose header was never finalized (crash) still read fine.
 */
class WavStreamReader(file: File) : Closeable {
    private val raf = RandomAccessFile(file, "r")
    private val dataOffset: Long
    val totalSamples: Long

    init {
        val header = ByteArray(HEADER_PROBE_SIZE)
        val read = raf.read(header)
        if (read < 44) throw WavDecodeException("File too small to be a WAV")
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.getInt(0) != 0x46464952 || buffer.getInt(8) != 0x45564157) {
            throw WavDecodeException("Not a WAV file")
        }

        // Walk chunks within the probed header to find fmt and data.
        var fmtOk = false
        var offset = -1L
        var pos = 12
        while (pos + 8 <= read) {
            val chunkId = buffer.getInt(pos)
            val chunkSize = buffer.getInt(pos + 4)
            when (chunkId) {
                0x20746d66 -> { // "fmt "
                    val format = buffer.getShort(pos + 8).toInt()
                    val channels = buffer.getShort(pos + 10).toInt()
                    val rate = buffer.getInt(pos + 12)
                    val bits = buffer.getShort(pos + 22).toInt()
                    if (format != 1 || channels != 1 || bits != 16 ||
                        rate != WavDecoder.WHISPER_SAMPLE_RATE
                    ) {
                        throw WavDecodeException(
                            "Expected 16kHz mono PCM16, got fmt=$format ch=$channels rate=$rate bits=$bits"
                        )
                    }
                    fmtOk = true
                }
                0x61746164 -> { // "data"
                    offset = pos + 8L
                }
            }
            if (offset >= 0 && fmtOk) break
            pos += 8 + chunkSize + (chunkSize and 1)
        }
        if (!fmtOk || offset < 0) throw WavDecodeException("WAV missing fmt/data chunk")

        dataOffset = offset
        totalSamples = (raf.length() - dataOffset) / 2
    }

    val durationMs: Long
        get() = totalSamples * 1000L / WavDecoder.WHISPER_SAMPLE_RATE

    /** Reads [count] samples starting at [startSample] as floats in [-1, 1]. */
    fun read(startSample: Long, count: Int): FloatArray {
        val actualCount = minOf(count.toLong(), totalSamples - startSample).toInt()
        if (actualCount <= 0) return FloatArray(0)
        val bytes = ByteArray(actualCount * 2)
        raf.seek(dataOffset + startSample * 2)
        raf.readFully(bytes)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(actualCount) { buffer.getShort(it * 2) / 32768f }
    }

    override fun close() {
        raf.close()
    }

    private companion object {
        // Enough to cover RIFF + fmt + a few metadata chunks before data.
        const val HEADER_PROBE_SIZE = 512
    }
}
