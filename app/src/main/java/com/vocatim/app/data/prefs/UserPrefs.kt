package com.vocatim.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vocatim.app.data.model.WhisperModel
import kotlinx.coroutines.flow.first

/**
 * Last-used model and language, applied to new recordings and imports.
 * A full settings screen arrives in a later phase; until then the debug
 * screen's selectors write here.
 */
class UserPrefs(private val context: Context) {

    suspend fun model(): WhisperModel {
        val id = context.prefsDataStore.data.first()[MODEL_KEY]
        return WhisperModel.fromId(id ?: "") ?: WhisperModel.TINY
    }

    suspend fun language(): String =
        context.prefsDataStore.data.first()[LANGUAGE_KEY] ?: "auto"

    suspend fun setModel(model: WhisperModel) {
        context.prefsDataStore.edit { it[MODEL_KEY] = model.id }
    }

    suspend fun setLanguage(code: String) {
        context.prefsDataStore.edit { it[LANGUAGE_KEY] = code }
    }

    private companion object {
        val MODEL_KEY = stringPreferencesKey("default_model")
        val LANGUAGE_KEY = stringPreferencesKey("default_language")
    }
}
