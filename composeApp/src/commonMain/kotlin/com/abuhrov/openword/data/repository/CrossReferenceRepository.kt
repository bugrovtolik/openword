package com.abuhrov.openword.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.abuhrov.openword.data.local.prepareDatabaseFile
import com.abuhrov.openword.data.platform.ioDispatcher
import com.abuhrov.openword.db.CrossReferenceDb
import com.abuhrov.openword.db.DatabaseDriverFactory
import com.abuhrov.openword.util.Constants
import kotlinx.coroutines.withContext

data class CrossReferenceItem(
    val bookTo: Long,
    val chapterTo: Long,
    val verseToStart: Long,
    val verseToEnd: Long?,
    val votes: Long?
)

object CrossReferenceRepository {
    private var database: CrossReferenceDb? = null

    suspend fun initialize() = withContext(ioDispatcher) {
        ensureInitialized()
    }

    suspend fun getCrossReferences(book: Long, chapter: Long, verse: Long): List<CrossReferenceItem> = withContext(ioDispatcher) {
        val db = ensureInitialized() ?: return@withContext emptyList()
        try {
            // Return all references matching the parameters where verse falls into a range or matches exactly.
            db.crossReferenceQueries.getCrossReferences(
                book = book,
                chapter = chapter,
                verse = verse,
                verse_end = verse
            ).awaitAsList().map {
                CrossReferenceItem(
                    bookTo = it.book_to,
                    chapterTo = it.chapter_to,
                    verseToStart = it.verse_to_start,
                    verseToEnd = it.verse_to_end,
                    votes = it.votes
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun ensureInitialized(): CrossReferenceDb? {
        if (database == null) {
            try {
                val simpleName = Constants.CROSS_REFERENCE_DB_NAME.substringAfterLast('/')
                prepareDatabaseFile(Constants.CROSS_REFERENCE_DB_NAME)
                val driver = DatabaseDriverFactory().createDriver(simpleName)
                database = CrossReferenceDb(driver)
            } catch (_: Exception) {
                return null
            }
        }
        return database
    }
}
