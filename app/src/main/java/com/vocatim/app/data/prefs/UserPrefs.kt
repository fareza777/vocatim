package com.vocatim.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vocatim.app.data.model.WhisperModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class UserSettings(
    /** Whisper model for whisper-specific paths; TINY when a non-whisper
     *  engine (e.g. Parakeet) is selected — check [selectedModelId] first. */
    val model: WhisperModel,
    /** Raw selected engine id: a WhisperModel id or ParakeetModel.ID. */
    val selectedModelId: String,
    val language: String,
    /** Whisper's built-in translate-to-English mode. */
    val translate: Boolean,
    /** 0 = automatic (high-performance core count). */
    val threads: Int,
    /** Require biometric/device credential to open the app. */
    val appLock: Boolean,
    /** Apply FLAG_SECURE: block screenshots and recents preview. */
    val blockScreenshots: Boolean,
    /** "system", "light", or "dark". */
    val themeMode: String,
    /** Multiplier for the transcript reading text size. */
    val textScale: Float,
    val onboardingDone: Boolean,
    /** One-time spotlight tour of the Home controls has been shown. */
    val homeTourDone: Boolean,
    /** Comma/space-separated terms that bias Whisper spelling (initial_prompt). */
    val customVocab: String,
    /** Beam search instead of greedy: more accurate, slower. */
    val highAccuracy: Boolean,
    /** Silero VAD pre-filter: skips silence, curbs hallucinated filler. */
    val vadEnabled: Boolean,
    /** Run the AI summary automatically once transcription finishes (Pro). */
    val autoSummarize: Boolean,
    /** Minutes format: general, one_on_one, interview, or custom. */
    val minutesTemplate: String,
    /** User-written minutes prompt, used when [minutesTemplate] is custom. */
    val customMinutesPrompt: String,
    /** Convert finished recordings from WAV to M4A to save ~85% storage. */
    val compressAudio: Boolean,
    /** SAF tree URI for weekly automatic backups; empty = disabled. */
    val autoBackupUri: String,
    /** Backup password, encrypted with an Android Keystore key. */
    val autoBackupPw: String,
    /** Epoch ms of the last successful automatic backup. */
    val autoBackupLast: Long,
    /** Selected on-device summarization model id (SummaryModel.id). */
    val summaryModel: String,
    /** Accent color key: violet, teal, gold, blue, or rose. */
    val accent: String,
    /** Surface palette key: linen, pearl, orchid, sage, sand, or ocean. */
    val surfaceStyle: String,
    /** Detail-page sections the user last left open (sticky preference). */
    val detailExpanded: Set<String>,
)

/** User settings applied to new recordings and imports. */
class UserPrefs(private val context: Context) {

