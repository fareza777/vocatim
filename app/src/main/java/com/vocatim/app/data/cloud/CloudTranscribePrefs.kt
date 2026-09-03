package com.vocatim.app.data.cloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vocatim.app.data.prefs.secretsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A provider Vocatim knows how to talk to for speech-to-text.
 *
 * The point of the enum is that the user never types a URL or a model name:
 * picking a provider fills both, and all that is left is pasting a key.
 */
enum class CloudTranscribeProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val model: String,
    /** Where the user creates a key — opened straight from the settings card. */
    val keyUrl: String,
    /** Shown under the provider name, so the choice is not blind. */
    val noteRes: Int,
) {
    GROQ(
        id = "groq",
        label = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        model = "whisper-large-v3-turbo",
        keyUrl = "https://console.groq.com/keys",
        noteRes = com.vocatim.app.R.string.cloud_tx_provider_groq_note,
    ),
    OPENAI(
        id = "openai",
        label = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        model = "whisper-1",
        keyUrl = "https://platform.openai.com/api-keys",
        noteRes = com.vocatim.app.R.string.cloud_tx_provider_openai_note,
    );

    companion object {
        val DEFAULT = GROQ
        fun fromId(id: String?): CloudTranscribeProvider =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Everything [com.vocatim.app.data.transcribe.CloudTranscriber] needs. */
data class CloudTranscribeConfig(
    val provider: CloudTranscribeProvider,
    val apiKey: String,
    /** Overrides [CloudTranscribeProvider.model] when the user set one. */
    val modelOverride: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
    val baseUrl: String get() = provider.baseUrl
    val model: String get() = modelOverride.ifBlank { provider.model }
}

/**
 * Settings for online transcription — deliberately separate from
 * [CloudAiPrefs].
 *
 * They looked like the same thing while building it (same providers, same
 * OpenAI-compatible shape) but they are not the same thing to a user: one
 * sends transcript text for summaries, the other sends audio. Sharing one
 * form meant a chat model name had to be filled in before audio would
 * upload, which made no sense to anyone reading the screen.
 *
 * Lives in the secrets DataStore, so the key is excluded from encrypted
 * backups and from Android Auto Backup.
 */
class CloudTranscribePrefs(private val context: Context) {

    val config: Flow<CloudTranscribeConfig> = context.secretsDataStore.data.map { prefs ->
        CloudTranscribeConfig(
            provider = CloudTranscribeProvider.fromId(prefs[PROVIDER_KEY]),
            apiKey = prefs[API_KEY_KEY] ?: "",
            modelOverride = prefs[MODEL_KEY] ?: "",
        )
    }

    suspend fun current(): CloudTranscribeConfig = config.first()

    suspend fun setProvider(provider: CloudTranscribeProvider) {
        context.secretsDataStore.edit { prefs ->
            prefs[PROVIDER_KEY] = provider.id
            // A model name is provider-specific; carrying one across would
            // send whisper-large-v3-turbo to OpenAI, which rejects it.
            prefs.remove(MODEL_KEY)
        }
    }

    suspend fun setApiKey(key: String) {
        context.secretsDataStore.edit { prefs ->
            val trimmed = key.trim()
            if (trimmed.isEmpty()) prefs.remove(API_KEY_KEY)
            else prefs[API_KEY_KEY] = trimmed
        }
    }

    suspend fun setModelOverride(model: String) {
        context.secretsDataStore.edit { prefs ->
            val trimmed = model.trim()
            if (trimmed.isEmpty()) prefs.remove(MODEL_KEY)
            else prefs[MODEL_KEY] = trimmed
        }
    }

    suspend fun clear() {
        context.secretsDataStore.edit { prefs ->
            prefs.remove(PROVIDER_KEY)
            prefs.remove(API_KEY_KEY)
            prefs.remove(MODEL_KEY)
        }
    }

    private companion object {
        val PROVIDER_KEY = stringPreferencesKey("cloud_tx_provider")
        val API_KEY_KEY = stringPreferencesKey("cloud_tx_api_key")
        val MODEL_KEY = stringPreferencesKey("cloud_tx_model")
    }
}
