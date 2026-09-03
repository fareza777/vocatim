package com.vocatim.app.data.cloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vocatim.app.data.prefs.secretsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * BYOK (bring-your-own-key) cloud AI settings. The key belongs to the user
 * and only ever travels to the provider THEY configured — never to us.
 * Stored in the app-private secrets DataStore, which is excluded from both
 * encrypted backups and Android Auto Backup — the key never leaves the device.
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

    val config: Flow<CloudAiConfig> = context.secretsDataStore.data.map { prefs ->
        CloudAiConfig(
            baseUrl = prefs[BASE_URL_KEY] ?: "",
            apiKey = prefs[API_KEY_KEY] ?: "",
            model = prefs[MODEL_KEY] ?: "",
        )
    }

    suspend fun current(): CloudAiConfig = config.first()

    suspend fun save(baseUrl: String, apiKey: String, model: String) {
        context.secretsDataStore.edit { prefs ->
            prefs[BASE_URL_KEY] = baseUrl.trim().trimEnd('/')
            prefs[API_KEY_KEY] = apiKey.trim()
            prefs[MODEL_KEY] = model.trim()
        }
    }

    suspend fun clear() {
        context.secretsDataStore.edit { prefs ->
            prefs.remove(BASE_URL_KEY)
            prefs.remove(API_KEY_KEY)
            prefs.remove(MODEL_KEY)
            prefs.remove(TRANSCRIBE_MODEL_KEY)
        }
    }

    /**
     * Speech model for cloud transcription. Separate from [CloudAiConfig.model],
     * which names a chat model — the same provider and key serve both, but a
     * chat model cannot transcribe and vice versa.
     */
    val transcribeModel: Flow<String> = context.secretsDataStore.data.map {
        it[TRANSCRIBE_MODEL_KEY] ?: DEFAULT_TRANSCRIBE_MODEL
    }

    suspend fun currentTranscribeModel(): String = transcribeModel.first()

    suspend fun saveTranscribeModel(model: String) {
        context.secretsDataStore.edit { prefs ->
            val trimmed = model.trim()
            if (trimmed.isEmpty()) prefs.remove(TRANSCRIBE_MODEL_KEY)
            else prefs[TRANSCRIBE_MODEL_KEY] = trimmed
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
        private val TRANSCRIBE_MODEL_KEY = stringPreferencesKey("cloud_transcribe_model")

        /** Groq's turbo Whisper: multilingual, and the cheapest fast option. */
        const val DEFAULT_TRANSCRIBE_MODEL = "whisper-large-v3-turbo"
    }
}