    val settings: Flow<UserSettings> = context.prefsDataStore.data.map { prefs ->
        UserSettings(
            model = WhisperModel.fromId(prefs[MODEL_KEY] ?: "") ?: WhisperModel.TINY,
            selectedModelId = prefs[MODEL_KEY] ?: WhisperModel.TINY.id,
            language = prefs[LANGUAGE_KEY] ?: "auto",
            translate = prefs[TRANSLATE_KEY] ?: false,
            threads = prefs[THREADS_KEY] ?: 0,
            appLock = prefs[APP_LOCK_KEY] ?: false,
            blockScreenshots = prefs[BLOCK_SCREENSHOTS_KEY] ?: false,
            // Default identity is the soft light theme; dark stays selectable.
            themeMode = prefs[THEME_MODE_KEY] ?: THEME_LIGHT,
            textScale = prefs[TEXT_SCALE_KEY] ?: 1.0f,
            onboardingDone = prefs[ONBOARDING_KEY] ?: false,
            homeTourDone = prefs[HOME_TOUR_KEY] ?: false,
            customVocab = prefs[CUSTOM_VOCAB_KEY] ?: "",
            highAccuracy = prefs[HIGH_ACCURACY_KEY] ?: false,
            vadEnabled = prefs[VAD_ENABLED_KEY] ?: true,
            autoSummarize = prefs[AUTO_SUMMARIZE_KEY] ?: false,
            minutesTemplate = prefs[MINUTES_TEMPLATE_KEY] ?: "general",
            customMinutesPrompt = prefs[CUSTOM_MINUTES_PROMPT_KEY] ?: "",
            compressAudio = prefs[COMPRESS_AUDIO_KEY] ?: false,
            autoBackupUri = prefs[AUTO_BACKUP_URI_KEY] ?: "",
            autoBackupPw = prefs[AUTO_BACKUP_PW_KEY] ?: "",
            autoBackupLast = prefs[AUTO_BACKUP_LAST_KEY] ?: 0L,
            summaryModel = prefs[SUMMARY_MODEL_KEY] ?: "qwen2.5-1.5b",
            accent = prefs[ACCENT_KEY] ?: "violet",
            surfaceStyle = prefs[SURFACE_STYLE_KEY] ?: "linen",
            detailExpanded = prefs[DETAIL_EXPANDED_KEY] ?: DEFAULT_DETAIL_EXPANDED,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun model(): WhisperModel = current().model

    suspend fun language(): String = current().language

    suspend fun setModel(model: WhisperModel) {
        context.prefsDataStore.edit { it[MODEL_KEY] = model.id }
    }

    /** Selects an engine by raw id (whisper ids or ParakeetModel.ID). */
    suspend fun setModelId(id: String) {
        context.prefsDataStore.edit { it[MODEL_KEY] = id }
    }

    suspend fun setLanguage(code: String) {
        context.prefsDataStore.edit { it[LANGUAGE_KEY] = code }
    }

    suspend fun setTranslate(enabled: Boolean) {
        context.prefsDataStore.edit { it[TRANSLATE_KEY] = enabled }
    }

    suspend fun setThreads(threads: Int) {
        context.prefsDataStore.edit { it[THREADS_KEY] = threads.coerceIn(0, 8) }
    }

    suspend fun setAppLock(enabled: Boolean) {
        context.prefsDataStore.edit { it[APP_LOCK_KEY] = enabled }
    }

    suspend fun setBlockScreenshots(enabled: Boolean) {
        context.prefsDataStore.edit { it[BLOCK_SCREENSHOTS_KEY] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.prefsDataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    suspend fun setTextScale(scale: Float) {
        context.prefsDataStore.edit { it[TEXT_SCALE_KEY] = scale }
    }

    suspend fun setOnboardingDone() {
        context.prefsDataStore.edit { it[ONBOARDING_KEY] = true }
    }

    suspend fun setHomeTourDone() {
        context.prefsDataStore.edit { it[HOME_TOUR_KEY] = true }
    }

    suspend fun setCustomVocab(text: String) {
        context.prefsDataStore.edit { it[CUSTOM_VOCAB_KEY] = text.take(400) }
    }

    suspend fun setHighAccuracy(enabled: Boolean) {
        context.prefsDataStore.edit { it[HIGH_ACCURACY_KEY] = enabled }
    }

    suspend fun setVadEnabled(enabled: Boolean) {
        context.prefsDataStore.edit { it[VAD_ENABLED_KEY] = enabled }
    }

    suspend fun setAutoSummarize(enabled: Boolean) {
        context.prefsDataStore.edit { it[AUTO_SUMMARIZE_KEY] = enabled }
    }

    suspend fun setMinutesTemplate(template: String) {
        context.prefsDataStore.edit { it[MINUTES_TEMPLATE_KEY] = template }
    }

    suspend fun setCustomMinutesPrompt(prompt: String) {
        context.prefsDataStore.edit { it[CUSTOM_MINUTES_PROMPT_KEY] = prompt.take(1000) }
    }

    suspend fun setCompressAudio(enabled: Boolean) {
        context.prefsDataStore.edit { it[COMPRESS_AUDIO_KEY] = enabled }
    }

    suspend fun setAutoBackup(uri: String, encryptedPw: String) {
        context.prefsDataStore.edit {
            it[AUTO_BACKUP_URI_KEY] = uri
            it[AUTO_BACKUP_PW_KEY] = encryptedPw
        }
    }

    suspend fun clearAutoBackup() {
        context.prefsDataStore.edit {
            it[AUTO_BACKUP_URI_KEY] = ""
            it[AUTO_BACKUP_PW_KEY] = ""
        }
    }

    suspend fun setAutoBackupLast(ts: Long) {
        context.prefsDataStore.edit { it[AUTO_BACKUP_LAST_KEY] = ts }
    }

    suspend fun setSummaryModel(id: String) {
        context.prefsDataStore.edit { it[SUMMARY_MODEL_KEY] = id }
    }

    suspend fun setAccent(key: String) {
        context.prefsDataStore.edit { it[ACCENT_KEY] = key }
    }

    suspend fun setSurfaceStyle(key: String) {
        context.prefsDataStore.edit { it[SURFACE_STYLE_KEY] = key }
    }

    /** Flips one detail section open/closed and remembers it for next time. */
    suspend fun toggleDetailSection(key: String) {
        context.prefsDataStore.edit { prefs ->
            val current = prefs[DETAIL_EXPANDED_KEY] ?: DEFAULT_DETAIL_EXPANDED
            prefs[DETAIL_EXPANDED_KEY] =
                if (key in current) current - key else current + key
        }
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        private val MODEL_KEY = stringPreferencesKey("default_model")
        private val LANGUAGE_KEY = stringPreferencesKey("default_language")
        private val TRANSLATE_KEY = booleanPreferencesKey("translate_to_english")
        private val THREADS_KEY = intPreferencesKey("num_threads")
        private val APP_LOCK_KEY = booleanPreferencesKey("app_lock")
        private val BLOCK_SCREENSHOTS_KEY = booleanPreferencesKey("block_screenshots")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val TEXT_SCALE_KEY = androidx.datastore.preferences.core.floatPreferencesKey("text_scale")
        private val ONBOARDING_KEY = booleanPreferencesKey("onboarding_done")
        private val HOME_TOUR_KEY = booleanPreferencesKey("home_tour_done")
        private val CUSTOM_VOCAB_KEY = stringPreferencesKey("custom_vocab")
        private val HIGH_ACCURACY_KEY = booleanPreferencesKey("high_accuracy")
        private val VAD_ENABLED_KEY = booleanPreferencesKey("vad_enabled")
        private val AUTO_SUMMARIZE_KEY = booleanPreferencesKey("auto_summarize")
        private val MINUTES_TEMPLATE_KEY = stringPreferencesKey("minutes_template")
        private val CUSTOM_MINUTES_PROMPT_KEY = stringPreferencesKey("custom_minutes_prompt")
        private val COMPRESS_AUDIO_KEY = booleanPreferencesKey("compress_audio")
        private val AUTO_BACKUP_URI_KEY = stringPreferencesKey("auto_backup_uri")
        private val AUTO_BACKUP_PW_KEY = stringPreferencesKey("auto_backup_pw")
        private val AUTO_BACKUP_LAST_KEY =
            androidx.datastore.preferences.core.longPreferencesKey("auto_backup_last")
        private val SUMMARY_MODEL_KEY = stringPreferencesKey("summary_model")
        private val ACCENT_KEY = stringPreferencesKey("accent")
        private val SURFACE_STYLE_KEY = stringPreferencesKey("surface_style")
        private val DETAIL_EXPANDED_KEY =
            androidx.datastore.preferences.core.stringSetPreferencesKey("detail_expanded")

        /** First-run default: the transcript and its audio open, rest closed. */
        val DEFAULT_DETAIL_EXPANDED = setOf("transcript", "audio")
    }
}
