package com.abuhrov.openword

import androidx.compose.ui.text.font.FontFamily
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.abuhrov.openword.db.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

private val STRONGS_PATTERN = Regex("[HG]\\d+[A-Za-z]*")
private val ROOT_WORD_PATTERN = Regex("\\{([^}]+)\\}")
private const val LEXICON_DB_NAME = "vocabulary/lexicon.SQLite3"

val availableTranslations = listOf(
    Translation("CUV", "Український (CUV)", "translations/CUV.SQLite3"),
    Translation("KJV", "Англійський (KJV)", "translations/KJV.SQLite3"),
    Translation("UBIO", "Український (Огієнко)", "translations/UBIO.SQLite3"),
    Translation("NUP", "Український (НУП)", "translations/NUP.SQLite3")
)

val availableCommentaries = listOf(
    CommentarySource("Огієнко", "commentaries/UBIO.commentaries.SQLite3"),
    CommentarySource("БКІК", "commentaries/IVP.SQLite3"),
    CommentarySource("Далас", "commentaries/constable.SQLite3")
)

data class Translation(val id: String, val displayName: String, val fileName: String)
data class Book(val id: Long, val name: String, val chapterCount: Long)
data class Verse(val bookId: Long, val chapter: Long, val number: Long, val text: String)

@Serializable
data class LexiconEntry(
    val strongCode: String,
    val originalWord: String,
    val gloss: String,
    val transliteration: String?,
    val definition: String?
)

@Serializable
data class CommentarySource(val displayName: String, val fileName: String)

@Serializable
data class CommentaryItem(
    val sourceName: String,
    val chapter: Long,
    val verseStart: Long,
    val verseEnd: Long,
    val text: String
)

expect suspend fun checkDatabaseFile(name: String): Boolean
expect suspend fun installDatabaseFile(name: String, resourcePath: String)
expect suspend fun loadAppFont(): FontFamily?

expect val ioDispatcher: CoroutineDispatcher

private suspend fun prepareDatabaseFile(fileName: String) {
    val simpleName = fileName.substringAfterLast('/')
    withContext(ioDispatcher) {
        if (!checkDatabaseFile(simpleName)) {
            withTimeout(15000L) {
                try {
                    installDatabaseFile(simpleName, "files/$fileName")
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to prepare database '$simpleName'.", e)
                }
            }
        }
    }
}

class Bible(
    val books: List<Book>,
    private val database: BibleDb
) {
    suspend fun getVerses(bookId: Long, chapter: Long): List<Verse> = withContext(ioDispatcher) {
        try {
            database.bibleQueries.getVerses(bookId, chapter)
                .awaitAsList()
                .map { Verse(it.book_number, it.chapter, it.verse, it.text) }
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
        Book(it.book_number, it.long_name ?: "", it.chapter_count ?: 0L)
    }
    Bible(books, database)
}

suspend fun getVocabularyForVerse(verse: Verse): List<LexiconEntry> = withContext(ioDispatcher) {
    VocabularyManager.getVocabulary(verse.bookId, verse.chapter, verse.number)
}

suspend fun getCommentariesForVerse(verse: Verse): List<CommentaryItem> = withContext(ioDispatcher) {
    val results = mutableListOf<CommentaryItem>()
    for (source in availableCommentaries) {
        try {
            prepareDatabaseFile(source.fileName)
            val simpleName = source.fileName.substringAfterLast('/')
            val driver = DatabaseDriverFactory().createDriver(simpleName)
            val db = CommentaryDb(driver)

            val comments = db.commentaryQueries.getCommentariesForVerse(
                book_number = verse.bookId,
                chapter = verse.chapter,
                verse_start = verse.number,
                verse_end = verse.number
            ).awaitAsList()

            comments.forEach { c ->
                results.add(CommentaryItem(
                    sourceName = source.displayName,
                    chapter = c.chapter ?: 0L,
                    verseStart = c.verse_start ?: 0L,
                    verseEnd = c.verse_end ?: 0L,
                    text = c.text ?: ""
                ))
            }
        } catch (_: Exception) { }
    }
    results
}

object VocabularyManager {
    private var database: LexiconDb? = null

    suspend fun initialize() = withContext(ioDispatcher) {
        ensureInitialized()
    }

    suspend fun getVocabulary(bookId: Long, chapter: Long, verse: Long): List<LexiconEntry> = withContext(ioDispatcher) {
        val db = ensureInitialized() ?: return@withContext emptyList()

        try {
            val rawEntries = db.lexiconQueries.getVocabularyForVerse(bookId, chapter, verse).awaitAsList()

            rawEntries.map { entry ->
                val allCodes = STRONGS_PATTERN.findAll(entry.strong_code).map { it.value }.toList()

                val glosses = allCodes.mapNotNull { rawCode ->
                    getLexicon(db, rawCode)?.gloss
                }

                val combinedGloss = if (glosses.isEmpty()) "Unknown" else glosses.joinToString(" + ")

                val rootMatch = ROOT_WORD_PATTERN.find(entry.strong_code)
                val rootText = rootMatch?.groupValues?.get(1) ?: entry.strong_code
                val rawRootCode = STRONGS_PATTERN.find(rootText)?.value ?: allCodes.firstOrNull()

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
                val simpleName = LEXICON_DB_NAME.substringAfterLast('/')
                prepareDatabaseFile(LEXICON_DB_NAME)
                val driver = DatabaseDriverFactory().createDriver(simpleName)
                database = LexiconDb(driver)
            } catch (_: Exception) {
                return null
            }
        }
        return database
    }
}

suspend fun getLexicon(db: LexiconDb, rawCode: String): Lexicon? {
    val code = normalizeStrongCode(rawCode)
    var def = db.lexiconQueries.getLexiconDefinition(code).awaitAsOneOrNull()

    if (def == null && code.length > 1 && code.last().isLetter()) {
        val lastChar = code.last()
        val swappedLast =
            if (lastChar.isUpperCase()) lastChar.lowercaseChar() else lastChar.uppercaseChar()
        val altCode = code.dropLast(1) + swappedLast
        def = db.lexiconQueries.getLexiconDefinition(altCode).awaitAsOneOrNull()
    }
    if (def == null && code.length > 1 && code.last().isLetter()) {
        val baseCode = code.dropLast(1)
        def = db.lexiconQueries.getLexiconDefinition(baseCode).awaitAsOneOrNull()
    }
    return def
}