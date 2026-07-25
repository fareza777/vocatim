package com.vocatim.app.data.transcribe

/**
 * Builds whisper's `initial_prompt`.
 *
 * Whisper continues in the *style* of whatever text it is primed with. With
 * no prompt it tends to emit one long run-on line: little punctuation,
 * inconsistent capitalisation, no sentence breaks — readable enough in
 * English, but noticeably rough in Indonesian, where the transcript often has
 * to be summarised before it makes sense.
 *
 * Priming it with two well-punctuated sentences in the target language costs
 * a handful of tokens and no measurable time, and pushes the output towards
 * the same shape.
 *
 * Seeds are deliberately short and generic. A long prompt eats into whisper's
 * 224-token context, and any prompt can leak into the output on near-silent
 * audio, so there is no upside to making them wordy.
 */
object TranscriptPrompt {

    /**
     * One or two sentences per language, each carrying a comma, a full stop
     * and a capitalised opening — the three things the raw output most often
     * lacks. Only languages we can seed correctly are listed; an unknown
     * language falls back to no seed rather than priming with the wrong one.
     */
    private val SEEDS = mapOf(
        "id" to "Selamat datang, terima kasih sudah bergabung. Berikut adalah catatan rapat hari ini.",
        "ms" to "Selamat datang, terima kasih kerana hadir. Berikut ialah catatan mesyuarat hari ini.",
        "en" to "Welcome, and thanks for joining. Here are the notes from today's meeting.",
        "es" to "Bienvenidos, gracias por acompañarnos. Estas son las notas de la reunión de hoy.",
        "fr" to "Bienvenue, merci de nous rejoindre. Voici les notes de la réunion d'aujourd'hui.",
        "de" to "Willkommen, danke fürs Dabeisein. Hier sind die Notizen zum heutigen Meeting.",
        "it" to "Benvenuti, grazie per la partecipazione. Ecco gli appunti della riunione di oggi.",
        "pt" to "Bem-vindos, obrigado por participar. Estas são as notas da reunião de hoje.",
        "nl" to "Welkom, bedankt voor je deelname. Dit zijn de notities van de vergadering van vandaag.",
        "ru" to "Добро пожаловать, спасибо, что присоединились. Вот заметки сегодняшней встречи.",
        "tr" to "Hoş geldiniz, katıldığınız için teşekkürler. İşte bugünkü toplantının notları.",
        "vi" to "Chào mừng, cảm ơn bạn đã tham gia. Đây là ghi chú của cuộc họp hôm nay.",
        "th" to "ยินดีต้อนรับ ขอบคุณที่เข้าร่วม นี่คือบันทึกการประชุมของวันนี้",
        "ja" to "ようこそ、ご参加ありがとうございます。本日の会議の記録です。",
        "ko" to "환영합니다, 참여해 주셔서 감사합니다. 오늘 회의 기록입니다.",
        "zh" to "欢迎，感谢您的参与。以下是今天的会议记录。",
        "ar" to "مرحباً، شكراً لانضمامكم. هذه هي ملاحظات اجتماع اليوم.",
        "hi" to "स्वागत है, शामिल होने के लिए धन्यवाद। यह आज की बैठक के नोट्स हैं।",
    )

    /**
     * @param language whisper language code, or null when auto-detect has not
     *   resolved one yet. "auto" is treated the same as null.
     * @param customVocab user-supplied terms; kept first so the punctuated
     *   seed sits closest to the output, where it primes style most strongly.
     * @return the prompt, or null when there is nothing useful to send.
     */
    fun build(language: String?, customVocab: String = ""): String? {
        val vocab = customVocab.trim()
        val seed = seedFor(language)
        return when {
            seed == null && vocab.isEmpty() -> null
            seed == null -> vocab
            vocab.isEmpty() -> seed
            else -> "$vocab. $seed"
        }
    }

    /** Exposed for tests and callers that only want the style seed. */
    fun seedFor(language: String?): String? {
        val code = language?.trim()?.lowercase() ?: return null
        if (code.isEmpty() || code == "auto") return null
        // Accept "id-ID" / "en_US" style tags as well as bare codes.
        val base = code.substringBefore('-').substringBefore('_')
        return SEEDS[base]
    }
}
