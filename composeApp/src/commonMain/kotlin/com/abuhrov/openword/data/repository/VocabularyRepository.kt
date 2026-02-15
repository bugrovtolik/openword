package com.abuhrov.openword.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.abuhrov.openword.data.local.prepareDatabaseFile
import com.abuhrov.openword.data.platform.ioDispatcher
import com.abuhrov.openword.db.DatabaseDriverFactory
import com.abuhrov.openword.db.LexiconDb
import com.abuhrov.openword.model.LexiconEntry
import com.abuhrov.openword.util.Constants
import com.abuhrov.openword.util.normalizeStrongCode
import kotlinx.coroutines.withContext

object VocabularyRepository {
    private var database: LexiconDb? = null

    suspend fun initialize() = withContext(ioDispatcher) {
        ensureInitialized()
    }

    suspend fun getVocabulary(bookId: Long, chapter: Long, verse: Long): List<LexiconEntry> = withContext(ioDispatcher) {
        val db = ensureInitialized() ?: return@withContext emptyList()

        try {
            val rawEntries = db.lexiconQueries.getVocabularyForVerse(bookId, chapter, verse).awaitAsList()

            rawEntries.map { entry ->
                val allCodes = Constants.STRONGS_PATTERN.findAll(entry.strong_code).map { it.value }.toList()

                val glosses = allCodes.mapNotNull { rawCode ->
                    getLexicon(db, rawCode)?.gloss
                }

                val combinedGloss = if (glosses.isEmpty()) "Unknown" else glosses.joinToString(" + ")

                val rootMatch = Constants.ROOT_WORD_PATTERN.find(entry.strong_code)
                val rootText = rootMatch?.groupValues?.get(1) ?: entry.strong_code
                val rawRootCode = Constants.STRONGS_PATTERN.find(rootText)?.value ?: allCodes.firstOrNull()

                var rootDef: String? = null
                var rootTrans: String? = null

                if (rawRootCode != null) {
                    val defEntry = getLexicon(db, rawRootCode)

                    rootDef = defEntry?.definition
                    rootTrans = defEntry?.transliteration
                }

                LexiconEntry(
                    strongCode = normalizeStrongCode(entry.strong_code),
                    originalWord = entry.original_word,
                    gloss = combinedGloss,
                    transliteration = rootTrans,
                    definition = rootDef
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun ensureInitialized(): LexiconDb? {
        if (database == null) {
            try {
                val simpleName = Constants.LEXICON_DB_NAME.substringAfterLast('/')
                prepareDatabaseFile(Constants.LEXICON_DB_NAME)
                val driver = DatabaseDriverFactory().createDriver(simpleName)
                database = LexiconDb(driver)
            } catch (_: Exception) {
                return null
            }
        }
        return database
    }
}
