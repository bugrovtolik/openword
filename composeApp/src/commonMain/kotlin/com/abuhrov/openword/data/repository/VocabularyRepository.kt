package com.abuhrov.openword.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.abuhrov.openword.data.local.prepareDatabaseFile
import com.abuhrov.openword.data.platform.ioDispatcher
import com.abuhrov.openword.db.DatabaseDriverFactory
import com.abuhrov.openword.db.LexiconDb
import com.abuhrov.openword.model.LexiconEntry
import com.abuhrov.openword.model.VerseLexiconPayload
import com.abuhrov.openword.model.WordTagMapping
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
            if (rawEntries.isEmpty()) return@withContext emptyList()
            enrichVocabulary(db, rawEntries)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getVerseLexiconPayload(bookId: Long, chapter: Long, verse: Long, verseText: String): VerseLexiconPayload? = withContext(ioDispatcher) {
        val db = ensureInitialized() ?: return@withContext null
        try {
            val rawEntries = db.lexiconQueries.getVocabularyForVerse(bookId, chapter, verse).awaitAsList()
            if (rawEntries.isEmpty()) return@withContext null
            mapToPayload(rawEntries, verseText)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getVocabularyAndPayload(bookId: Long, chapter: Long, verse: Long, verseText: String): Pair<List<LexiconEntry>, VerseLexiconPayload?> = withContext(ioDispatcher) {
        val db = ensureInitialized() ?: return@withContext emptyList<LexiconEntry>() to null
        try {
            val rawEntries = db.lexiconQueries.getVocabularyForVerse(bookId, chapter, verse).awaitAsList()
            if (rawEntries.isEmpty()) return@withContext emptyList<LexiconEntry>() to null
            
            enrichVocabulary(db, rawEntries) to mapToPayload(rawEntries, verseText)
        } catch (_: Exception) {
            emptyList<LexiconEntry>() to null
        }
    }

    private fun mapToPayload(rawEntries: List<com.abuhrov.openword.db.GetVocabularyForVerse>, verseText: String): VerseLexiconPayload {
        val sourceList = rawEntries.map { 
            WordTagMapping(heb = it.original_word, tags = it.strong_code)
        }
        return VerseLexiconPayload(verse = verseText, source = sourceList)
    }

    private suspend fun enrichVocabulary(db: LexiconDb, rawEntries: List<com.abuhrov.openword.db.GetVocabularyForVerse>): List<LexiconEntry> {
        // 1. Collect all unique raw codes across all words in the verse
        val allRawCodes = rawEntries.flatMap { entry ->
            Constants.STRONGS_PATTERN.findAll(entry.strong_code).map { it.value }
        }.toSet()

        // 2. Batch lookup lexicon entries with fallback logic
        val lexiconMap = batchLookupLexicon(db, allRawCodes)

        // 3. Process each entry, splitting multi-tag strings into individual LexiconEntry objects
        return rawEntries.flatMap { entry ->
            val classification = classifyStrongs(entry.strong_code)
            
            // Create entries for prefixes, roots, and suffixes separately to allow exact mapping
            val allCodes = classification.prefixes + classification.roots + classification.suffixes
            
            allCodes.map { rawTag ->
                val lexicon = lexiconMap[rawTag]
                LexiconEntry(
                    strongCode = normalizeStrongCode(rawTag),
                    originalWord = entry.original_word,
                    gloss = lexicon?.gloss ?: "Unknown",
                    transliteration = lexicon?.transliteration,
                    definition = lexicon?.definition
                )
            }
        }.distinctBy { it.strongCode }
    }



    private suspend fun batchLookupLexicon(db: LexiconDb, rawCodes: Set<String>): Map<String, com.abuhrov.openword.db.Lexicon> {
        val candidates = mutableSetOf<String>()

        for (rawCode in rawCodes) {
            val code = normalizeStrongCode(rawCode)
            candidates.add(code)

            if (code.length > 1 && code.last().isLetter()) {
                val lastChar = code.last()
                val swappedLast = if (lastChar.isUpperCase()) lastChar.lowercaseChar() else lastChar.uppercaseChar()
                candidates.add(code.dropLast(1) + swappedLast)
                candidates.add(code.dropLast(1))
            }
        }

        val results = db.lexiconQueries.getLexiconBatch(candidates.toList()).awaitAsList()
        val resultMap = results.associateBy { it.strong_code }

        val finalMap = mutableMapOf<String, com.abuhrov.openword.db.Lexicon>()
        for (rawCode in rawCodes) {
            val code = normalizeStrongCode(rawCode)
            var lexicon = resultMap[code]
            
            if (lexicon == null && code.length > 1 && code.last().isLetter()) {
                val lastChar = code.last()
                val swappedLast = if (lastChar.isUpperCase()) lastChar.lowercaseChar() else lastChar.uppercaseChar()
                lexicon = resultMap[code.dropLast(1) + swappedLast]
            }
            
            if (lexicon == null && code.length > 1 && code.last().isLetter()) {
                lexicon = resultMap[code.dropLast(1)]
            }
            
            if (lexicon != null) {
                finalMap[rawCode] = lexicon
            }
        }
        return finalMap
    }

    private fun classifyStrongs(rawString: String): StrongsClassification {
        val rootMatch = Constants.ROOT_WORD_PATTERN.find(rawString)
        val rootRange = rootMatch?.range ?: IntRange.EMPTY
        
        val allMatches = Constants.STRONGS_PATTERN.findAll(rawString)
        val prefixes = mutableListOf<String>()
        val roots = mutableListOf<String>()
        val suffixes = mutableListOf<String>()
        
        for (match in allMatches) {
            val code = match.value
            val range = match.range
            
            if (rootRange != IntRange.EMPTY) {
                if (range.first >= rootRange.first && range.last <= rootRange.last) {
                    roots.add(code)
                } else if (range.last < rootRange.first) {
                    prefixes.add(code)
                } else {
                    suffixes.add(code)
                }
            } else {
                roots.add(code)
            }
        }
        
        return StrongsClassification(prefixes, roots, suffixes)
    }

    private data class StrongsClassification(
        val prefixes: List<String>,
        val roots: List<String>,
        val suffixes: List<String>
    )



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
