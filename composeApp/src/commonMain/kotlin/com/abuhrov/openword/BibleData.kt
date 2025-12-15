package com.abuhrov.openword

import com.abuhrov.openword.db.BibleDb
import com.abuhrov.openword.db.DatabaseDriverFactory
import com.abuhrov.openword.db.LexiconDb
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// Matches: H1234, H1234a, H1234G, G5678 (Case Insensitive)
private val STRONGS_PATTERN = Regex("[HG]\\d+[A-Za-z]*")
private val ROOT_WORD_PATTERN = Regex("\\{([^}]+)\\}")
private const val LEXICON_DB_NAME = "lexicon.SQLite3"

val availableTranslations = listOf(
    Translation("CUV", "Український (CUV)", "CUV.SQLite3"),
    Translation("KJV", "Англійський (KJV)", "KJV.SQLite3"),
    Translation("UBIO", "Український (Огієнко)", "UBIO.SQLite3"),
    Translation("NUP", "Український (НУП)", "NUP.SQLite3")
)

data class Translation(val id: String, val displayName: String, val fileName: String)
data class Book(val id: Long, val name: String, val chapterCount: Long)
data class Verse(val bookId: Long, val chapter: Long, val number: Long, val text: String)

data class LexiconEntry(
    val strongCode: String,
    val originalWord: String,
    val gloss: String,
    val transliteration: String?,
    val definition: String?
)

expect suspend fun checkDatabaseFile(name: String): Boolean
expect suspend fun installDatabaseFile(name: String)
expect val ioDispatcher: CoroutineDispatcher

private suspend fun prepareDatabaseFile(fileName: String) {
    withContext(ioDispatcher) {
        if (!checkDatabaseFile(fileName)) {
            withTimeout(15000L) {
                try {
                    println("Installing DB: $fileName ...")
                    installDatabaseFile(fileName)
                    println("DB $fileName successfully installed.")
                } catch (e: Exception) {
                    println("Failed to install $fileName: $e")
                    throw IllegalStateException("Failed to prepare database '$fileName'.", e)
                }
            }
        }
    }
}

class Bible(
    val translation: Translation,
    val books: List<Book>,
    private val database: BibleDb
) {
    suspend fun getVerses(bookId: Long, chapter: Long): List<Verse> = withContext(ioDispatcher) {
        try {
            database.bibleQueries.getVerses(bookId, chapter)
                .executeAsList()
                .map { Verse(it.book_number, it.chapter, it.verse, it.text) }
        } catch (e: Exception) {
            println("Error querying verses: $e")
            emptyList()
        }
    }
}

suspend fun loadBibleData(translation: Translation): Bible = withContext(ioDispatcher) {
    val database = initializeBibleDatabase(translation.fileName)
    val rawBooks = try {
        database.bibleQueries.getBooks().executeAsList()
    } catch (e: Exception) {
        println("Error fetching books: $e")
        emptyList()
    }
    val books = rawBooks.map {
        Book(it.book_number ?: 0L, it.long_name ?: "", it.chapter_count ?: 0L)
    }
    Bible(translation, books, database)
}

suspend fun getVocabularyForVerse(verse: Verse): List<LexiconEntry> = withContext(ioDispatcher) {
    VocabularyManager.getVocabulary(verse.bookId, verse.chapter, verse.number)
}

object VocabularyManager {
    private var database: LexiconDb? = null

    suspend fun initialize() = withContext(ioDispatcher) {
        ensureInitialized()
    }

