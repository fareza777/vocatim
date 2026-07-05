package com.vocatim.app.data.transcribe

import java.util.Locale

/**
 * Extractive "key points": scores sentences by the frequency of the words
 * they contain (TF, stopword-filtered) and returns the top ones in original
 * order. No model download, instant, fully offline.
 */
object KeyPointsExtractor {

    fun extract(text: String, maxPoints: Int = 5): List<String> {
        val sentences = splitSentences(text)
        if (sentences.size <= maxPoints) return sentences

        val frequency = HashMap<String, Int>()
        val sentenceWords = sentences.map { sentence ->
            tokenize(sentence).also { words ->
                words.forEach { frequency[it] = (frequency[it] ?: 0) + 1 }
            }
        }

        val scored = sentences.indices.map { i ->
            val words = sentenceWords[i]
            // Normalized by length so long ramblers don't always win.
            val score = if (words.isEmpty()) 0.0
            else words.sumOf { (frequency[it] ?: 0).toDouble() } / (words.size + 3)
            i to score
        }

        return scored
            .sortedByDescending { it.second }
            .take(maxPoints)
            .map { it.first }
            .sorted()
            .map { sentences[it] }
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length >= MIN_SENTENCE_LENGTH }

    private fun tokenize(sentence: String): List<String> =
        sentence.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 && it !in STOPWORDS }

    private const val MIN_SENTENCE_LENGTH = 20

    // Indonesian + English high-frequency words that carry no topical signal.
    private val STOPWORDS = setOf(
        "yang", "dan", "ini", "itu", "dengan", "untuk", "dari", "pada", "juga",
        "akan", "ada", "atau", "dalam", "tidak", "bisa", "kita", "kami", "saya",
        "anda", "dia", "mereka", "sudah", "belum", "adalah", "karena", "seperti",
        "jadi", "kalau", "tapi", "tetapi", "lebih", "saat", "ketika", "namun",
        "the", "and", "that", "this", "with", "for", "from", "was", "were",
        "are", "have", "has", "had", "not", "can", "will", "would", "there",
        "their", "they", "you", "your", "but", "about", "into", "than", "then",
        "when", "what", "which", "who", "how", "all", "also", "been", "its",
    )
}
