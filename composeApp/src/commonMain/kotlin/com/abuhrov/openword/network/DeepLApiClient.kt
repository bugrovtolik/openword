package com.abuhrov.openword.network

import com.abuhrov.openword.Settings
import com.abuhrov.openword.network.model.DeepLRequest
import com.abuhrov.openword.network.model.DeepLResponse
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

object DeepLApiClient {
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
     * Translate a single text string via DeepL API (proxied through Cloudflare Worker).
     * Uses HTML tag handling to preserve markup.
     * Results are cached for 24 hours.
     */
    suspend fun translateText(text: String): String {
        val cacheKey = "deepl_cache_" + text.hashCode().toUInt().toString()
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
            val request = DeepLRequest(text)

            val response: DeepLResponse = client.post(Constants.DEEPL_PROXY_URL) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            val result = response.translations.firstOrNull()?.text ?: text

            Settings.setString(cacheKey, result)
            Settings.setLong(timestampKey, currentTimeMs)

            result
        } catch (_: Exception) {
            text // Return original text on failure
        }
    }
}
