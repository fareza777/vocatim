package com.vocatim.app.data.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleGeneratorTest {

    @Test
    fun `short text becomes the title without trailing punctuation`() {
        assertEquals("Halo dunia", TitleGenerator.fromText("Halo dunia."))
    }

    @Test
    fun `long text is cut at a word boundary with ellipsis`() {
        val title = TitleGenerator.fromText(
            "Kisah Atlantis pertama kali tertulis sekitar tahun tiga ratus enam puluh sebelum Masehi"
        )!!
        assertTrue(title.endsWith("…"))
        assertTrue(title.length <= 45)
        // Never cuts mid-word.
        assertTrue(title.removeSuffix("…").last() != ' ')
    }

    @Test
    fun `collapses whitespace`() {
        assertEquals("Satu dua tiga", TitleGenerator.fromText("  Satu \n dua   tiga  "))
    }

    @Test
    fun `blank text yields null`() {
        assertNull(TitleGenerator.fromText("   "))
        assertNull(TitleGenerator.fromText(""))
    }
}
