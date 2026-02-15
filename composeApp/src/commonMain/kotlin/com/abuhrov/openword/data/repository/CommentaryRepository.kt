package com.abuhrov.openword.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.abuhrov.openword.data.config.availableCommentaries
import com.abuhrov.openword.data.local.prepareDatabaseFile
import com.abuhrov.openword.data.platform.ioDispatcher
import com.abuhrov.openword.db.CommentaryDb
import com.abuhrov.openword.db.DatabaseDriverFactory
import com.abuhrov.openword.model.CommentaryItem
import com.abuhrov.openword.model.CommentarySource
import com.abuhrov.openword.model.Verse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CommentaryRepository {
    private val databases = mutableMapOf<String, CommentaryDb>()
    private val mutex = Mutex()

    suspend fun clearCache() {
        mutex.withLock { databases.clear() }
    }

    /**
     * Pre-downloads and opens all available commentary databases in parallel.
     * Call at app startup so commentaries are ready when the user needs them.
     */
    suspend fun initialize() = coroutineScope {
        availableCommentaries.map { source ->
            async(ioDispatcher) {
                try {
                    getOrOpenDatabase(source)
                } catch (_: Exception) {
                    // Non-fatal: individual commentary may fail to load
                }
            }
        }.awaitAll()
    }

    private suspend fun getOrOpenDatabase(source: CommentarySource): CommentaryDb {
        mutex.withLock {
            databases[source.fileName]?.let { return it }
            prepareDatabaseFile(source.fileName)
            val simpleName = source.fileName.substringAfterLast('/')
            val driver = DatabaseDriverFactory().createDriver(simpleName)
            val db = CommentaryDb(driver)
            databases[source.fileName] = db
            return db
        }
    }

    suspend fun getCommentaries(verse: Verse): List<CommentaryItem> = coroutineScope {
        availableCommentaries.map { source ->
            async(ioDispatcher) {
                try {
                    val db = getOrOpenDatabase(source)
                    db.commentaryQueries.getCommentariesForVerse(
                        book_number = verse.bookId,
                        chapter = verse.chapter,
                        verse_start = verse.number,
                        verse_end = verse.number
                    ).awaitAsList().map { c ->
                        CommentaryItem(
                            sourceName = source.displayName,
                            chapter = c.chapter ?: 0L,
                            verseStart = c.verse_start ?: 0L,
                            verseEnd = c.verse_end ?: 0L,
                            text = c.text ?: ""
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun getMarkerNote(bookId: Long, chapter: Long, marker: Long, source: CommentarySource): String? {
        return try {
            val db = getOrOpenDatabase(source)
            db.commentaryQueries.getCommentaryByMarker(
                book_number = bookId,
                chapter = chapter,
                marker = "[$marker]"
            ).awaitAsOneOrNull()?.text
        } catch (_: Exception) {
            null
        }
    }
}
