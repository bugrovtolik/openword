package com.abuhrov.openword.network

import com.abuhrov.openword.Settings
import com.abuhrov.openword.model.ChatMessage
import com.abuhrov.openword.network.model.ProxyRequest
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

object GeminiApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }
    
    // 24 hours in milliseconds
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

    /**
     * Single-shot AI request — no chat history.
     * Used by Vocabulary and Commentaries features.
     */
    suspend fun generateSingleResponse(prompt: String): String {
        val cacheKey = "gemini_cache_" + prompt.hashCode().toUInt().toString()
        return generateResponse(cacheKey, history = listOf(ChatMessage("user", prompt)))
    }

    /**
     * Multi-turn chat request — preserves the full conversation history.
     * Used only by AIPopup.
     */
    suspend fun generateChatResponse(history: List<ChatMessage>): String {
        val cacheKey = "gemini_cache_" + history.hashCode().toUInt().toString()
        return generateResponse(cacheKey, history)
    }

    private suspend fun generateResponse(cacheKey: String, history: List<ChatMessage>): String {
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
            val response: ProxyResponse = client.post(Constants.GEMINI_PROXY_URL) {
                contentType(ContentType.Application.Json)
                setBody(ProxyRequest(history = history))
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
