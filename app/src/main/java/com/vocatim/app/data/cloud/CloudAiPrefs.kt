package com.vocatim.app.data.cloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vocatim.app.data.prefs.prefsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * BYOK (bring-your-own-key) cloud AI settings. The key belongs to the user
 * and only ever travels to the provider THEY configured — never to us.
 * Stored in app-private DataStore; excluded from encrypted backups.
 */
data class CloudAiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

class CloudAiPrefs(private val context: Context) {

    val config: Flow<CloudAiConfig> = context.prefsDataStore.data.map { prefs ->
        CloudAiConfig(
            baseUrl = prefs[BASE_URL_KEY] ?: "",
            apiKey = prefs[API_KEY_KEY] ?: "",
            model = prefs[MODEL_KEY] ?: "",
        )
    }

    suspend fun current(): CloudAiConfig = config.first()

    suspend fun save(baseUrl: String, apiKey: String, model: String) {
        context.prefsDataStore.edit { prefs ->
            prefs[BASE_URL_KEY] = baseUrl.trim().trimEnd('/')
            prefs[API_KEY_KEY] = apiKey.trim()
            prefs[MODEL_KEY] = model.trim()
        }
    }

    suspend fun clear() {
        context.prefsDataStore.edit { prefs ->
            prefs.remove(BASE_URL_KEY)
            prefs.remove(API_KEY_KEY)
            prefs.remove(MODEL_KEY)
        }
    }

    companion object {
        /** Presets fill the base URL; all use the OpenAI-compatible shape. */
        val PRESETS = listOf(
            "MiniMax" to "https://api.minimax.io/v1",
            "OpenAI" to "https://api.openai.com/v1",
            "DeepSeek" to "https://api.deepseek.com/v1",
            "Groq" to "https://api.groq.com/openai/v1",
        )

        private val BASE_URL_KEY = stringPreferencesKey("cloud_ai_base_url")
        private val API_KEY_KEY = stringPreferencesKey("cloud_ai_api_key")
        private val MODEL_KEY = stringPreferencesKey("cloud_ai_model")
    }
}
