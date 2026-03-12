package com.abuhrov.openword

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.abuhrov.openword.data.config.availableTranslations
import com.abuhrov.openword.data.local.clearAllLocalData
import com.abuhrov.openword.data.platform.loadAppFont
import com.abuhrov.openword.data.repository.*
import com.abuhrov.openword.domain.search.SearchIndexer
import com.abuhrov.openword.model.*
import com.abuhrov.openword.ui.dialog.*
import com.abuhrov.openword.ui.screen.BibleReaderScreen
import com.abuhrov.openword.ui.screen.BibleTopBar
import com.abuhrov.openword.ui.theme.AppTheme
import com.abuhrov.openword.util.Constants
import com.abuhrov.openword.util.stripTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var fontSizeScale by remember { mutableStateOf(Settings.getString(Constants.SettingsKeys.FONT_SCALE, Constants.DEFAULT_FONT_SCALE).toFloat()) }
    var autoTranslate by remember { mutableStateOf(Settings.getString(Constants.SettingsKeys.AUTO_TRANSLATE, Constants.DEFAULT_AUTO_TRANSLATE).toBoolean()) }
    var navViewMode by remember { mutableStateOf(NavigationViewMode.valueOf(Settings.getString(Constants.SettingsKeys.NAV_VIEW_MODE, Constants.DEFAULT_NAV_VIEW_MODE))) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val currentFont = loadAppFont()

    AppTheme(fontSizeScale = fontSizeScale, currentFont = currentFont) {
        val clipboardManager = LocalClipboardManager.current

        val savedTranslationId = Settings.getString(Constants.SettingsKeys.LAST_TRANSLATION, availableTranslations.first().id)
        val savedBookId = Settings.getLong(Constants.SettingsKeys.LAST_BOOK, Constants.DEFAULT_BOOK_ID)
        val savedChapter = Settings.getLong(Constants.SettingsKeys.LAST_CHAPTER, Constants.DEFAULT_CHAPTER)
        val savedVerse = Settings.getLong(Constants.SettingsKeys.LAST_VERSE, Constants.DEFAULT_VERSE)

        var bible by remember { mutableStateOf<Bible?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var loadError by remember { mutableStateOf<String?>(null) }
        var isInitialLoad by remember { mutableStateOf(true) }

        var selectedTranslation by remember { mutableStateOf(availableTranslations.find { it.id == savedTranslationId } ?: availableTranslations.first()) }
        var selectedBook by remember { mutableStateOf<Book?>(null) }
        var selectedChapter by remember { mutableStateOf(savedChapter) }
        var selectedVerse by remember { mutableStateOf(savedVerse) }

        var selectedVerses by remember { mutableStateOf(setOf<Verse>()) }

        var currentVerses by remember { mutableStateOf<List<Verse>>(emptyList()) }

        var showVocabularyForVerse by remember { mutableStateOf<Verse?>(null) }
        var currentVocabularyList by remember { mutableStateOf<List<LexiconEntry>>(emptyList()) }
        var showCommentariesForVerse by remember { mutableStateOf<Verse?>(null) }
        var currentCommentariesList by remember { mutableStateOf<List<CommentaryItem>>(emptyList()) }
        var showCrossReferencesForVerse by remember { mutableStateOf<Verse?>(null) }
        var currentCrossReferenceList by remember { mutableStateOf<List<CrossReferenceUiItem>>(emptyList()) }
        var showAIPopupForVerses by remember { mutableStateOf<List<Verse>?>(null) }
        var selectedDefinition by remember { mutableStateOf<LexiconEntry?>(null) }
        var pendingStrongCode by remember { mutableStateOf<String?>(null) }

        var showTranslationSelection by remember { mutableStateOf(false) }
        var showNavSelection by remember { mutableStateOf(false) }
        var showSearchDialog by remember { mutableStateOf(false) }
        var currentSearchQuery by remember { mutableStateOf("") }
        var navMode by remember { mutableStateOf(NavMode.BOOK) }

        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        val clearSelection = { selectedVerses = emptySet() }

        val onNextChapter: () -> Unit = {
            if (bible != null && selectedBook != null) {
                if (selectedChapter < selectedBook!!.chapterCount) {
                    selectedChapter += 1
                    selectedVerse = 1L
                } else {
                    val currentIndex = bible!!.books.indexOfFirst { it.id == selectedBook!!.id }
                    if (currentIndex != -1 && currentIndex < bible!!.books.lastIndex) {
                        selectedBook = bible!!.books[currentIndex + 1]
                        selectedChapter = 1L
                        selectedVerse = 1L
                    }
                }
                clearSelection()
                scope.launch { listState.scrollToItem(0) }
            }
        }

        val onPreviousChapter: () -> Unit = {
            if (bible != null && selectedBook != null) {
                if (selectedChapter > 1) {
                    selectedChapter -= 1
                    selectedVerse = 1L
                    clearSelection()
                    scope.launch { listState.scrollToItem(0) }
                } else {
                    val currentIndex = bible!!.books.indexOfFirst { it.id == selectedBook!!.id }
                    if (currentIndex > 0) {
                        val prevBook = bible!!.books[currentIndex - 1]
                        selectedBook = prevBook
                        selectedChapter = prevBook.chapterCount
                        selectedVerse = 1L
                        clearSelection()
                        scope.launch { listState.scrollToItem(0) }
                    }
                }
            }
        }

        LaunchedEffect(selectedTranslation, selectedBook, selectedChapter, selectedVerse) {
            if (!isLoading && selectedBook != null) {
                Settings.setString(Constants.SettingsKeys.LAST_TRANSLATION, selectedTranslation.id)
                Settings.setLong(Constants.SettingsKeys.LAST_BOOK, selectedBook!!.id)
                Settings.setLong(Constants.SettingsKeys.LAST_CHAPTER, selectedChapter)
                Settings.setLong(Constants.SettingsKeys.LAST_VERSE, selectedVerse)
                Settings.setLong("book_${selectedBook!!.id}_chapter", selectedChapter)
                Settings.setLong("book_${selectedBook!!.id}_verse", selectedVerse)
            }
        }

        LaunchedEffect(Unit) {
            launch(Dispatchers.Default) { VocabularyRepository.initialize() }
            launch(Dispatchers.Default) { CommentaryRepository.initialize() }
            launch(Dispatchers.Default) { DictionaryRepository.initialize() }
            launch(Dispatchers.Default) { CrossReferenceRepository.initialize() }
        }

        LaunchedEffect(selectedTranslation) {
            isLoading = true
            loadError = null
            try {
                val loadedBible = loadBibleData(selectedTranslation)
                bible = loadedBible
                if (isInitialLoad) {
                    val book = loadedBible.books.find { it.id == savedBookId } ?: loadedBible.books.firstOrNull()
                    selectedBook = book
                    if (book != null && selectedChapter > book.chapterCount) selectedChapter = 1L
                    isInitialLoad = false
                } else {
                    val currentBookId = selectedBook?.id
                    val newBookInstance = loadedBible.books.find { it.id == currentBookId }
                    if (newBookInstance != null) {
                        selectedBook = newBookInstance
                    } else {
                        selectedBook = loadedBible.books.firstOrNull()
                        selectedChapter = 1L
                        selectedVerse = 1L
                    }
                }
                scope.launch { SearchIndexer.buildIndex(loadedBible, selectedTranslation.id) }
            } catch (e: Exception) {
                loadError = e.message
                bible = null
            } finally {
                isLoading = false
            }
        }

        LaunchedEffect(bible, selectedBook, selectedChapter) {
            if (bible != null && selectedBook != null) {
                val verses = withContext(Dispatchers.Default) { bible!!.getVerses(selectedBook!!.id, selectedChapter) }
                currentVerses = verses
            } else { currentVerses = emptyList() }
        }

        LaunchedEffect(currentVerses, selectedVerse) {
            if (currentVerses.isNotEmpty()) {
                if (selectedVerse <= 1L) {
                    listState.scrollToItem(0)
                } else {
                    val indexInList = currentVerses.indexOfFirst { it.number == selectedVerse }
                    val targetIndex = if (indexInList != -1) {
                        indexInList + 1
                    } else {
                        val approx = currentVerses.indexOfFirst { it.number > selectedVerse }
                        if (approx != -1 && approx > 0) approx else 1
                    }
                    listState.scrollToItem(targetIndex)
                }
            }
        }

        LaunchedEffect(showVocabularyForVerse) {
            if (showVocabularyForVerse != null && selectedBook != null) {
                try {
                    val vocab = withContext(Dispatchers.Default) { getVocabularyForVerse(showVocabularyForVerse!!) }
                    currentVocabularyList = vocab
                    if (pendingStrongCode != null) { selectedDefinition = vocab.find { it.strongCode.contains(pendingStrongCode!!) }; pendingStrongCode = null }
                    else selectedDefinition = null
                } catch (_: Exception) { currentVocabularyList = emptyList() }
            } else { currentVocabularyList = emptyList(); selectedDefinition = null }
        }

        LaunchedEffect(showCommentariesForVerse) {
            currentCommentariesList = if (showCommentariesForVerse != null && selectedBook != null) {
                try {
                    withContext(Dispatchers.Default) { getCommentariesForVerse(showCommentariesForVerse!!) }
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()
        }

        LaunchedEffect(showCrossReferencesForVerse) {
            currentCrossReferenceList = if (showCrossReferencesForVerse != null && bible != null) {
                try {
                    val rawRefs = withContext(Dispatchers.Default) {
                        CrossReferenceRepository.getCrossReferences(
                            book = selectedBook!!.id,
                            chapter = selectedChapter,
                            verse = showCrossReferencesForVerse!!.number
                        )
                    }
                    val uiItems = rawRefs.mapNotNull { ref ->
                        val targetBook = bible!!.books.find { it.id == ref.bookTo }
                        if (targetBook != null) {
                            val chunkVerses = withContext(Dispatchers.Default) {
                                bible!!.getVerses(targetBook.id, ref.chapterTo)
                            }
                            // Form single string for reference text
                            val startIdx = (ref.verseToStart - 1).toInt().coerceAtLeast(0)
                            val endIdx = ((ref.verseToEnd ?: ref.verseToStart) - 1).toInt().coerceAtMost(chunkVerses.lastIndex).coerceAtLeast(startIdx)
                            
                            if (startIdx <= endIdx && chunkVerses.isNotEmpty()) {
                                val textBuilder = StringBuilder()
                                for (i in startIdx..endIdx) {
                                    if (i < chunkVerses.size) {
                                        textBuilder.append(chunkVerses[i].text).append(" ")
                                    }
                                }
                                CrossReferenceUiItem(reference = ref, book = targetBook, verseText = textBuilder.toString().trim())
                            } else null
                        } else null
                    }
                    uiItems
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()
        }

        Scaffold(
            topBar = {
                BibleTopBar(
                    selectedTranslation = selectedTranslation,
                    selectedBook = selectedBook,
                    selectedChapter = selectedChapter,
                    selectedVerse = selectedVerse,
                    selectedVerses = selectedVerses,
                    onTranslationClick = { clearSelection(); showTranslationSelection = true },
                    onNavigationClick = { clearSelection(); navMode = NavMode.BOOK; showNavSelection = true },
                    onSettingsClick = { clearSelection(); showSettingsDialog = true },
                    onSearchClick = { query -> clearSelection(); currentSearchQuery = query; showSearchDialog = true },
                    onSearchError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                    onCopyVerses = {
                        if (selectedVerses.isNotEmpty() && selectedBook != null) {
                            val sortedVerses = selectedVerses.sortedBy { it.number }
                            val verseNumbers = formatVerseNumbers(sortedVerses.map { it.number })
                            val verseTexts = sortedVerses.joinToString("\n") { stripTags(it.text) }
                            clipboardManager.setText(AnnotatedString("$verseTexts\n${selectedBook!!.shortName} $selectedChapter:$verseNumbers"))
                            clearSelection()
                        }
                    },
                    onShowCommentaries = {
                        if (selectedVerses.size == 1) {
                            showCommentariesForVerse = selectedVerses.first()
                            clearSelection()
                        }
                    },
                    onShowVocabulary = {
                        if (selectedVerses.size == 1) {
                            showVocabularyForVerse = selectedVerses.first()
                            clearSelection()
                        }
                    },
                    onShowCrossReferences = {
                        if (selectedVerses.size == 1) {
                            showCrossReferencesForVerse = selectedVerses.first()
                            clearSelection()
                        }
                    },
                    onShowAI = {
                        if (selectedVerses.isNotEmpty()) {
                            showAIPopupForVerses = selectedVerses.sortedBy { it.number }
                            clearSelection()
                        }
                    },
                    onClearSelection = { clearSelection() }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            BibleReaderScreen(
                padding = padding,
                isLoading = isLoading,
                loadError = loadError,
                bible = bible,
                selectedBook = selectedBook,
                selectedChapter = selectedChapter,
                selectedVerse = selectedVerse,
                currentVerses = currentVerses,
                selectedVerses = selectedVerses,
                fontSizeScale = fontSizeScale,
                commentarySource = selectedTranslation.commentarySource,
                listState = listState,
                scope = scope,
                onNextChapter = onNextChapter,
                onPreviousChapter = onPreviousChapter,
                onVerseSelected = { selectedVerse = it },
                onVerseLongPressed = { verse ->
                    selectedVerses = if (verse in selectedVerses) {
                        selectedVerses - verse
                    } else {
                        selectedVerses + verse
                    }
                },
                onVerseTapped = { verse ->
                    selectedVerses = if (verse in selectedVerses) selectedVerses - verse else selectedVerses + verse
                },
                onDoubleTapStrong = { verse, code -> clearSelection(); pendingStrongCode = code; showVocabularyForVerse = verse }
            )
        }

        if (showVocabularyForVerse != null) {
            VocabularyPopup(
                selectedBookName = selectedBook?.shortName, chapter = selectedChapter, verse = showVocabularyForVerse!!.number,
                vocabularyList = currentVocabularyList, selectedDefinition = selectedDefinition, autoTranslateEnabled = autoTranslate,
                onSelectDefinition = { selectedDefinition = it }, onDismiss = { showVocabularyForVerse = null; pendingStrongCode = null }
            )
        }

        if (showCommentariesForVerse != null) {
            CommentariesPopup(
                bookName = selectedBook?.shortName, chapter = selectedChapter, verse = showCommentariesForVerse!!.number,
                commentaries = currentCommentariesList, autoTranslateEnabled = autoTranslate, onDismiss = { showCommentariesForVerse = null }
            )
        }

        if (showCrossReferencesForVerse != null) {
            CrossReferencesPopup(
                bookName = selectedBook?.shortName,
                chapter = selectedChapter,
                verse = showCrossReferencesForVerse!!.number,
                crossReferences = currentCrossReferenceList,
                onReferenceClick = { targetBookId, targetChapter, targetVerse ->
                    val newBook = bible?.books?.find { it.id == targetBookId }
                    if (newBook != null) {
                        selectedBook = newBook
                        selectedChapter = targetChapter
                        selectedVerse = targetVerse
                        showCrossReferencesForVerse = null
                        clearSelection()
                    }
                },
                onDismiss = { showCrossReferencesForVerse = null }
            )
        }

        if (showAIPopupForVerses != null) {
            val aiVerses = showAIPopupForVerses!!
            val verseRef = if (aiVerses.size == 1) {
                "${stripTags(aiVerses.first().text)}\n${selectedBook?.shortName} $selectedChapter:${aiVerses.first().number}"
            } else {
                val verseNumbers = formatVerseNumbers(aiVerses.map { it.number })
                val verseTexts = aiVerses.joinToString("\n") { "${it.number}: ${stripTags(it.text)}" }
                "$verseTexts\n${selectedBook?.shortName} $selectedChapter:$verseNumbers"
            }
            AIPopup(verseRef = verseRef, onDismiss = { showAIPopupForVerses = null })
        }

        if (showTranslationSelection) {
            TranslationSelectionDialog(availableTranslations = availableTranslations, selectedTranslation = selectedTranslation, onSelect = { selectedTranslation = it; showTranslationSelection = false }, onDismiss = { showTranslationSelection = false })
        }

        if (showNavSelection && bible != null) {
            NavigationSelectionDialog(
                bible = bible!!, navMode = navMode, navViewMode = navViewMode, selectedBook = selectedBook, selectedChapter = selectedChapter, currentVerseCount = currentVerses.size,
                onNavModeChange = { navMode = it }, onSelectBook = { book ->
                    selectedBook = book
                    val savedCh = Settings.getLong("book_${book.id}_chapter", 1L)
                    val savedVs = Settings.getLong("book_${book.id}_verse", 1L)
                    selectedChapter = if (savedCh <= book.chapterCount) savedCh else 1L
                    selectedVerse = savedVs
                    navMode = NavMode.CHAPTER
                },
                onSelectChapter = { selectedChapter = it; selectedVerse = 1L; navMode = NavMode.VERSE },
                onSelectVerse = { selectedVerse = it; showNavSelection = false; scope.launch { listState.scrollToItem(it.toInt()) } },
                onDismiss = { showNavSelection = false }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                currentFontSizeScale = fontSizeScale, currentAutoTranslate = autoTranslate, currentNavViewMode = navViewMode,
                onFontSizeChange = { fontSizeScale = it; Settings.setString(Constants.SettingsKeys.FONT_SCALE, it.toString()) },
                onAutoTranslateChange = { autoTranslate = it; Settings.setString(Constants.SettingsKeys.AUTO_TRANSLATE, it.toString()) },
                onNavViewModeChange = { navViewMode = it; Settings.setString(Constants.SettingsKeys.NAV_VIEW_MODE, it.name) },
                onReloadAllData = {
                    scope.launch {
                        isLoading = true; clearAllLocalData(); try { bible = loadBibleData(selectedTranslation) } catch (_: Exception) {}
                        isLoading = false; showSettingsDialog = false
                    }
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showSearchDialog) {
            SearchDialog(
                query = currentSearchQuery,
                onSearch = { q -> SearchIndexer.search(q) },
                onResultClick = { targetBookId, targetChapter, targetVerse ->
                    val newBook = bible?.books?.find { it.id == targetBookId }
                    if (newBook != null) {
                        selectedBook = newBook
                        selectedChapter = targetChapter
                        selectedVerse = targetVerse
                        showSearchDialog = false
                        clearSelection()
                    }
                },
                onDismiss = { showSearchDialog = false }
            )
        }
    }
}

private fun formatVerseNumbers(numbers: List<Long>): String {
    if (numbers.isEmpty()) return ""
    val sorted = numbers.sorted()
    val parts = mutableListOf<String>()
    var rangeStart = sorted[0]
    var rangeEnd = sorted[0]

    for (i in 1 until sorted.size) {
        if (sorted[i] == rangeEnd + 1) {
            rangeEnd = sorted[i]
        } else {
            parts.add(if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart-$rangeEnd")
            rangeStart = sorted[i]
            rangeEnd = sorted[i]
        }
    }
    parts.add(if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart-$rangeEnd")
    return parts.joinToString(",")
}