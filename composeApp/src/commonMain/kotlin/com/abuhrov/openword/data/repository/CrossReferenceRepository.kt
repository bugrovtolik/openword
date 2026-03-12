package com.abuhrov.openword.data.repository

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

    suspend fun initialize() {
        if (database == null) {
            withContext(ioDispatcher) {
                try {
                    prepareDatabaseFile(Constants.CROSS_REFERENCE_DB_NAME)
                    val driver = DatabaseDriverFactory().createDriver(Constants.CROSS_REFERENCE_DB_NAME.substringAfterLast('/'))
                    database = CrossReferenceDb(driver)
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to initialize Cross References", e)
                }
            }
        }
    }

    suspend fun getCrossReferences(book: Long, chapter: Long, verse: Long): List<CrossReferenceItem> {
        val db = database ?: return emptyList()
        return withContext(ioDispatcher) {
            try {
                // Return all references matching the parameters where verse falls into a range or matches exactly.
                db.crossReferenceQueries.getCrossReferences(
                    book = book,
                    chapter = chapter,
                    verse = verse,
                    verse_end = verse
                ).executeAsList().map {
                    CrossReferenceItem(
                        bookTo = it.book_to,
                        chapterTo = it.chapter_to,
                        verseToStart = it.verse_to_start,
                        verseToEnd = it.verse_to_end,
                        votes = it.votes
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
