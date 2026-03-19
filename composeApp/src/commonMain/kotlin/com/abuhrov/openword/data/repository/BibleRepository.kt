package com.abuhrov.openword.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.abuhrov.openword.data.local.prepareDatabaseFile
import com.abuhrov.openword.data.platform.ioDispatcher
import com.abuhrov.openword.db.BibleDb
import com.abuhrov.openword.db.DatabaseDriverFactory
import com.abuhrov.openword.model.Book
import com.abuhrov.openword.model.CommentaryItem
import com.abuhrov.openword.model.CommentarySource
import com.abuhrov.openword.model.LexiconEntry
import com.abuhrov.openword.model.Translation
import com.abuhrov.openword.model.Verse
import com.abuhrov.openword.model.VerseLexiconPayload
import com.abuhrov.openword.util.stripTags
import kotlinx.coroutines.withContext

class Bible(
    val books: List<Book>,
    private val database: BibleDb
) {
    suspend fun getVerses(bookId: Long, chapter: Long): List<Verse> = withContext(ioDispatcher) {
        try {
            database.bibleQueries.getVerses(bookId, chapter)
                .awaitAsList()
                .map { Verse(it.book_number, it.chapter, it.verse, it.text) }
                .filter {
                    val plainText = it.text.replace(Regex("<[^>]+>"), "").trim()
                    plainText.isNotEmpty()
                }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

suspend fun loadBibleData(translation: Translation): Bible = withContext(ioDispatcher) {
    prepareDatabaseFile(translation.fileName)
    val simpleName = translation.fileName.substringAfterLast('/')
    val driver = DatabaseDriverFactory().createDriver(simpleName)
    val database = BibleDb(driver)

    val rawBooks = try {
        database.bibleQueries.getBooks().awaitAsList()
    } catch (_: Exception) {
        emptyList()
    }
    val books = rawBooks.map {
        val longName = if (it.long_name != null && "*" in it.long_name) {
            it.long_name.replace("*", "")
        } else {
            it.long_name ?: ""
        }
        Book(it.book_number, longName, it.short_name ?: "", it.chapter_count ?: 0L, it.book_color)
    }
    Bible(books, database)
}

suspend fun getVocabularyForVerse(verse: Verse): List<LexiconEntry> = withContext(ioDispatcher) {
    VocabularyRepository.getVocabulary(verse.bookId, verse.chapter, verse.number)
}

suspend fun getVerseLexiconPayload(verse: Verse): VerseLexiconPayload? = withContext(ioDispatcher) {
    val cleanText = stripTags(verse.text).trim()
    VocabularyRepository.getVerseLexiconPayload(verse.bookId, verse.chapter, verse.number, cleanText)
}

suspend fun getVocabularyAndPayloadForVerse(verse: Verse): Pair<List<LexiconEntry>, VerseLexiconPayload?> = withContext(ioDispatcher) {
    val cleanText = stripTags(verse.text).trim()
    VocabularyRepository.getVocabularyAndPayload(verse.bookId, verse.chapter, verse.number, cleanText)
}


suspend fun getCommentariesForVerse(verse: Verse): List<CommentaryItem> = withContext(ioDispatcher) {
    CommentaryRepository.getCommentaries(verse)
}

suspend fun getCommentaryForMarker(bookId: Long, chapter: Long, verse: Long, marker: String, source: CommentarySource): String? = withContext(ioDispatcher) {
    CommentaryRepository.getMarkerNote(bookId, chapter, verse, marker, source)
}
