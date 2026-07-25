package com.vocatim.app.data.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptPromptTest {

    @Test
    fun `seed for indonesian is punctuated`() {
        val seed = TranscriptPrompt.seedFor("id")
        assertTrue("expected a seed for id", seed != null)
        // The whole point is priming punctuation: without these the seed
        // teaches whisper nothing about sentence shape.
        assertTrue("seed should contain a comma", seed!!.contains(","))
        assertTrue("seed should contain a full stop", seed.contains("."))
    }

    @Test
    fun `auto language has no seed`() {
        assertNull(TranscriptPrompt.seedFor("auto"))
    }

    @Test
    fun `null language has no seed`() {
        assertNull(TranscriptPrompt.seedFor(null))
    }

    @Test
    fun `unknown language falls back to no seed rather than wrong language`() {
        assertNull(TranscriptPrompt.seedFor("xx"))
    }

    @Test
    fun `locale tags resolve to the base language`() {
        assertEquals(TranscriptPrompt.seedFor("id"), TranscriptPrompt.seedFor("id-ID"))
        assertEquals(TranscriptPrompt.seedFor("en"), TranscriptPrompt.seedFor("en_US"))
    }

    @Test
    fun `language code is case insensitive`() {
        assertEquals(TranscriptPrompt.seedFor("id"), TranscriptPrompt.seedFor("ID"))
    }

    @Test
    fun `build returns null when there is nothing to send`() {
        assertNull(TranscriptPrompt.build(language = "auto", customVocab = ""))
        assertNull(TranscriptPrompt.build(language = null, customVocab = "   "))
    }

    @Test
    fun `build keeps custom vocab when the language has no seed`() {
        assertEquals(
            "Vocatim, Fareza",
            TranscriptPrompt.build(language = "auto", customVocab = "  Vocatim, Fareza  "),
        )
    }

    @Test
    fun `build returns the bare seed when no vocab is set`() {
        assertEquals(TranscriptPrompt.seedFor("id"), TranscriptPrompt.build("id", ""))
    }

    @Test
    fun `build puts vocab first so the seed sits closest to the output`() {
        val prompt = TranscriptPrompt.build("id", "Vocatim")!!
        assertTrue("vocab should come first", prompt.startsWith("Vocatim"))
        assertTrue("seed should be appended", prompt.endsWith(TranscriptPrompt.seedFor("id")!!))
    }

    @Test
    fun `every seed carries sentence punctuation`() {
        // A seed without terminal punctuation would prime the wrong style,
        // which is worse than sending no prompt at all.
        listOf("id", "ms", "en", "es", "fr", "de", "it", "pt", "nl", "ru", "tr", "vi", "ja", "ko", "zh")
            .forEach { code ->
                val seed = TranscriptPrompt.seedFor(code)
                assertTrue("missing seed for $code", seed != null)
                val terminal = seed!!.trimEnd().last()
                assertTrue(
                    "seed for $code should end in sentence punctuation, got '$terminal'",
                    terminal in setOf('.', '。', '۔', '।'),
                )
            }
    }
}
