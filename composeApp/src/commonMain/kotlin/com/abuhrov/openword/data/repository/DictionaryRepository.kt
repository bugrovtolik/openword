package com.abuhrov.openword.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.abuhrov.openword.data.local.prepareDatabaseFile
import com.abuhrov.openword.data.platform.ioDispatcher
import com.abuhrov.openword.db.DatabaseDriverFactory
import com.abuhrov.openword.db.DictionaryDb
import com.abuhrov.openword.util.Constants
import kotlinx.coroutines.withContext

object DictionaryRepository {
    private var database: DictionaryDb? = null
    private val memoryCache = mutableMapOf<String, String>()
    private var topicsCache: List<String>? = null

    suspend fun initialize() = withContext(ioDispatcher) {
        val db = ensureInitialized()
        if (db != null) {
            getTopics(db)
        }
    }

    fun hasDefinitionSync(word: String): Boolean {
        val cache = topicsCache ?: return false
        val lowerWord = word.lowercase()
        
        var matchedTopic = cache.find { it.lowercase() == lowerWord }
        if (matchedTopic == null) {
            matchedTopic = cache.filter { topic ->
                val lowerTopic = topic.lowercase()
                lowerWord.startsWith(lowerTopic.take(3))
            }.maxByOrNull { topic ->
                var commonLength = 0
                val lowerTopic = topic.lowercase()
                while (commonLength < lowerWord.length && commonLength < lowerTopic.length &&
                    lowerWord[commonLength] == lowerTopic[commonLength]) {
                    commonLength++
                }
                commonLength
            }
        }
        return matchedTopic != null
    }

    suspend fun findDefinition(word: String): String? = withContext(ioDispatcher) {
        val db = ensureInitialized() ?: return@withContext null
        
        // Use lowercase for matching
        val lowerWord = word.lowercase()
        
        if (memoryCache.containsKey(lowerWord)) {
            return@withContext memoryCache[lowerWord]!!
        }

        // Exact match first (case insensitive)
        val allTopics = getTopics(db)
        var matchedTopic = allTopics.find { it.lowercase() == lowerWord }

        // If not exact match, try longest common prefix logic (at least 3 characters)
        if (matchedTopic == null) {
            matchedTopic = allTopics.filter { topic ->
                val lowerTopic = topic.lowercase()
                lowerWord.startsWith(lowerTopic.take(3)) // At least first 3 chars match
            }.maxByOrNull { topic ->
                // The topic that has the longest common prefix is the best match
                var commonLength = 0
                val lowerTopic = topic.lowercase()
                while (commonLength < lowerWord.length && commonLength < lowerTopic.length &&
                    lowerWord[commonLength] == lowerTopic[commonLength]) {
                    commonLength++
                }
                commonLength
            }
        }

        if (matchedTopic != null) {
            val definition = db.dictionaryQueries.getDefinitionForTopic(matchedTopic).awaitAsOneOrNull()
            if (definition != null) {
                memoryCache[lowerWord] = definition
                return@withContext definition
            }
        }
        
        null
    }

    private suspend fun getTopics(db: DictionaryDb): List<String> {
        if (topicsCache == null) {
            topicsCache = db.dictionaryQueries.getAllTopics().awaitAsList()
        }
        return topicsCache!!
    }

    private suspend fun ensureInitialized(): DictionaryDb? {
        if (database == null) {
            try {
                val simpleName = Constants.DICTIONARY_DB_NAME.substringAfterLast('/')
                prepareDatabaseFile(Constants.DICTIONARY_DB_NAME)
                val driver = DatabaseDriverFactory().createDriver(simpleName)
                database = DictionaryDb(driver)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return database
    }
}
