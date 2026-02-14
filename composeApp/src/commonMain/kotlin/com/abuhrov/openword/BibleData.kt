package com.abuhrov.openword

import androidx.compose.ui.text.font.FontFamily
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.abuhrov.openword.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

private val STRONGS_PATTERN = Regex("[HG]\\d+[A-Za-z]*")
private val ROOT_WORD_PATTERN = Regex("\\{([^}]+)\\}")
private const val LEXICON_DB_NAME = "vocabulary/lexicon.SQLite3"

val availableTranslations = listOf(
    Translation(
        id = "CUV",
        displayName = "Сучасний переклад УБТ",
        fileName = "translations/CUV.SQLite3",
        commentarySource = CommentarySource("Сучасний переклад", "commentaries/CUV.commentaries.SQLite3")
    ),
    Translation("GYZ", "Олександр Гижа", "translations/GYZ.SQLite3"),
    Translation("HOM", "Іван Хоменко", "translations/HOM.SQLite3"),
    Translation("KJV", "King James", "translations/KJV.SQLite3"),
    Translation("МСЦ", "МСЦ ЄХБ", "translations/MSC.SQLite3"),
    Translation(
        id = "UBIO",
        displayName = "Іван Огієнко",
        fileName = "translations/UBIO.SQLite3",
        commentarySource = CommentarySource("Іван Огієнко", "commentaries/UBIO.commentaries.SQLite3")
    ),
    Translation("NUP", "Юрій Попченко", "translations/NUP.SQLite3"),
    Translation(
        id = "UMT",
        displayName = "Свята Біблія: Сучасною мовою",
        fileName = "translations/UMT.SQLite3",
        commentarySource = CommentarySource("Свята Біблія: Сучасною мовою", "commentaries/UMT.commentaries.SQLite3")
    )
)

val availableCommentaries = listOf(
    CommentarySource("Біблійний культурно-історичний коментар", "commentaries/IVP.SQLite3"),
    CommentarySource("Томас Ко́нстебл", "commentaries/constable.SQLite3"),
    CommentarySource("Далласька богословська семінарія", "commentaries/dallas.SQLite3"),
)

data class Translation(
    val id: String,
    val displayName: String,
    val fileName: String,
    val commentarySource: CommentarySource? = null
)
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
data class CommentarySource(
    val displayName: String,
    val fileName: String
)

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
expect suspend fun deleteDatabaseFile(name: String)
expect suspend fun loadAppFont(): FontFamily?

expect val ioDispatcher: CoroutineDispatcher

suspend fun clearAllLocalData() = withContext(ioDispatcher) {
    deleteDatabaseFile(LEXICON_DB_NAME.substringAfterLast('/'))
    availableTranslations.forEach { deleteDatabaseFile(it.fileName.substringAfterLast('/')) }
    availableCommentaries.forEach { deleteDatabaseFile(it.fileName.substringAfterLast('/')) }
    CommentaryManager.clearCache()
}

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
        Book(it.book_number, it.long_name ?: "", it.chapter_count ?: 0L)
    }
    Bible(books, database)
}

suspend fun getVocabularyForVerse(verse: Verse): List<LexiconEntry> = withContext(ioDispatcher) {
    VocabularyManager.getVocabulary(verse.bookId, verse.chapter, verse.number)
}

suspend fun getCommentariesForVerse(verse: Verse): List<CommentaryItem> = withContext(ioDispatcher) {
    CommentaryManager.getCommentaries(verse)
}

suspend fun getCommentaryForMarker(bookId: Long, chapter: Long, marker: Long, source: CommentarySource): String? = withContext(ioDispatcher) {
    CommentaryManager.getMarkerNote(bookId, chapter, marker, source)
}

object CommentaryManager {
    private val databases = mutableMapOf<String, CommentaryDb>()
    private val mutex = Mutex()

    suspend fun clearCache() {
        mutex.withLock { databases.clear() }
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