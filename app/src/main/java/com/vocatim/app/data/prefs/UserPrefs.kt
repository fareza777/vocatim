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
)

/** User settings applied to new recordings and imports. */
class UserPrefs(private val context: Context) {

    val settings: Flow<UserSettings> = context.prefsDataStore.data.map { prefs ->
        UserSettings(
            model = WhisperModel.fromId(prefs[MODEL_KEY] ?: "") ?: WhisperModel.TINY,
            language = prefs[LANGUAGE_KEY] ?: "auto",
            translate = prefs[TRANSLATE_KEY] ?: false,
            threads = prefs[THREADS_KEY] ?: 0,
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

    private companion object {
        val MODEL_KEY = stringPreferencesKey("default_model")
        val LANGUAGE_KEY = stringPreferencesKey("default_language")
        val TRANSLATE_KEY = booleanPreferencesKey("translate_to_english")
        val THREADS_KEY = intPreferencesKey("num_threads")
    }
}
