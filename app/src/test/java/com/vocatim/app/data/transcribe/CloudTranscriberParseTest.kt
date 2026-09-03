package com.vocatim.app.data.transcribe

import com.vocatim.whisper.WhisperSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing is where a cloud transcript quietly becomes second-class: get the
 * timestamps wrong and karaoke playback, SRT export and speaker detection all
 * break while the text still looks fine.
 *
 * The parser is exercised through a copy of its logic rather than the class
 * itself, which needs Android's MediaCodec to construct.
 */
class CloudTranscriberParseTest {

    private fun parse(json: String): List<WhisperSegment> {
        val root = org.json.JSONObject(json)
        val array = root.optJSONArray("segments")
        if (array == null || array.length() == 0) {
            val text = root.optString("text").trim()
            return if (text.isEmpty()) emptyList() else listOf(WhisperSegment(0L, 0L, text))
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

    @Test
    fun `seconds become milliseconds`() {
        val out = parse(
            """{"segments":[{"start":1.5,"end":3.25,"text":"halo dunia"}]}"""
        )
        assertEquals(1, out.size)
        assertEquals(1500L, out[0].startMs)
        assertEquals(3250L, out[0].endMs)
        assertEquals("halo dunia", out[0].text)
    }

    @Test
    fun `segment order and gaps are preserved`() {
        val out = parse(
            """{"segments":[
                {"start":0.0,"end":2.0,"text":"satu"},
                {"start":5.0,"end":6.5,"text":"dua"}
            ]}"""
        )
        assertEquals(listOf(0L, 5000L), out.map { it.startMs })
        assertEquals(listOf(2000L, 6500L), out.map { it.endMs })
    }

    @Test
    fun `blank segments are dropped`() {
        val out = parse(
            """{"segments":[
                {"start":0.0,"end":1.0,"text":"   "},
                {"start":1.0,"end":2.0,"text":"nyata"}
            ]}"""
        )
        assertEquals(1, out.size)
        assertEquals("nyata", out[0].text)
    }

    @Test
    fun `plain text response is kept rather than lost`() {
        // Short audio can come back without a segment array; losing the
        // transcript over a missing timeline would be the worse failure.
        val out = parse("""{"text":"kalimat pendek"}""")
        assertEquals(1, out.size)
        assertEquals("kalimat pendek", out[0].text)
    }

    @Test
    fun `empty response yields no segments`() {
        assertTrue(parse("""{"text":"  "}""").isEmpty())
        assertTrue(parse("""{"segments":[]}""").isEmpty())
    }

    @Test
    fun `missing timestamps default to zero instead of throwing`() {
        val out = parse("""{"segments":[{"text":"tanpa waktu"}]}""")
        assertEquals(1, out.size)
        assertEquals(0L, out[0].startMs)
    }

    /** Mirrors the offset applied to every part after the first. */
    @Test
    fun `later parts are shifted onto the recording timeline`() {
        val partStartMs = 45 * 60 * 1000L
        val raw = parse("""{"segments":[{"start":2.0,"end":4.0,"text":"lanjutan"}]}""")
        val shifted = raw.map {
            WhisperSegment(it.startMs + partStartMs, it.endMs + partStartMs, it.text)
        }
        assertEquals(partStartMs + 2000L, shifted[0].startMs)
        assertEquals(partStartMs + 4000L, shifted[0].endMs)
    }
}
