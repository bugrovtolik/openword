package com.abuhrov.openword.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.abuhrov.openword.AIPopup
import com.abuhrov.openword.Bible
import com.abuhrov.openword.Book
import com.abuhrov.openword.BubbleActionButton
import com.abuhrov.openword.CommentariesPopup
import com.abuhrov.openword.CommentaryItem
import com.abuhrov.openword.LexiconEntry
import com.abuhrov.openword.NavMode
import com.abuhrov.openword.NavigationSelectionDialog
import com.abuhrov.openword.Settings
import com.abuhrov.openword.TopBarButton
import com.abuhrov.openword.TranslationSelectionDialog
import com.abuhrov.openword.Verse
import com.abuhrov.openword.VocabularyManager
import com.abuhrov.openword.VocabularyPopup
import com.abuhrov.openword.availableTranslations
import com.abuhrov.openword.getCommentariesForVerse
import com.abuhrov.openword.getVocabularyForVerse
import com.abuhrov.openword.loadBibleData
import com.abuhrov.openword.normalizeStrongCode
import com.abuhrov.openword.parseBibleText
import com.abuhrov.openword.stripTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
) {

    // Init State from Settings
    val savedTranslationId =
        Settings.getString("last_translation", availableTranslations.first().id)
    val savedBookId = Settings.getLong("last_book", 1L)
    val savedChapter = Settings.getLong("last_chapter", 1L)
    val savedVerse = Settings.getLong("last_verse", 1L)

    // App State
    var bible by remember { mutableStateOf<Bible?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // FIX: Flag to track if we need to apply saved settings
    var isInitialLoad by remember { mutableStateOf(true) }

    // Selection State
    var selectedTranslation by remember {
        mutableStateOf(availableTranslations.find { it.id == savedTranslationId }
            ?: availableTranslations.first())
    }
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    var selectedChapter by remember { mutableStateOf(savedChapter) } // Initialize with saved
    var selectedVerse by remember { mutableStateOf(savedVerse) }     // Initialize with saved

    // Data State (Lazy Loaded)
    var currentVerses by remember { mutableStateOf<List<Verse>>(emptyList()) }

    // Popup States
    var selectedVerseForMenu by remember { mutableStateOf<Verse?>(null) }
    var showVocabularyForVerse by remember { mutableStateOf<Verse?>(null) }
    var currentVocabularyList by remember { mutableStateOf<List<LexiconEntry>>(emptyList()) }

    // Commentaries State
    var showCommentariesForVerse by remember { mutableStateOf<Verse?>(null) }
    var currentCommentariesList by remember { mutableStateOf<List<CommentaryItem>>(emptyList()) }

    // AI State
    var showAIPopupForVerse by remember { mutableStateOf<Verse?>(null) }

    // Detailed Definition State
    var selectedDefinition by remember { mutableStateOf<LexiconEntry?>(null) }

    // Auto-select definition (for double-tap feature)
    var pendingStrongCode by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current
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
                // Try next book
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
                // Try previous book
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
        // Only save if we are not loading (to avoid overwriting with defaults during init)
        if (!isLoading && selectedBook != null) {
            Settings.setString("last_translation", selectedTranslation.id)
            Settings.setLong("last_book", selectedBook!!.id)
            Settings.setLong("last_chapter", selectedChapter)
            Settings.setLong("last_verse", selectedVerse)
        }
    }

    // --- APP STARTUP ---
    LaunchedEffect(Unit) {
        launch(Dispatchers.Default) {
            VocabularyManager.initialize()
        }
    }

    // 1. Load Bible Metadata
    LaunchedEffect(selectedTranslation) {
        isLoading = true
        loadError = null
        try {
            val loadedBible = loadBibleData(selectedTranslation)
            bible = loadedBible

            if (isInitialLoad) {
                // FIX: Restore from Settings on first load
                val book = loadedBible.books.find { it.id == savedBookId }
                    ?: loadedBible.books.firstOrNull()

                selectedBook = book

                // Validate saved chapter
                if (book != null && selectedChapter > book.chapterCount) {
                    selectedChapter = 1L
                }
                // We keep selectedChapter/Verse as initialized from saved values
                isInitialLoad = false
            } else {
                // Standard logic when switching translation manually
                val currentBookId = selectedBook?.id
                val newBookInstance = if (currentBookId != null) {
                    loadedBible.books.find { it.id == currentBookId }
                } else {
                    null
                }

                if (newBookInstance != null) {
                    selectedBook = newBookInstance
                } else {
                    selectedBook = loadedBible.books.firstOrNull()
                    selectedChapter = 1L
                    selectedVerse = 1L
                }
            }
        } catch (e: Exception) {
            loadError = e.message
            bible = null
        } finally {
            isLoading = false
        }
    }

    // 2. Load Verses
    LaunchedEffect(bible, selectedBook, selectedChapter) {
        if (bible != null && selectedBook != null) {
            val verses = withContext(Dispatchers.Default) {
                bible!!.getVerses(selectedBook!!.id, selectedChapter)
            }
            currentVerses = verses

            // Scroll to saved verse ONLY if we just loaded verses matching the selection
            // and we haven't scrolled yet.
            // Using a simple check to see if list state is at top
            if (selectedVerse > 1 && verses.size >= selectedVerse && listState.firstVisibleItemIndex == 0) {
                scope.launch { listState.scrollToItem(selectedVerse.toInt()) }
            }
        } else {
            currentVerses = emptyList()
        }
    }

    // 3. Load Vocabulary
    LaunchedEffect(showVocabularyForVerse) {
        if (showVocabularyForVerse != null && selectedBook != null) {
            try {
                val vocab = withContext(Dispatchers.Default) {
                    getVocabularyForVerse(showVocabularyForVerse!!)
                }
                currentVocabularyList = vocab

                if (pendingStrongCode != null) {
                    selectedDefinition = vocab.find { it.strongCode.contains(pendingStrongCode!!) }
                    pendingStrongCode = null
                } else {
                    selectedDefinition = null
                }
            } catch (e: Exception) {
                currentVocabularyList = emptyList()
            }
        } else {
            currentVocabularyList = emptyList()
            selectedDefinition = null
        }
    }

    // 4. Load Commentaries
    LaunchedEffect(showCommentariesForVerse) {
        if (showCommentariesForVerse != null && selectedBook != null) {
            try {
                val comments = withContext(Dispatchers.Default) {
                    getCommentariesForVerse(showCommentariesForVerse!!)
                }
                currentCommentariesList = comments
            } catch (_: Exception) {
                currentCommentariesList = emptyList()
            }
        } else {
            currentCommentariesList = emptyList()
        }
    }

    //TODO replace Scaffold and move top bar to separate composable
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TopBarButton(selectedTranslation.displayName) {
                        showTranslationSelection = true
                    }

                    val locationLabel = if (selectedBook != null) {
                        "${selectedBook!!.name} $selectedChapter:$selectedVerse"
                    } else "Оберіть книгу"

                    TopBarButton(locationLabel) {
                        navMode = NavMode.BOOK
                        showNavSelection = true
                    }
                }
            }
        }
    ) { padding ->
        // --- SWIPE GESTURE BOX ---
        var dragOffset by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragOffset = 0f },
                        onDragEnd = {
                            if (dragOffset < -100f) { // Swipe Left -> Next
                                onNextChapter()
                            } else if (dragOffset > 100f) { // Swipe Right -> Prev
                                onPreviousChapter()
                            }
                            dragOffset = 0f
                        }
                    ) { change, dragAmount ->
                        // change.consume() // Do not consume if you want inner scrolling to work, but List is vertical
                        dragOffset += dragAmount
                    }
                }
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (bible != null && selectedBook != null) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 60.dp)
                ) {
                    item {
                        Text(
                            text = "${selectedBook!!.name} $selectedChapter",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(currentVerses) { verse ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val styledText = parseBibleText("${verse.number}  ${verse.text}")
                            var textLayoutResult by remember {
                                mutableStateOf<TextLayoutResult?>(
                                    null
                                )
                            }

                            Text(
                                text = styledText,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                                onTextLayout = { textLayoutResult = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                                    .background(
                                        if (selectedVerseForMenu == verse) MaterialTheme.colorScheme.primaryContainer.copy(
                                            alpha = 0.3f
                                        )
                                        else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .pointerInput(verse) {
                                        detectTapGestures(
                                            onTap = {
                                                selectedVerseForMenu = verse
                                                selectedVerse = verse.number
                                            },
                                            onDoubleTap = { pos ->
                                                textLayoutResult?.let { layoutResult ->
                                                    val offset =
                                                        layoutResult.getOffsetForPosition(pos)
                                                    val annotations =
                                                        styledText.getStringAnnotations(
                                                            tag = "STRONG",
                                                            start = offset,
                                                            end = offset
                                                        )

                                                    if (annotations.isNotEmpty()) {
                                                        val code = annotations.first().item
                                                        val cleanCode =
                                                            code.replace("(", "").replace(")", "")

                                                        pendingStrongCode =
                                                            normalizeStrongCode(cleanCode)

                                                        showVocabularyForVerse = verse
                                                        selectedVerseForMenu = null
                                                    }
                                                }
                                            }
                                        )
                                    }
                            )

                            if (selectedVerseForMenu == verse) {
                                Popup(
                                    alignment = Alignment.TopCenter,
                                    offset = IntOffset(0, -100),
                                    onDismissRequest = { selectedVerseForMenu = null }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(24.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 8.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BubbleActionButton(
                                                Icons.Default.ContentCopy,
                                                "Копіювати"
                                            ) {
                                                val rawText =
                                                    "${selectedBook!!.name} $selectedChapter:${verse.number}\n${
                                                        stripTags(verse.text)
                                                    }"
                                                clipboardManager.setText(AnnotatedString(rawText))
                                                selectedVerseForMenu = null
                                            }
                                            VerticalDivider(
                                                modifier = Modifier.height(24.dp),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                                    alpha = 0.2f
                                                )
                                            )

                                            BubbleActionButton(
                                                Icons.AutoMirrored.Filled.MenuBook,
                                                "Коментарі"
                                            ) {
                                                showCommentariesForVerse = verse
                                                selectedVerseForMenu = null
                                            }

                                            VerticalDivider(
                                                modifier = Modifier.height(24.dp),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                                    alpha = 0.2f
                                                )
                                            )

                                            BubbleActionButton(
                                                Icons.Default.School,
                                                "Словник"
                                            ) {
                                                showVocabularyForVerse = verse
                                                selectedVerseForMenu = null
                                            }

                                            VerticalDivider(
                                                modifier = Modifier.height(24.dp),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                                    alpha = 0.2f
                                                )
                                            )

                                            BubbleActionButton(
                                                Icons.Default.AutoAwesome,
                                                "AI"
                                            ) {
                                                showAIPopupForVerse = verse
                                                selectedVerseForMenu = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Не вдалося завантажити текст Біблії.")
                    if (bible != null && selectedBook == null) {
                        Text(
                            "В цьому перекладі бракує книг.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (loadError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(loadError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showVocabularyForVerse != null) {
        VocabularyPopup(
            selectedBookName = selectedBook?.name,
            chapter = selectedChapter,
            verse = showVocabularyForVerse!!.number,
            vocabularyList = currentVocabularyList,
            selectedDefinition = selectedDefinition,
            onSelectDefinition = { selectedDefinition = it },
            onDismiss = {
                showVocabularyForVerse = null
                pendingStrongCode = null
            }
        )
    }

    if (showCommentariesForVerse != null) {
        CommentariesPopup(
            bookName = selectedBook?.name,
            chapter = selectedChapter,
            verse = showCommentariesForVerse!!.number,
            commentaries = currentCommentariesList,
            onDismiss = { showCommentariesForVerse = null }
        )
    }

    if (showAIPopupForVerse != null) {
        AIPopup(
            verseRef = "${selectedBook?.name} $selectedChapter:${showAIPopupForVerse!!.number}\n${
                stripTags(
                    showAIPopupForVerse!!.text
                )
            }",
            onDismiss = { showAIPopupForVerse = null }
        )
    }

    if (showTranslationSelection) {
        TranslationSelectionDialog(
            availableTranslations = availableTranslations,
            selectedTranslation = selectedTranslation,
            onSelect = {
                selectedTranslation = it
                showTranslationSelection = false
            },
            onDismiss = { showTranslationSelection = false }
        )
    }

    if (showNavSelection && bible != null) {
        NavigationSelectionDialog(
            bible = bible!!,
            navMode = navMode,
            selectedBook = selectedBook,
            selectedChapter = selectedChapter,
            currentVerseCount = currentVerses.size,
            onNavModeChange = { navMode = it },
            onSelectBook = {
                selectedBook = it
                selectedChapter = 1L
                selectedVerse = 1L
                navMode = NavMode.CHAPTER
            },
            onSelectChapter = {
                selectedChapter = it
                selectedVerse = 1L
                navMode = NavMode.VERSE
            },
            onSelectVerse = {
                selectedVerse = it
                showNavSelection = false
                scope.launch { listState.scrollToItem(it.toInt()) }
            },
            onDismiss = { showNavSelection = false }
        )
    }
}