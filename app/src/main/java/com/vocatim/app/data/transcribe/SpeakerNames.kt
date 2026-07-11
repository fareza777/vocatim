package com.vocatim.app.data.transcribe

import org.json.JSONObject

/** Codec for the per-transcript speaker display names, JSON {"1":"Budi"}. */
object SpeakerNames {
    fun decode(json: String?): Map<Int, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    key.toIntOrNull()?.let { put(it, obj.getString(key)) }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encode(names: Map<Int, String>): String? {
        val cleaned = names.filterValues { it.isNotBlank() }
        if (cleaned.isEmpty()) return null
        return JSONObject().apply {
            cleaned.forEach { (index, name) -> put(index.toString(), name.trim()) }
        }.toString()
    }
}
