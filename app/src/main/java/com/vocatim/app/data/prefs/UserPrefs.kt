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
    val model: WhisperModel,
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
)

/** User settings applied to new recordings and imports. */
class UserPrefs(private val context: Context) {

    val settings: Flow<UserSettings> = context.prefsDataStore.data.map { prefs ->
        UserSettings(
            model = WhisperModel.fromId(prefs[MODEL_KEY] ?: "") ?: WhisperModel.TINY,
            language = prefs[LANGUAGE_KEY] ?: "auto",
            translate = prefs[TRANSLATE_KEY] ?: false,
            threads = prefs[THREADS_KEY] ?: 0,
            appLock = prefs[APP_LOCK_KEY] ?: false,
            blockScreenshots = prefs[BLOCK_SCREENSHOTS_KEY] ?: false,
            themeMode = prefs[THEME_MODE_KEY] ?: THEME_SYSTEM,
            textScale = prefs[TEXT_SCALE_KEY] ?: 1.0f,
            onboardingDone = prefs[ONBOARDING_KEY] ?: false,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun model(): WhisperModel = current().model

    suspend fun language(): String = current().language

    suspend fun setModel(model: WhisperModel) {
        context.prefsDataStore.edit { it[MODEL_KEY] = model.id }
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
    }
}
