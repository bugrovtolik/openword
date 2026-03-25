package com.abuhrov.openword.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.abuhrov.openword.data.local.prepareDatabaseFile
import com.abuhrov.openword.data.platform.ioDispatcher
import com.abuhrov.openword.db.DatabaseDriverFactory
import com.abuhrov.openword.db.LexiconDb
import com.abuhrov.openword.db.WordsDefinitionsDb
import com.abuhrov.openword.db.lexicon.GetVocabularyForVerse
import com.abuhrov.openword.db.lexicon.Lexicon
import com.abuhrov.openword.model.LexiconEntry
import com.abuhrov.openword.model.VerseLexiconPayload
import com.abuhrov.openword.model.WordTagMapping
import com.abuhrov.openword.util.Constants
import com.abuhrov.openword.util.normalizeStrongCode
import kotlinx.coroutines.withContext
import kotlin.collections.forEach

object VocabularyRepository {
    private var lexiconDb: LexiconDb? = null
    private var wordsDefinitionsDb: WordsDefinitionsDb? = null

    suspend fun initialize() = withContext(ioDispatcher) {
        ensureInitialized()
    }


    suspend fun getVocabularyAndPayload(bookId: Long, chapter: Long, verse: Long, verseText: String): Pair<List<LexiconEntry>, VerseLexiconPayload?> = withContext(ioDispatcher) {
        if (!ensureInitialized()) return@withContext emptyList<LexiconEntry>() to null
        val lexicon = lexiconDb!!
        val wordsDefinitions = wordsDefinitionsDb!!
        try {
            val rawEntries = lexicon.lexiconQueries.getVocabularyForVerse(bookId, chapter, verse).awaitAsList()
            if (rawEntries.isEmpty()) return@withContext emptyList<LexiconEntry>() to null

            enrichVocabulary(lexicon, wordsDefinitions, rawEntries) to VerseLexiconPayload(
                verse = verseText,
                source = rawEntries.map {
                    WordTagMapping(
                        orig = it.original_word,
                        tags = it.strong_code
                    )
                }
            )
        } catch (_: Exception) {
            emptyList<LexiconEntry>() to null
        }
    }

    suspend fun getLexiconEntry(strongCode: String): LexiconEntry? = withContext(ioDispatcher) {
        if (!ensureInitialized()) return@withContext null
        val lexicon = lexiconDb!!
        val wordsDefinitions = wordsDefinitionsDb!!

        try {
            val normalizedCode = normalizeStrongCode(strongCode)
            val lex = lexicon.lexiconQueries.getLexiconDefinition(normalizedCode).awaitAsOneOrNull()
            val def = wordsDefinitions.wordsDefinitionsDbQueries.getLexiconDefinition(normalizedCode).awaitAsOneOrNull()

            if (lex == null && def == null) return@withContext null

            LexiconEntry(
                strongCode = normalizedCode,
                shortDefinition = lex?.gloss ?: def?.short_definition ?: "",
                fullDefinition = def?.definition ?: lex?.definition,
                transliteration = lex?.transliteration ?: def?.transliteration,
                originalWord = lex?.original_word ?: def?.lexeme,
                morphology = lex?.morphology
            )
        } catch (_: Exception) {
            null
        }
    }


    private suspend fun enrichVocabulary(
        lexiconDb: LexiconDb,
        definitionsDb: WordsDefinitionsDb,
        rawEntries: List<GetVocabularyForVerse>
    ): List<LexiconEntry> {
        // 2. Batch lookup lexicon entries with fallback logic
        val lexiconMap = batchLookupLexicon(lexiconDb, rawEntries)
        val roots = rawEntries.flatMap { classifyStrongs(it.strong_code).roots }
        val definitions = definitionsDb.wordsDefinitionsDbQueries.getLexiconBatch(roots).awaitAsList()

        // 3. Process each entry, splitting multi-tag strings into individual LexiconEntry objects
        val strongLexicons = rawEntries.flatMap { entry ->
            val classification = classifyStrongs(entry.strong_code)

            // Create entries for prefixes, roots, and suffixes separately to allow exact mapping
            val allCodes = classification.prefixes + classification.roots + classification.suffixes
            allCodes.map { rawTag ->
                val lexicon = lexiconMap[rawTag]
                LexiconEntry(
                    strongCode = normalizeStrongCode(rawTag),
                    shortDefinition = lexicon?.gloss ?: "",
                    fullDefinition = lexicon?.definition,
                    transliteration = lexicon?.transliteration,
                    originalWord = lexicon?.original_word,
                    morphology = lexicon?.morphology
                )
            }
        }.associateBy { it.strongCode }

        definitions.forEach {
            strongLexicons[it.topic]?.fullDefinition = it.definition
        }

        return strongLexicons.values.toList()
    }



    private suspend fun batchLookupLexicon(
        lexiconDb: LexiconDb,
        rawCodes: List<GetVocabularyForVerse>
    ): Map<String, Lexicon> {
        val candidates = mutableSetOf<String>()

        for (rawCode in rawCodes) {
            val allMatches = Constants.STRONGS_PATTERN.findAll(rawCode.strong_code)
            for (match in allMatches) {
                candidates.add(match.value)
                if (match.value.length > 1 && match.value.last().isLetter()) {
                    // Search for both lowercase and uppercase variants along with the root variant
                    val lastChar = match.value.last()
                    val swappedLast = if (lastChar.isUpperCase()) lastChar.lowercaseChar() else lastChar.uppercaseChar()
                    candidates.add(match.value.dropLast(1))
                    candidates.add(match.value.dropLast(1) + swappedLast)
                }
            }
        }

        val results = lexiconDb.lexiconQueries.getLexiconBatch(candidates.toList()).awaitAsList()
        val resultMap = results.associateBy { it.strong_code }.toMutableMap()

        val finalMap = mutableMapOf<String, Lexicon>()
        for (rawCode in rawCodes) {
            val allMatches = Constants.STRONGS_PATTERN.findAll(rawCode.strong_code)
            for (match in allMatches) {
                val code = match.value
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
                    finalMap[match.value] = lexicon
                }
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

    private suspend fun ensureInitialized(): Boolean {
        if (lexiconDb == null || wordsDefinitionsDb == null) {
            try {
                val simpleName = Constants.LEXICON_DB_NAME.substringAfterLast('/')
                prepareDatabaseFile(Constants.LEXICON_DB_NAME)
                val driver = DatabaseDriverFactory().createDriver(simpleName)
                lexiconDb = LexiconDb(driver)
                
                val scSimpleName = Constants.WORDS_DEFINITIONS_DB_NAME.substringAfterLast('/')
                prepareDatabaseFile(Constants.WORDS_DEFINITIONS_DB_NAME)
                val scDriver = DatabaseDriverFactory().createDriver(scSimpleName)
                wordsDefinitionsDb = WordsDefinitionsDb(scDriver)
            } catch (_: Exception) {
                return false
            }
        }
        return lexiconDb != null && wordsDefinitionsDb != null
    }
}
