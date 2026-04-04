package com.abuhrov.openword.network

import com.abuhrov.openword.Settings
import com.abuhrov.openword.network.model.ProxyResponse
import com.abuhrov.openword.util.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object GroqApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }

    // 30 days in milliseconds — biblical text bindings are stable
    private const val CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000L

    /**
     * Single-shot AI request via Groq — fast inference for structured JSON tasks.
     * Sends the prompt as plain text to the /groq Worker endpoint.
     * Used by Vocabulary word binding.
     */
    suspend fun generateResponse(prompt: String): String {
        val cacheKey = "groq_cache_" + prompt.hashCode().toUInt().toString()
        val timestampKey = "${cacheKey}_timestamp"

        val cachedResponse = Settings.getString(cacheKey, "")
        val cachedTimestampMs = Settings.getLong(timestampKey, 0L)
        val currentTimeMs = io.ktor.util.date.getTimeMillis()

        if (cachedResponse.isNotBlank() && (currentTimeMs - cachedTimestampMs) < CACHE_TTL_MS) {
            return cachedResponse
        }

        // Clean up stale cache entry
        if (cachedResponse.isNotBlank()) {
            Settings.remove(cacheKey)
            Settings.remove(timestampKey)
        }

        return try {
            val response: ProxyResponse = client.post(Constants.GROQ_PROXY_URL) {
                contentType(ContentType.Text.Plain)
                setBody(prompt)
            }.body()

            val result = response.text ?: "AI не відповідає"

            if (response.text != null) {
                Settings.setString(cacheKey, result)
                Settings.setLong(timestampKey, currentTimeMs)
            }

            result
        } catch (e: Exception) {
            "Помилка: ${e.message}"
        }
    }
}
