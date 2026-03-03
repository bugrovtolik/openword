package com.abuhrov.openword

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import com.abuhrov.openword.data.config.availableTranslations
import com.abuhrov.openword.data.local.clearAllLocalData
import com.abuhrov.openword.data.platform.loadAppFont
import com.abuhrov.openword.data.repository.*
import com.abuhrov.openword.model.*
import com.abuhrov.openword.model.NavigationViewMode
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
        // Init State from Settings
        val savedTranslationId = Settings.getString(Constants.SettingsKeys.LAST_TRANSLATION, availableTranslations.first().id)
        val savedBookId = Settings.getLong(Constants.SettingsKeys.LAST_BOOK, Constants.DEFAULT_BOOK_ID)
        val savedChapter = Settings.getLong(Constants.SettingsKeys.LAST_CHAPTER, Constants.DEFAULT_CHAPTER)
        val savedVerse = Settings.getLong(Constants.SettingsKeys.LAST_VERSE, Constants.DEFAULT_VERSE)

        // App State
        var bible by remember { mutableStateOf<Bible?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var loadError by remember { mutableStateOf<String?>(null) }
        var isInitialLoad by remember { mutableStateOf(true) }

        // Selection State
        var selectedTranslation by remember { mutableStateOf(availableTranslations.find { it.id == savedTranslationId } ?: availableTranslations.first()) }
        var selectedBook by remember { mutableStateOf<Book?>(null) }
        var selectedChapter by remember { mutableStateOf(savedChapter) }
        var selectedVerse by remember { mutableStateOf(savedVerse) }

        // Data State
        var currentVerses by remember { mutableStateOf<List<Verse>>(emptyList()) }

        // Popup States
        var showVocabularyForVerse by remember { mutableStateOf<Verse?>(null) }
        var currentVocabularyList by remember { mutableStateOf<List<LexiconEntry>>(emptyList()) }
        var showCommentariesForVerse by remember { mutableStateOf<Verse?>(null) }
        var currentCommentariesList by remember { mutableStateOf<List<CommentaryItem>>(emptyList()) }
        var showAIPopupForVerse by remember { mutableStateOf<Verse?>(null) }
        var selectedDefinition by remember { mutableStateOf<LexiconEntry?>(null) }
        var pendingStrongCode by remember { mutableStateOf<String?>(null) }

        var showTranslationSelection by remember { mutableStateOf(false) }
        var showNavSelection by remember { mutableStateOf(false) }
        var navMode by remember { mutableStateOf(NavMode.BOOK) }

        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        // --- NAVIGATION HELPERS ---
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
                scope.launch { listState.scrollToItem(0) }
            }
        }

        val onPreviousChapter: () -> Unit = {
            if (bible != null && selectedBook != null) {
                if (selectedChapter > 1) {
                    selectedChapter -= 1
                    selectedVerse = 1L
                    scope.launch { listState.scrollToItem(0) }
                } else {
                    val currentIndex = bible!!.books.indexOfFirst { it.id == selectedBook!!.id }
                    if (currentIndex > 0) {
                        val prevBook = bible!!.books[currentIndex - 1]
                        selectedBook = prevBook
                        selectedChapter = prevBook.chapterCount
                        selectedVerse = 1L
                        scope.launch { listState.scrollToItem(0) }
                    }
                }
            }
        }

        // --- PERSISTENCE EFFECT ---
        LaunchedEffect(selectedTranslation, selectedBook, selectedChapter, selectedVerse) {
            if (!isLoading && selectedBook != null) {
                Settings.setString(Constants.SettingsKeys.LAST_TRANSLATION, selectedTranslation.id)
                Settings.setLong(Constants.SettingsKeys.LAST_BOOK, selectedBook!!.id)
                Settings.setLong(Constants.SettingsKeys.LAST_CHAPTER, selectedChapter)
                Settings.setLong(Constants.SettingsKeys.LAST_VERSE, selectedVerse)
            }
        }

        // --- APP STARTUP ---
        LaunchedEffect(Unit) {
            launch(Dispatchers.Default) { VocabularyRepository.initialize() }
            launch(Dispatchers.Default) { CommentaryRepository.initialize() }
        }

        // 1. Load Bible Metadata
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
                    val newBookInstance = if (currentBookId != null) loadedBible.books.find { it.id == currentBookId } else null
                    if (newBookInstance != null) selectedBook = newBookInstance
                    else { selectedBook = loadedBible.books.firstOrNull(); selectedChapter = 1L; selectedVerse = 1L }
                }
            } catch (e: Exception) { loadError = e.message; bible = null } finally { isLoading = false }
        }

        // 2. Load Verses
        LaunchedEffect(bible, selectedBook, selectedChapter) {
            if (bible != null && selectedBook != null) {
                val verses = withContext(Dispatchers.Default) { bible!!.getVerses(selectedBook!!.id, selectedChapter) }
                currentVerses = verses
                if (selectedVerse > 1 && verses.size >= selectedVerse && listState.firstVisibleItemIndex == 0) {
                    scope.launch { listState.scrollToItem(selectedVerse.toInt()) }
                }
            } else { currentVerses = emptyList() }
        }

        // 3. Load Vocabulary
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

        // 4. Load Commentaries
        LaunchedEffect(showCommentariesForVerse) {
            currentCommentariesList = if (showCommentariesForVerse != null && selectedBook != null) {
                try {
                    withContext(Dispatchers.Default) { getCommentariesForVerse(showCommentariesForVerse!!) }
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
                    onTranslationClick = { showTranslationSelection = true },
                    onNavigationClick = { navMode = NavMode.BOOK; showNavSelection = true },
                    onSettingsClick = { showSettingsDialog = true }
                )
            }
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
                fontSizeScale = fontSizeScale,
                commentarySource = selectedTranslation.commentarySource,
                listState = listState,
                scope = scope,
                onNextChapter = onNextChapter,
                onPreviousChapter = onPreviousChapter,
                onVerseSelected = { selectedVerse = it },
                onShowCommentaries = { showCommentariesForVerse = it },
                onShowVocabulary = { showVocabularyForVerse = it },
                onShowAI = { showAIPopupForVerse = it },
                onDoubleTapStrong = { verse, code -> pendingStrongCode = code; showVocabularyForVerse = verse }
            )
        }

        // --- DIALOGS ---
        if (showVocabularyForVerse != null) {
            VocabularyPopup(
                selectedBookName = selectedBook?.name, chapter = selectedChapter, verse = showVocabularyForVerse!!.number,
                vocabularyList = currentVocabularyList, selectedDefinition = selectedDefinition, autoTranslateEnabled = autoTranslate,
                onSelectDefinition = { selectedDefinition = it }, onDismiss = { showVocabularyForVerse = null; pendingStrongCode = null }
            )
        }

        if (showCommentariesForVerse != null) {
            CommentariesPopup(
                bookName = selectedBook?.name, chapter = selectedChapter, verse = showCommentariesForVerse!!.number,
                commentaries = currentCommentariesList, autoTranslateEnabled = autoTranslate, onDismiss = { showCommentariesForVerse = null }
            )
        }

        if (showAIPopupForVerse != null) {
            AIPopup(verseRef = "${selectedBook?.name} $selectedChapter:${showAIPopupForVerse!!.number}\n${stripTags(showAIPopupForVerse!!.text)}", onDismiss = { showAIPopupForVerse = null })
        }

        if (showTranslationSelection) {
            TranslationSelectionDialog(availableTranslations = availableTranslations, selectedTranslation = selectedTranslation, onSelect = { selectedTranslation = it; showTranslationSelection = false }, onDismiss = { showTranslationSelection = false })
        }

        if (showNavSelection && bible != null) {
            NavigationSelectionDialog(
                bible = bible!!, navMode = navMode, navViewMode = navViewMode, selectedBook = selectedBook, selectedChapter = selectedChapter, currentVerseCount = currentVerses.size,
                onNavModeChange = { navMode = it }, onSelectBook = { selectedBook = it; selectedChapter = 1L; selectedVerse = 1L; navMode = NavMode.CHAPTER },
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
    }
}