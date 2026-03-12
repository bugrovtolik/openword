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

    private fun findMatchingTopic(topics: List<String>, lowerWord: String): String? {
        var matchedTopic = topics.find { it.lowercase() == lowerWord }
        if (matchedTopic == null) {
            matchedTopic = topics.filter { it.lowercase().let { lowerWord.startsWith(it.take(3)) } }
                .maxByOrNull { topic ->
                    val lowerTopic = topic.lowercase()
                    var i = 0
                    while (i < lowerWord.length && i < lowerTopic.length && lowerWord[i] == lowerTopic[i]) i++
                    i
                }
        }
        return matchedTopic
    }

    fun hasDefinitionSync(word: String): Boolean {
        val cache = topicsCache ?: return false
        return findMatchingTopic(cache, word.lowercase()) != null
    }

    suspend fun findDefinition(word: String): String? = withContext(ioDispatcher) {
        val db = ensureInitialized() ?: return@withContext null
        
        val lowerWord = word.lowercase()
        if (memoryCache.containsKey(lowerWord)) {
            return@withContext memoryCache[lowerWord]!!
        }

        val allTopics = getTopics(db)
        val matchedTopic = findMatchingTopic(allTopics, lowerWord)

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
