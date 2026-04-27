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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import com.abuhrov.openword.data.config.availableTranslations
import com.abuhrov.openword.data.local.clearAllLocalData
import com.abuhrov.openword.data.local.prepareDatabaseFile
import com.abuhrov.openword.data.platform.checkDatabaseFile
import com.abuhrov.openword.data.platform.ioDispatcher
import com.abuhrov.openword.data.platform.loadAppFont
import com.abuhrov.openword.data.repository.*
import com.abuhrov.openword.domain.search.SearchIndexer
import com.abuhrov.openword.model.*
import com.abuhrov.openword.model.ChapterItem
import com.abuhrov.openword.ui.dialog.*
import com.abuhrov.openword.ui.dialog.HistoryDialog
import com.abuhrov.openword.ui.dialog.HistoryItem
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
fun App(initialScrollIndex: Int = 0) {
    var fontSizeScale by remember { mutableStateOf(Settings.getString(Constants.SettingsKeys.FONT_SCALE, Constants.DEFAULT_FONT_SCALE).toFloat()) }
    var autoTranslate by remember { mutableStateOf(Settings.getString(Constants.SettingsKeys.AUTO_TRANSLATE, Constants.DEFAULT_AUTO_TRANSLATE).toBoolean()) }
    var navViewMode by remember { mutableStateOf(NavigationViewMode.valueOf(Settings.getString(Constants.SettingsKeys.NAV_VIEW_MODE, Constants.DEFAULT_NAV_VIEW_MODE))) }
    var searchStrictness by remember { mutableStateOf(SearchStrictness.valueOf(Settings.getString(Constants.SettingsKeys.SEARCH_STRICTNESS, Constants.DEFAULT_SEARCH_STRICTNESS))) }
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
        var chapterItems by remember { mutableStateOf<List<ChapterItem>>(emptyList()) }
        var lastLoadedChapter by remember { mutableStateOf(0L) }
        var firstLoadedChapter by remember { mutableStateOf(0L) }
        var navigationVersion by remember { mutableStateOf(0) }
        var isScrollUpdate by remember { mutableStateOf(false) }

        var showVocabularyForVerse by remember { mutableStateOf<Verse?>(null) }
        var currentVocabularyList by remember { mutableStateOf<List<LexiconEntry>>(emptyList()) }
        var currentVerseLexiconPayload by remember { mutableStateOf<VerseLexiconPayload?>(null) }
        var showCommentariesForVerse by remember { mutableStateOf<Verse?>(null) }
        var currentCommentariesList by remember { mutableStateOf<List<CommentaryItem>>(emptyList()) }
        var isCommentariesLoading by remember { mutableStateOf(false) }
        var showCrossReferencesForVerseNumber by remember { mutableStateOf<Long?>(null) }
        var currentCrossReferenceList by remember { mutableStateOf<List<CrossReferenceUiItem>>(emptyList()) }
        var isCrossReferencesLoading by remember { mutableStateOf(false) }
        var showCompareTranslationsForVerses by remember { mutableStateOf<List<Verse>?>(null) }
        var currentCompareTranslationsList by remember { mutableStateOf<List<CompareTranslationsUiItem>>(emptyList()) }
        var compareRefreshTrigger by remember { mutableStateOf(0) }
        var showAIPopupForVerses by remember { mutableStateOf<List<Verse>?>(null) }
        var pendingStrongCode by remember { mutableStateOf<String?>(null) }

        var showTranslationSelection by remember { mutableStateOf(false) }
        var showNavSelection by remember { mutableStateOf(false) }
        var showHistoryDialog by remember { mutableStateOf(false) }
        var showSearchDialog by remember { mutableStateOf(false) }
        var currentSearchQuery by remember { mutableStateOf("") }
        var navMode by remember { mutableStateOf(NavMode.BOOK) }

        var historyList by remember {
            mutableStateOf(
                Settings.getString(Constants.SettingsKeys.HISTORY, "")
                    .split(",")
                    .filter { it.isNotBlank() }
                    .mapNotNull {
                        val parts = it.split(":")
                        if (parts.size == 3) {
                            val b = parts[0].toLongOrNull()
                            val c = parts[1].toLongOrNull()
                            val v = parts[2].toLongOrNull()
                            if (b != null && c != null && v != null) Triple(b, c, v) else null
                        } else null
                    }
            )
        }

        val addToHistory: (Long, Long, Long) -> Unit = { bookId, chapter, verse ->
            val newItem = Triple(bookId, chapter, verse)
            val newList = (listOf(newItem) + historyList.filter { it != newItem }).take(10)
            historyList = newList
            Settings.setString(Constants.SettingsKeys.HISTORY, newList.joinToString(",") { "${it.first}:${it.second}:${it.third}" })
        }

        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val focusManager = LocalFocusManager.current

        // Track scroll position and persist it for cold-start recovery
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { index ->
                    Settings.setString(Constants.SettingsKeys.SCROLL_POSITION, index.toString())
                }
        }

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
            }
        }

        val onPreviousChapter: () -> Unit = {
            if (bible != null && selectedBook != null) {
                if (selectedChapter > 1) {
                    selectedChapter -= 1
                    selectedVerse = 1L
                    clearSelection()
                } else {
                    val currentIndex = bible!!.books.indexOfFirst { it.id == selectedBook!!.id }
                    if (currentIndex > 0) {
                        val prevBook = bible!!.books[currentIndex - 1]
                        selectedBook = prevBook
                        selectedChapter = prevBook.chapterCount
                        selectedVerse = 1L
                        clearSelection()
                    }
                }
            }
        }

        LaunchedEffect(selectedTranslation, selectedBook, selectedChapter, selectedVerse) {
            if (!isLoading && selectedBook != null) {
                Settings.setString(Constants.SettingsKeys.LAST_TRANSLATION, selectedTranslation.id)
                Settings.setLong(Constants.SettingsKeys.LAST_BOOK, selectedBook!!.id)
                Settings.setString(Constants.SettingsKeys.LAST_BOOK_NAME, selectedBook!!.shortName)
                Settings.setLong(Constants.SettingsKeys.LAST_CHAPTER, selectedChapter)
                Settings.setLong(Constants.SettingsKeys.LAST_VERSE, selectedVerse)
                Settings.setLong("book_${selectedBook!!.id}_chapter", selectedChapter)
                Settings.setLong("book_${selectedBook!!.id}_verse", selectedVerse)
            }
        }

        // Non-critical repositories will be initialized lazily when first used
        LaunchedEffect(Unit) {
            CommentaryRepository.initialize()
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
            if (isScrollUpdate) {
                isScrollUpdate = false
                return@LaunchedEffect
            }
            if (bible != null && selectedBook != null) {
                val startChapter = maxOf(selectedChapter - 2, 1L)
                val endChapter = minOf(selectedChapter + 2, selectedBook!!.chapterCount)
                val items = mutableListOf<ChapterItem>()
                for (ch in startChapter..endChapter) {
                    items.add(ChapterItem.Header(selectedBook!!.shortName, ch))
                    val verses = bible!!.getVerses(selectedBook!!.id, ch)
                    for (v in verses) {
                        items.add(ChapterItem.VerseItem(v, ch))
                    }
                }
                chapterItems = items
                lastLoadedChapter = endChapter
                firstLoadedChapter = startChapter
                currentVerses = items
                    .filterIsInstance<ChapterItem.VerseItem>()
                    .filter { it.chapter == selectedChapter }
                    .map { it.verse }
                navigationVersion++
            } else {
                chapterItems = emptyList()
                currentVerses = emptyList()
                lastLoadedChapter = 0L
                firstLoadedChapter = 0L
            }
        }

        // Scroll to the target verse only on explicit navigation (not on prefetch or scroll-driven changes)
        LaunchedEffect(navigationVersion) {
            if (chapterItems.isNotEmpty()) {
                // Check if the items actually contain the target chapter before trying to scroll.
                // If not, the data loader hasn't finished yet, and it will re-trigger this block once it does.
                val hasTargetChapter = chapterItems.any { it is ChapterItem.Header && it.chapter == selectedChapter }
                if (!hasTargetChapter) return@LaunchedEffect

                val headerIndex = chapterItems.indexOfFirst {
                    it is ChapterItem.Header && it.chapter == selectedChapter
                }

                if (selectedVerse <= 1L) {
                    listState.scrollToItem(headerIndex)
                } else {
                    val targetIndex = chapterItems.indexOfFirst {
                        it is ChapterItem.VerseItem && it.chapter == selectedChapter && it.verse.number == selectedVerse
                    }
                    if (targetIndex != -1) {
                        listState.scrollToItem(targetIndex)
                    } else {
                        val approx = chapterItems.indexOfFirst {
                            it is ChapterItem.VerseItem && it.chapter == selectedChapter && it.verse.number > selectedVerse
                        }
                        if (approx != -1) listState.scrollToItem(approx) else listState.scrollToItem(headerIndex)
                    }
                }
            }
        }

        // Prefetch chapters as user scrolls near the edges of loaded content
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { firstVisibleIndex ->
                    if (chapterItems.isEmpty() || bible == null || selectedBook == null) return@collect
                    val item = chapterItems.getOrNull(firstVisibleIndex) ?: return@collect
                    val visibleChapter = when (item) {
                        is ChapterItem.Header -> item.chapter
                        is ChapterItem.VerseItem -> item.chapter
                    }
                    // Forward: when user reaches within 1 chapter of the last loaded, append 3 more
                    if (visibleChapter >= lastLoadedChapter - 1 && lastLoadedChapter < selectedBook!!.chapterCount) {
                        val startCh = lastLoadedChapter + 1
                        val endCh = minOf(lastLoadedChapter + 3, selectedBook!!.chapterCount)
                        val newItems = mutableListOf<ChapterItem>()
                        for (ch in startCh..endCh) {
                            newItems.add(ChapterItem.Header(selectedBook!!.shortName, ch))
                            val verses = bible!!.getVerses(selectedBook!!.id, ch)
                            for (v in verses) {
                                newItems.add(ChapterItem.VerseItem(v, ch))
                            }
                        }
                        chapterItems = chapterItems + newItems
                        lastLoadedChapter = endCh
                    }
                    // Backward: when user scrolls up near the first loaded chapter, prepend 3 more
                    if (visibleChapter <= firstLoadedChapter + 1 && firstLoadedChapter > 1L) {
                        val endCh = firstLoadedChapter - 1
                        val startCh = maxOf(endCh - 2, 1L)
                        val newItems = mutableListOf<ChapterItem>()
                        for (ch in startCh..endCh) {
                            newItems.add(ChapterItem.Header(selectedBook!!.shortName, ch))
                            val verses = bible!!.getVerses(selectedBook!!.id, ch)
                            for (v in verses) {
                                newItems.add(ChapterItem.VerseItem(v, ch))
                            }
                        }
                        val prependCount = newItems.size
                        chapterItems = newItems + chapterItems
                        firstLoadedChapter = startCh
                        // Adjust scroll position to compensate for prepended items
                        listState.scrollToItem(
                            listState.firstVisibleItemIndex + prependCount,
                            listState.firstVisibleItemScrollOffset
                        )
                    }
                }
        }

        // Detect scroll stop and update navigation title with the currently visible chapter and verse
        LaunchedEffect(Unit) {
            snapshotFlow { listState.isScrollInProgress }
                .collect { scrolling ->
                    if (!scrolling && chapterItems.isNotEmpty()) {
                        val firstVisibleIndex = listState.firstVisibleItemIndex.coerceIn(0, chapterItems.lastIndex)
                        // Find the first visible VerseItem at or after the first visible index
                        var visibleChapter = selectedChapter
                        var visibleVerse = selectedVerse
                        for (i in firstVisibleIndex..chapterItems.lastIndex) {
                            val item = chapterItems[i]
                            if (item is ChapterItem.VerseItem) {
                                visibleChapter = item.chapter
                                visibleVerse = item.verse.number
                                break
                            } else if (item is ChapterItem.Header) {
                                visibleChapter = item.chapter
                            }
                        }
                        val chapterChanged = visibleChapter != selectedChapter
                        if (chapterChanged) {
                            isScrollUpdate = true
                            selectedChapter = visibleChapter
                            currentVerses = chapterItems
                                .filterIsInstance<ChapterItem.VerseItem>()
                                .filter { it.chapter == visibleChapter }
                                .map { it.verse }
                        }
                        selectedVerse = visibleVerse
                    }
                }
        }

        LaunchedEffect(showVocabularyForVerse) {
            if (showVocabularyForVerse != null && selectedBook != null) {
                try {
                    val (vocab, payload) = withContext(ioDispatcher) {
                        getVocabularyAndPayloadForVerse(showVocabularyForVerse!!) 
                    }
                    currentVocabularyList = vocab
                    currentVerseLexiconPayload = payload
                    
                    if (pendingStrongCode != null) { 
                        // Note: To be fully implemented into aiLinkedWords highlighting
                        pendingStrongCode = null 
                    }
                } catch (_: Exception) { 
                    currentVocabularyList = emptyList()
                    currentVerseLexiconPayload = null 
                }
            } else {
                currentVocabularyList = emptyList()
                currentVerseLexiconPayload = null
            }
        }


        LaunchedEffect(showCommentariesForVerse) {
            if (showCommentariesForVerse != null && selectedBook != null) {
                isCommentariesLoading = true
                currentCommentariesList = try {
                    getCommentariesForVerse(showCommentariesForVerse!!)
                } catch (_: Exception) {
                    emptyList()
                }
                isCommentariesLoading = false
            } else {
                currentCommentariesList = emptyList()
                isCommentariesLoading = false
            }
        }

        LaunchedEffect(showCrossReferencesForVerseNumber) {
            if (showCrossReferencesForVerseNumber != null && bible != null) {
                isCrossReferencesLoading = true
                currentCrossReferenceList = try {
                    val rawRefs = CrossReferenceRepository.getCrossReferences(
                        book = selectedBook!!.id,
                        chapter = selectedChapter,
                        verse = showCrossReferencesForVerseNumber!!
                    )
                    val uiItems = rawRefs.mapNotNull { ref ->
                        val targetBook = bible!!.books.find { it.id == ref.bookTo }
                        if (targetBook != null) {
                            val chunkVerses = bible!!.getVerses(targetBook.id, ref.chapterTo)
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
                isCrossReferencesLoading = false
            } else {
                currentCrossReferenceList = emptyList()
                isCrossReferencesLoading = false
            }
        }

        LaunchedEffect(showCompareTranslationsForVerses, compareRefreshTrigger) {
            currentCompareTranslationsList = if (showCompareTranslationsForVerses != null && selectedBook != null) {
                val targets = showCompareTranslationsForVerses!!.map { it.number }
                val results = mutableListOf<CompareTranslationsUiItem>()
                for (translation in availableTranslations) {
                    try {
                        val simpleName = translation.fileName.substringAfterLast('/')
                        val isDownloaded = withContext(ioDispatcher) { checkDatabaseFile(simpleName) }
                        
                        if (isDownloaded) {
                            val tempBible = withContext(ioDispatcher) { loadBibleData(translation) }
                            val verses = withContext(ioDispatcher) {
                                tempBible.getVerses(selectedBook!!.id, selectedChapter)
                                    .filter { it.number in targets }
                            }
                            results.add(CompareTranslationsUiItem(translation, verses.sortedBy { it.number }, true))
                        } else {
                            results.add(CompareTranslationsUiItem(translation, isDownloaded = false))
                        }
                    } catch (_: Exception) {
                        results.add(CompareTranslationsUiItem(translation, isDownloaded = false))
                    }
                }
                results.sortedWith(compareByDescending<CompareTranslationsUiItem> { it.isDownloaded }
                    .thenBy { it.translation.displayName })
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
                            showCrossReferencesForVerseNumber = selectedVerses.first().number
                            clearSelection()
                        }
                    },
                    onShowCompareTranslations = {
                        if (selectedVerses.isNotEmpty()) {
                            showCompareTranslationsForVerses = selectedVerses.toList()
                            clearSelection()
                        }
                    },
                    onShowAI = {
                        if (selectedVerses.isNotEmpty()) {
                            showAIPopupForVerses = selectedVerses.sortedBy { it.number }
                            clearSelection()
                        }
                    },
                    onHistoryClick = { showHistoryDialog = true },
                    onClearSelection = { clearSelection() }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.pressed }) {
                                    focusManager.clearFocus()
                                }
                            }
                        }
                    }
            ) {
                BibleReaderScreen(
                    padding = padding,
                isLoading = isLoading,
                loadError = loadError,
                bible = bible,
                selectedBook = selectedBook,
                selectedChapter = selectedChapter,
                chapterItems = chapterItems,
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
        }

        if (showVocabularyForVerse != null) {
            VocabularyPopup(
                selectedBookName = selectedBook?.shortName, chapter = selectedChapter, verse = showVocabularyForVerse!!.number,
                vocabularyList = currentVocabularyList, verseLexiconPayload = currentVerseLexiconPayload,
                bible = bible, onDismiss = { showVocabularyForVerse = null; pendingStrongCode = null }
            )
        }

        if (showCommentariesForVerse != null) {
            CommentariesPopup(
                bookName = selectedBook?.shortName, chapter = selectedChapter, verse = showCommentariesForVerse!!.number,
                commentaries = currentCommentariesList, isLoading = isCommentariesLoading, bible = bible, onDismiss = { showCommentariesForVerse = null }
            )
        }

        if (showCrossReferencesForVerseNumber != null) {
            CrossReferencesPopup(
                bookName = selectedBook?.shortName,
                chapter = selectedChapter,
                verse = showCrossReferencesForVerseNumber!!,
                crossReferences = currentCrossReferenceList,
                isLoading = isCrossReferencesLoading,
                onReferenceClick = { targetBookId, targetChapter, targetVerse ->
                    val newBook = bible?.books?.find { it.id == targetBookId }
                    if (newBook != null) {
                        selectedBook = newBook
                        selectedChapter = targetChapter
                        selectedVerse = targetVerse
                        showCrossReferencesForVerseNumber = null
                        clearSelection()
                    }
                },
                onDismiss = { showCrossReferencesForVerseNumber = null }
            )
        }

        if (showCompareTranslationsForVerses != null) {
            CompareTranslationsPopup(
                bookName = selectedBook?.shortName,
                chapter = selectedChapter,
                verseNumbers = formatVerseNumbers(showCompareTranslationsForVerses!!.map { it.number }),
                compareItems = currentCompareTranslationsList,
                onDownloadTranslation = { translation ->
                    scope.launch {
                        try {
                            prepareDatabaseFile(translation.fileName)
                            compareRefreshTrigger++
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Помилка завантаження: ${e.message}")
                        }
                    }
                },
                onDismiss = { showCompareTranslationsForVerses = null }
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
                onSelectVerse = { verse ->
                    selectedVerse = verse
                    if (selectedBook != null) addToHistory(selectedBook!!.id, selectedChapter, verse)
                    showNavSelection = false
                    navigationVersion++
                },
                onDismiss = { showNavSelection = false }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                currentFontSizeScale = fontSizeScale, currentAutoTranslate = autoTranslate, currentNavViewMode = navViewMode, currentSearchStrictness = searchStrictness,
                onFontSizeChange = { fontSizeScale = it; Settings.setString(Constants.SettingsKeys.FONT_SCALE, it.toString()) },
                onAutoTranslateChange = { autoTranslate = it; Settings.setString(Constants.SettingsKeys.AUTO_TRANSLATE, it.toString()) },
                onNavViewModeChange = { navViewMode = it; Settings.setString(Constants.SettingsKeys.NAV_VIEW_MODE, it.name) },
                onSearchStrictnessChange = { searchStrictness = it; Settings.setString(Constants.SettingsKeys.SEARCH_STRICTNESS, it.name) },
                onReloadAllData = {
                    scope.launch {
                        isLoading = true; clearAllLocalData(); try { 
                            bible = loadBibleData(selectedTranslation)
                            CommentaryRepository.initialize()
                        } catch (_: Exception) {}
                        isLoading = false; showSettingsDialog = false
                    }
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showHistoryDialog) {
            val history = historyList.mapNotNull { triple ->
                val book = bible?.books?.find { it.id == triple.first }
                if (book != null) {
                    HistoryItem(triple.first, book.shortName, triple.second, triple.third)
                } else null
            }
            HistoryDialog(
                history = history,
                onSelect = { b, c, v ->
                    val newBook = bible?.books?.find { it.id == b }
                    if (newBook != null) {
                        selectedBook = newBook
                        selectedChapter = c
                        selectedVerse = v
                        addToHistory(b, c, v)
                        showHistoryDialog = false
                        clearSelection()
                    }
                },
                onDismiss = { showHistoryDialog = false }
            )
        }

        if (showSearchDialog) {
            SearchDialog(
                query = currentSearchQuery,
                onSearch = { q -> SearchIndexer.search(q, searchStrictness.maxDistance, searchStrictness.allowPrefix) },
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