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

    suspend fun generateChatResponse(history: List<ChatMessage>): String {
        val cacheKey = "gemini_cache_" + history.hashCode().toUInt().toString()
        val cachedResponse = Settings.getString(cacheKey, "")

        if (cachedResponse.isNotBlank()) {
            return cachedResponse
        }

        return try {
            val response: ProxyResponse = client.post(Constants.GEMINI_PROXY_URL) {
                contentType(ContentType.Application.Json)
                setBody(ProxyRequest(history = history))
            }.body()

            val result = response.text ?: "AI не відповідає"

            if (response.text != null) {
                Settings.setString(cacheKey, result)
            }

            result
        } catch (e: Exception) {
            "Помилка: ${e.message}"
        }
    }
}
