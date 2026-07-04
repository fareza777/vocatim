package com.vocatim.app.data.audio

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WavFileWriterTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `written file round-trips through WavStreamReader`() {
        val file = File(tempDir.root, "out.wav")
        val samples = ShortArray(16_000) { (it % 1000).toShort() }
        WavFileWriter(file).use { writer ->
            writer.write(samples)
            writer.write(samples, count = 8_000)
        }

        WavStreamReader(file).use { reader ->
            assertEquals(24_000, reader.totalSamples)
            assertEquals(1500, reader.durationMs)
            val readBack = reader.read(0, 4)
            assertEquals(0f, readBack[0], 1e-4f)
            assertEquals(1 / 32768f, readBack[1], 1e-6f)
        }
    }

    @Test
    fun `written file decodes with WavDecoder`() {
        val file = File(tempDir.root, "out.wav")
        WavFileWriter(file).use { it.write(ShortArray(1600) { 100 }) }

        val decoded = WavDecoder.decode(file.readBytes())
        assertEquals(1600, decoded.samples.size)
        assertEquals(100L, decoded.durationMs)
    }

    @Test
    fun `unfinalized file is still readable from its length`() {
        // Simulates a crash: header says dataSize=0 because close() never ran.
        val file = File(tempDir.root, "crash.wav")
        val writer = WavFileWriter(file)
        writer.write(ShortArray(32_000) { 7 })
        // No close: header still has placeholder sizes.

        WavStreamReader(file).use { reader ->
            assertEquals(32_000, reader.totalSamples)
            assertEquals(2_000, reader.durationMs)
        }
        writer.close()
    }

    @Test
    fun `reader clamps reads past the end`() {
        val file = File(tempDir.root, "clamp.wav")
        WavFileWriter(file).use { it.write(ShortArray(100)) }

        WavStreamReader(file).use { reader ->
            assertEquals(50, reader.read(50, 100).size)
            assertEquals(0, reader.read(100, 10).size)
        }
    }
}
