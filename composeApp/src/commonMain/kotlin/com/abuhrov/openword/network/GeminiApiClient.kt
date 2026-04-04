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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.ktor.client.request.preparePost
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    
    // 30 days in milliseconds — biblical text bindings are stable
    private const val CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000L

    /**
     * Single-shot AI request — no chat history.
     * Used by Commentaries features.
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

    /**
     * Streaming chat request — reads SSE chunks from the proxy.
     */
    suspend fun generateChatResponseStream(history: List<ChatMessage>): Flow<String> = flow {
        client.preparePost(Constants.GEMINI_PROXY_URL) {
            contentType(ContentType.Application.Json)
            setBody(ProxyRequest(history = history, stream = true))
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") continue
                    if (data.isBlank()) continue
                    try {
                        val jsonElement = Json.parseToJsonElement(data)
                        val textChunk = jsonElement.jsonObject["candidates"]?.jsonArray?.get(0)
                            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)
                            ?.jsonObject?.get("text")?.jsonPrimitive?.content
                        
                        if (textChunk != null) {
                            emit(textChunk)
                        }
                    } catch (e: Exception) {
                        // Ignore parse errors on individual chunks
                    }
                }
            }
        }
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
