package com.vocatim.app.data.cloud

/** Prompts for BYOK cloud jobs (summary + meeting minutes). */
object CloudPrompts {
    /** Frontier models handle huge context; still cap absurd inputs. */
    const val MAX_INPUT_CHARS = 300_000

    fun summarySystem(language: String): String = if (language == "id") {
        "Anda meringkas transkrip rapat dan catatan suara. Tulis ringkasan padat " +
            "dalam bahasa Indonesia berupa poin-poin: gagasan utama, keputusan, dan " +
            "action item. Setia pada isi transkrip; jangan mengarang."
    } else {
        "You summarize meeting and voice-note transcripts. Write a tight bullet-point " +
            "summary covering key ideas, decisions, and action items. Be faithful to " +
            "the transcript; do not invent facts. Reply in ${languageName(language)}."
    }

    const val TEMPLATE_GENERAL = "general"
    const val TEMPLATE_ONE_ON_ONE = "one_on_one"
    const val TEMPLATE_INTERVIEW = "interview"
    const val TEMPLATE_CUSTOM = "custom"

    /** Minutes prompt for the chosen [template]; custom uses [customPrompt]. */
    fun minutesSystem(
        language: String,
        template: String = TEMPLATE_GENERAL,
        customPrompt: String = "",
    ): String = when (template) {
        TEMPLATE_CUSTOM ->
            if (customPrompt.isNotBlank()) {
                customPrompt +
                    "\nWrite in ${languageName(language)}. Be faithful to the " +
                    "transcript; do not invent information."
            } else minutesSystem(language)
        TEMPLATE_ONE_ON_ONE ->
            "You are a professional note-taker for 1-on-1 meetings. Convert the " +
                "transcript into clean Markdown notes in ${languageName(language)}, " +
                "structured as: # 1-on-1 Notes, **Participants** (if identifiable), " +
                "## Updates & Wins, ## Challenges & Blockers, ## Feedback, " +
                "## Agreed Next Steps (- [ ] task — owner — deadline if mentioned). " +
                "Be faithful to the transcript; do not invent information."
        TEMPLATE_INTERVIEW ->
            "You are a professional interview note-taker. Convert the transcript " +
                "into clean Markdown notes in ${languageName(language)}, structured " +
                "as: # Interview Notes, **Interviewee & Role** (if identifiable), " +
                "**Overall Impression** (2-3 sentences), ## Key Questions & Answers, " +
                "## Strengths, ## Concerns, ## Follow-ups. Be faithful to the " +
                "transcript; do not invent information."
        else -> minutesSystem(language)
    }

    fun minutesSystem(language: String): String = if (language == "id") {
        "Anda adalah notulis profesional. Ubah transkrip rapat berikut menjadi " +
            "notulen rapat yang rapi dalam bahasa Indonesia, format Markdown, dengan " +
            "struktur:\n" +
            "# Notulen Rapat\n" +
            "**Topik:** (simpulkan dari isi)\n" +
            "**Ringkasan:** 2-3 kalimat.\n" +
            "## Poin Pembahasan\n(poin-poin utama, kelompokkan per topik)\n" +
            "## Keputusan\n(daftar keputusan yang diambil; tulis \"Tidak ada\" bila tidak ada)\n" +
            "## Action Item\n(format: - [ ] tugas — penanggung jawab bila disebut — tenggat bila disebut)\n" +
            "Setia pada isi; jangan menambah informasi yang tidak ada di transkrip. " +
            "Rapikan bahasa lisan menjadi bahasa tulis yang baik."
    } else {
        "You are a professional minute-taker. Convert the following meeting " +
            "transcript into clean meeting minutes in ${languageName(language)}, in " +
            "Markdown, structured as: # Meeting Minutes, **Topic**, **Summary** " +
            "(2-3 sentences), ## Discussion Points (grouped by topic), ## Decisions " +
            "(write \"None\" if none), ## Action Items (- [ ] task — owner if " +
            "mentioned — deadline if mentioned). Be faithful to the transcript; do " +
            "not invent information. Polish spoken language into clear writing."
    }

    /** Grounded Q&A over one transcript. */
    fun qaSystem(language: String): String =
        "You answer questions about a meeting/voice-note transcript the user " +
            "provides. Answer concisely in ${languageName(language)}, citing only " +
            "what the transcript actually says. If the transcript does not contain " +
            "the answer, say so plainly instead of guessing."

    /** Faithful translation of a transcript or minutes. */
    fun translateSystem(targetLanguage: String): String =
        "Translate the user's text into $targetLanguage. Preserve the meaning, " +
            "tone, structure, and any Markdown formatting. Output ONLY the " +
            "translation, no preamble."

    private fun languageName(code: String): String =
        java.util.Locale(code).getDisplayLanguage(java.util.Locale.ENGLISH)
            .ifBlank { "the same language as the transcript" }
}
