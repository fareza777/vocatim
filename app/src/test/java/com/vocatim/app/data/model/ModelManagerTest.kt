package com.vocatim.app.data.model

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.MessageDigest

class ModelManagerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var manager: ModelManager

    // Valid ggml payload: magic "lmgg" (0x67676d6c little-endian) + filler.
    private val validModelBytes =
        byteArrayOf(0x6c, 0x6d, 0x67, 0x67) + ByteArray(1020) { (it % 251).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        manager = ModelManager(
            modelsDir = tempDir.newFolder("models"),
            client = OkHttpClient(),
            urlResolver = { server.url("/models/${it.fileName}").toString() },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `downloads model and reports progress`() = runTest {
        server.enqueue(bodyResponse(validModelBytes))

        manager.download(WhisperModel.TINY)

        assertEquals(ModelState.Downloaded, manager.state(WhisperModel.TINY).value)
        assertTrue(manager.modelFile(WhisperModel.TINY).exists())
        assertEquals(validModelBytes.size.toLong(), manager.modelFile(WhisperModel.TINY).length())
    }

    @Test
    fun `resumes partial download with range request`() = runTest {
        // Simulate an interrupted download: half the file already on disk.
        val half = validModelBytes.size / 2
        val partFile = manager.modelFile(WhisperModel.TINY).resolveSibling("ggml-tiny.bin.part")
        partFile.writeBytes(validModelBytes.copyOfRange(0, half))

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                    ?: return bodyResponse(validModelBytes)
                assertEquals("bytes=$half-", range)
                val remaining = validModelBytes.copyOfRange(half, validModelBytes.size)
                return bodyResponse(remaining).setResponseCode(206)
            }
        }

        manager.download(WhisperModel.TINY)

        assertEquals(ModelState.Downloaded, manager.state(WhisperModel.TINY).value)
        assertTrue(
            manager.modelFile(WhisperModel.TINY).readBytes().contentEquals(validModelBytes)
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `restarts cleanly when server ignores range`() = runTest {
        val partFile = manager.modelFile(WhisperModel.TINY).resolveSibling("ggml-tiny.bin.part")
        partFile.writeBytes(ByteArray(100) { 9 })

        // Plain 200: server sends the whole file from the beginning.
        server.enqueue(bodyResponse(validModelBytes))

        manager.download(WhisperModel.TINY)

        assertEquals(ModelState.Downloaded, manager.state(WhisperModel.TINY).value)
        assertTrue(
            manager.modelFile(WhisperModel.TINY).readBytes().contentEquals(validModelBytes)
        )
    }

    @Test
    fun `rejects file without ggml magic and deletes it`() = runTest {
        val badBytes = ByteArray(1024) { 0x42 }
        server.enqueue(bodyResponse(badBytes))

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { manager.download(WhisperModel.TINY) }
        }

        assertTrue(manager.state(WhisperModel.TINY).value is ModelState.Failed)
        assertFalse(manager.modelFile(WhisperModel.TINY).exists())
    }

    @Test
    fun `verifies sha256 from etag when present`() = runTest {
        val sha = MessageDigest.getInstance("SHA-256").digest(validModelBytes)
            .joinToString("") { "%02x".format(it) }
        server.enqueue(bodyResponse(validModelBytes).setHeader("ETag", "\"$sha\""))

        manager.download(WhisperModel.TINY)

        assertEquals(ModelState.Downloaded, manager.state(WhisperModel.TINY).value)
    }

    @Test
    fun `fails on sha256 mismatch and deletes file`() = runTest {
        val wrongSha = "0".repeat(64)
        server.enqueue(bodyResponse(validModelBytes).setHeader("ETag", "\"$wrongSha\""))

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { manager.download(WhisperModel.TINY) }
        }

        assertTrue(manager.state(WhisperModel.TINY).value is ModelState.Failed)
        assertFalse(manager.modelFile(WhisperModel.TINY).exists())
    }

    @Test
    fun `fails on http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { manager.download(WhisperModel.TINY) }
        }

        val state = manager.state(WhisperModel.TINY).value
        assertTrue(state is ModelState.Failed)
        assertTrue((state as ModelState.Failed).message.contains("500"))
    }

    @Test
    fun `delete removes model and resets state`() = runTest {
        server.enqueue(bodyResponse(validModelBytes))
        manager.download(WhisperModel.TINY)

        manager.delete(WhisperModel.TINY)

        assertEquals(ModelState.NotDownloaded, manager.state(WhisperModel.TINY).value)
        assertFalse(manager.modelFile(WhisperModel.TINY).exists())
    }

    @Test
    fun `download is noop when file already exists`() = runTest {
        server.enqueue(bodyResponse(validModelBytes))
        manager.download(WhisperModel.TINY)

        // No second response enqueued: a network hit would fail the test.
        manager.download(WhisperModel.TINY)

        assertEquals(ModelState.Downloaded, manager.state(WhisperModel.TINY).value)
        assertEquals(1, server.requestCount)
    }

    private fun bodyResponse(bytes: ByteArray): MockResponse =
        MockResponse().setBody(Buffer().apply { write(bytes) })
}
