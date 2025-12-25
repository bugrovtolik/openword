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

private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent"

data class ChatMessage(
    val role: String,
    val text: String,
    val isError: Boolean = false
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
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isBlank()) {
            return "Error: GEMINI_API_KEY not found in local.properties. Please add it and rebuild."
        }

        return try {
            val contents = history.map { msg ->
                Content(
                    role = msg.role,
                    parts = listOf(Part(text = msg.text))
                )
            }

            val response: GeminiResponse = client.post("$GEMINI_URL?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(GeminiRequest(contents = contents))
            }.body()

            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "AI не відповідає"
        } catch (e: Exception) {
            "Помилка: ${e.message}"
        }
    }
}

@Serializable
data class GeminiRequest(val contents: List<Content>)

@Serializable
data class Content(
    val role: String,
    val parts: List<Part>
)

@Serializable
data class Part(val text: String)

@Serializable
data class GeminiResponse(val candidates: List<Candidate> = emptyList())

@Serializable
data class Candidate(val content: Content)