package com.vocatim.app.data.transcribe

import com.vocatim.whisper.WhisperWord
import org.json.JSONArray

/** Compact JSON codec for per-word timings stored on a segment row. */
object WordTimings {
    fun encode(words: List<WhisperWord>): String? {
        if (words.isEmpty()) return null
        val arr = JSONArray()
        words.forEach { w ->
            arr.put(JSONArray().put(w.startMs).put(w.endMs).put(w.text))
        }
        return arr.toString()
    }

    fun decode(json: String?): List<WhisperWord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val w = arr.getJSONArray(i)
                WhisperWord(w.getLong(0), w.getLong(1), w.getString(2))
            }
        }.getOrDefault(emptyList())
    }
}
