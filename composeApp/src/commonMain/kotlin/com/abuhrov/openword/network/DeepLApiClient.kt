package com.abuhrov.openword.network

import com.abuhrov.openword.Settings
import com.abuhrov.openword.network.model.DeepLBatchRequest
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

    // 30 days in milliseconds — lexicon translations are stable
    private const val CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000L

    /**
     * Translate a single text string via DeepL API (proxied through Cloudflare Worker).
     * Used for one-off translations (e.g. commentaries translate button).
     * Results are cached for 30 days.
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

    /**
     * Translate multiple texts in a single DeepL API call.
     * Returns a list of translated strings in the same order as input.
     * Texts that are already cached are served from cache; only uncached texts
     * are sent to the API, minimizing network calls.
     */
    suspend fun translateBatch(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()

        val currentTimeMs = io.ktor.util.date.getTimeMillis()
        val results = MutableList(texts.size) { "" }

        // Separate cached from uncached
        val uncachedIndices = mutableListOf<Int>()
        val uncachedTexts = mutableListOf<String>()

        for ((index, text) in texts.withIndex()) {
            val cacheKey = "deepl_cache_" + text.hashCode().toUInt().toString()
            val timestampKey = "${cacheKey}_timestamp"
            val cachedResponse = Settings.getString(cacheKey, "")
            val cachedTimestampMs = Settings.getLong(timestampKey, 0L)

            if (cachedResponse.isNotBlank() && (currentTimeMs - cachedTimestampMs) < CACHE_TTL_MS) {
                results[index] = cachedResponse
            } else {
                // Clean up stale cache
                if (cachedResponse.isNotBlank()) {
                    Settings.remove(cacheKey)
                    Settings.remove(timestampKey)
                }
                uncachedIndices.add(index)
                uncachedTexts.add(text)
            }
        }

        // All cached — return immediately
        if (uncachedTexts.isEmpty()) return results

        return try {
            val response: DeepLResponse = client.post(Constants.DEEPL_PROXY_URL) {
                contentType(ContentType.Application.Json)
                setBody(DeepLBatchRequest(uncachedTexts))
            }.body()

            // Map API results back to their original positions
            for ((i, apiIndex) in uncachedIndices.withIndex()) {
                val translated = response.translations.getOrNull(i)?.text ?: texts[apiIndex]
                results[apiIndex] = translated

                // Cache each result individually
                val originalText = texts[apiIndex]
                val cacheKey = "deepl_cache_" + originalText.hashCode().toUInt().toString()
                val timestampKey = "${cacheKey}_timestamp"
                Settings.setString(cacheKey, translated)
                Settings.setLong(timestampKey, currentTimeMs)
            }

            results
        } catch (_: Exception) {
            // On failure, fill uncached slots with original text
            for (idx in uncachedIndices) {
                results[idx] = texts[idx]
            }
            results
        }
    }
}
