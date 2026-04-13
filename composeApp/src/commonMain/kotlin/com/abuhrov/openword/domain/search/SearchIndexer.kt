package com.abuhrov.openword.domain.search

import com.abuhrov.openword.data.repository.Bible
import com.abuhrov.openword.util.levenshteinDistance
import com.abuhrov.openword.util.stripTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SearchResult(val verseId: Long, val bookId: Long, val displayBookName: String, val chapter: Long, val verseNumber: Long, val text: String, val matches: List<String>)

object SearchIndexer {
    private val mutex = Mutex()
    private var isBuilt = false
    private var currentBibleId: String? = null

    // Inverted index maps normalized lowercased word to an IntArray of verse IDs.
    // Using IntArray saves significant heap memory on both iOS/Android compared to List<Int>.
    private var invertedIndex: Map<String, LongArray> = emptyMap()
    // Mapping VerseID to BookName, chapter, verse number and text to quickly construct results without hitting DB
    private var verseCache: Map<Long, VerseData> = emptyMap()

    private class VerseData(val bookId: Long, val bookName: String, val chapter: Long, val verseNumber: Long, val text: String)

    suspend fun buildIndex(bible: Bible, translationId: String) {
        mutex.withLock {
            if (isBuilt && currentBibleId == translationId) return
            // Clear previous index to let GC clean up
            invertedIndex = emptyMap()
            verseCache = emptyMap()
            isBuilt = false
            currentBibleId = null
        }

        withContext(Dispatchers.Default) {
            val localIndex = mutableMapOf<String, MutableSet<Long>>()
            val localVerseCache = mutableMapOf<Long, VerseData>()
            
            val books = bible.books
            for (book in books) {
                val bookName = book.shortName.ifEmpty { book.name }
                for (chapter in 1L..book.chapterCount) {
                    // Check for cancellation to avoid unnecessary work if translation changed
                    ensureActive()
                    val verses = bible.getVerses(book.id, chapter)
                    for (verse in verses) {
                        val cleanText = stripTags(verse.text)
                        
                        // bitwise encode verse primary key (bookId, chapter, number) into 64-bit Long
                        val verseId = (book.id shl 32) or (chapter shl 16) or verse.number

                        // Populate verse cache to avoid hundreds of individual reads during search
                        localVerseCache[verseId] = VerseData(book.id, bookName, chapter, verse.number, cleanText)

                        // Extract vocabulary
                        val words = cleanText.split(Regex("[^\\p{L}\\p{N}]+"))
                        for (w in words) {
                            if (w.length < 3) continue
                            val word = w.lowercase()
                            val set = localIndex.getOrPut(word) { mutableSetOf() }
                            set.add(verseId)
                        }
                    }
                }
            }

            // Convert to primitive arrays
            val finalIndex = mutableMapOf<String, LongArray>()
            for ((word, verseSet) in localIndex) {
                finalIndex[word] = verseSet.toLongArray()
            }

            mutex.withLock {
                invertedIndex = finalIndex
                verseCache = localVerseCache
                isBuilt = true
                currentBibleId = translationId
            }
        }
    }

    suspend fun search(query: String, maxDistance: Int, allowPrefix: Boolean): List<SearchResult> {
        if (query.length < 3) return emptyList()

        val indexSnapshot = mutex.withLock { 
            if (!isBuilt) return emptyList()
            invertedIndex
        }
        val verseCacheSnapshot = mutex.withLock { verseCache }

        return withContext(Dispatchers.Default) {
            val finalResults = mutableListOf<SearchResult>()
            val terms = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
            if (terms.isEmpty()) return@withContext emptyList()

            val matchedSets = mutableListOf<Set<Long>>()
            val allMatchedWords = mutableSetOf<String>()

            for (term in terms) {
                val matchesForTerm = mutableSetOf<Long>()
                val matchingVocab = mutableSetOf<String>()

                for ((vocabWord, verseIds) in indexSnapshot) {
                    // Fast exact match or prefix
                    if (vocabWord == term || (allowPrefix && vocabWord.startsWith(term))) {
                        matchesForTerm.addAll(verseIds.toTypedArray())
                        matchingVocab.add(vocabWord)
                    } else if (term.length > 3 && vocabWord.length > 3) {
                        // Levenshtein
                        if (kotlin.math.abs(vocabWord.length - term.length) <= maxDistance) {
                            val dist = levenshteinDistance(term, vocabWord)
                            if (dist <= maxDistance) {
                                matchesForTerm.addAll(verseIds.toTypedArray())
                                matchingVocab.add(vocabWord)
                            }
                        }
                    }
                }
                if (matchesForTerm.isNotEmpty()) {
                    matchedSets.add(matchesForTerm)
                    allMatchedWords.addAll(matchingVocab)
                } else {
                    // Match failure for an AND query term means no results overall
                    return@withContext emptyList()
                }
            }

            if (matchedSets.isNotEmpty()) {
                // Intersect all matched sets for AND logic
                var resultIds = matchedSets.first()
                for (i in 1 until matchedSets.size) {
                    resultIds = resultIds.intersect(matchedSets[i])
                }

                val finalMatches = allMatchedWords.toList()
                for (id in resultIds) {
                    val vData = verseCacheSnapshot[id]
                    if (vData != null) {
                        finalResults.add(
                            SearchResult(
                                verseId = id,
                                bookId = vData.bookId,
                                displayBookName = vData.bookName,
                                chapter = vData.chapter,
                                verseNumber = vData.verseNumber,
                                text = vData.text,
                                matches = finalMatches
                            )
                        )
                    }
                }
            }

            // Return all elements, sorted by book and chapter
            finalResults.sortedWith(compareBy({ it.bookId }, { it.chapter }, { it.verseNumber }))
        }
    }

    suspend fun clear() {
        mutex.withLock {
            invertedIndex = emptyMap()
            verseCache = emptyMap()
            isBuilt = false
            currentBibleId = null
        }
    }
}