    suspend fun getVocabulary(bookId: Long, chapter: Long, verse: Long): List<LexiconEntry> = withContext(ioDispatcher) {
        val db = ensureInitialized() ?: return@withContext emptyList()

        try {
            val rawEntries = db.lexiconQueries.getVocabularyForVerse(bookId, chapter, verse).executeAsList()

            rawEntries.map { entry ->
                val allCodes = STRONGS_PATTERN.findAll(entry.strong_code).map { it.value }.toList()

                // FIX: Normalize each code before lookup (H430 -> H0430)
                val glosses = allCodes.mapNotNull { rawCode ->
                    val code = normalizeStrongCode(rawCode)

                    // 1. Try Exact Normalized Match
                    var def = db.lexiconQueries.getLexiconDefinition(code).executeAsOneOrNull()

                    // 2. Fallback: Case Flip (H1254A -> H1254a)
                    if (def == null && code.length > 1 && code.last().isLetter()) {
                        val lastChar = code.last()
                        val swappedLast = if (lastChar.isUpperCase()) lastChar.lowercaseChar() else lastChar.uppercaseChar()
                        val altCode = code.dropLast(1) + swappedLast
                        def = db.lexiconQueries.getLexiconDefinition(altCode).executeAsOneOrNull()
                    }

                    // 3. Fallback: Base Code (strip suffix)
                    if (def == null && code.length > 1 && code.last().isLetter()) {
                        val baseCode = code.dropLast(1)
                        def = db.lexiconQueries.getLexiconDefinition(baseCode).executeAsOneOrNull()
                    }

                    def?.gloss
                }

                val combinedGloss = if (glosses.isEmpty()) "Unknown" else glosses.joinToString(" + ")

                // Root Word Logic
                val rootMatch = ROOT_WORD_PATTERN.find(entry.strong_code)
                val rootText = rootMatch?.groupValues?.get(1) ?: entry.strong_code
                val rawRootCode = STRONGS_PATTERN.find(rootText)?.value ?: allCodes.firstOrNull()

                var rootDef: String? = null
                var rootTrans: String? = null

                if (rawRootCode != null) {
                    val rootCode = normalizeStrongCode(rawRootCode) // FIX: Normalize root too

                    var defEntry = db.lexiconQueries.getLexiconDefinition(rootCode).executeAsOneOrNull()

                    if (defEntry == null && rootCode.length > 1 && rootCode.last().isLetter()) {
                        val lastChar = rootCode.last()
                        val swappedLast = if (lastChar.isUpperCase()) lastChar.lowercaseChar() else lastChar.uppercaseChar()
                        val altCode = rootCode.dropLast(1) + swappedLast
                        defEntry = db.lexiconQueries.getLexiconDefinition(altCode).executeAsOneOrNull()
                    }

                    if (defEntry == null && rootCode.length > 1 && rootCode.last().isLetter()) {
                        val baseCode = rootCode.dropLast(1)
                        defEntry = db.lexiconQueries.getLexiconDefinition(baseCode).executeAsOneOrNull()
                    }

                    rootDef = defEntry?.definition
                    rootTrans = defEntry?.transliteration
                }

                LexiconEntry(
                    // We keep original raw code for display if desired, or you can normalize this too
                    strongCode = normalizeStrongCode(entry.strong_code),
                    originalWord = entry.original_word,
                    gloss = combinedGloss,
                    transliteration = rootTrans,
                    definition = rootDef
                )
            }
        } catch (e: Exception) {
            println("Error querying vocabulary: $e")
            emptyList()
        }
    }

    private suspend fun ensureInitialized(): LexiconDb? {
        if (database == null) {
            try {
                database = initializeLexiconDatabase(LEXICON_DB_NAME)
            } catch (e: Exception) {
                println("Failed to initialize Vocabulary: $e")
                return null
            }
        }
        return database
    }
}

private suspend fun initializeBibleDatabase(fileName: String): BibleDb {
    prepareDatabaseFile(fileName)
    val driver = DatabaseDriverFactory().createDriver(fileName)
    return BibleDb(driver)
}

private suspend fun initializeLexiconDatabase(fileName: String): LexiconDb {
    prepareDatabaseFile(fileName)
    val driver = DatabaseDriverFactory().createDriver(fileName)
    return LexiconDb(driver)
}