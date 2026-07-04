package com.vocatim.app.data.transcribe

/** Builds a human-friendly title from the opening words of a transcript. */
object TitleGenerator {
    private const val MAX_LENGTH = 44
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':', ' ')

    fun fromText(text: String): String? {
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return null
        if (cleaned.length <= MAX_LENGTH) {
            return cleaned.trimEnd(*TRAILING_PUNCTUATION).ifEmpty { null }
        }
        val cut = cleaned.take(MAX_LENGTH + 1)
        val lastSpace = cut.lastIndexOf(' ')
        // Cut at a word boundary unless it would leave a stub.
        val base = if (lastSpace > MAX_LENGTH / 2) cut.take(lastSpace) else cut.take(MAX_LENGTH)
        val trimmed = base.trimEnd(*TRAILING_PUNCTUATION)
        return if (trimmed.isEmpty()) null else "$trimmed…"
    }
}
