package com.abuhrov.openword

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val PROXY_URL = "https://openword-api.bugrovtolik.workers.dev"

@Serializable
data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String
)

object GeminiApi {
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
            val response: ProxyResponse = client.post(PROXY_URL) {
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

@Serializable
data class ProxyRequest(val history: List<ChatMessage>)

@Serializable
data class ProxyResponse(val text: String?)