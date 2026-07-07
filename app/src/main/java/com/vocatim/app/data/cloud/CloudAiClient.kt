package com.vocatim.app.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudAiException(message: String) : Exception(message)

/**
 * Minimal OpenAI-compatible chat-completions client. MiniMax, OpenAI,
 * DeepSeek, Groq, and most others share this request shape, so one client
 * covers every BYOK provider. Non-streaming: summaries/minutes are one-shot.
 */
class CloudAiClient(baseClient: OkHttpClient) {

    // LLM responses for long transcripts can take minutes to generate.
    private val client = baseClient.newBuilder()
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        config: CloudAiConfig,
        system: String,
        user: String,
        maxTokens: Int = 4096,
    ): String = withContext(Dispatchers.IO) {
        if (!config.isConfigured) throw CloudAiException("NOT_CONFIGURED")

        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("temperature", 0.3)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }

        val request = Request.Builder()
            .url(config.baseUrl + "/chat/completions")
            .header("Authorization", "Bearer " + config.apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Surface the provider's own error message when parseable.
                val providerMessage = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw CloudAiException(
                    providerMessage?.takeIf { it.isNotBlank() }
                        ?: "HTTP ${response.code}"
                )
            }
            val content = runCatching {
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }.getOrNull()
            // MiniMax M-series (and other reasoning models) may prepend a
            // <think>...</think> block; strip it so only the answer remains.
            content
                ?.replace(Regex("(?s)<think>.*?</think>"), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw CloudAiException("Empty response from provider")
        }
    }
}
