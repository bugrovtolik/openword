package com.abuhrov.openword

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var currentFont by remember { mutableStateOf<FontFamily?>(null) }

    // --- SETTINGS STATE ---
    // Read from Settings (stored as Strings, parse manually)
    var fontSizeScale by remember { mutableStateOf(Settings.getString("font_scale", "1.0").toFloat()) }
    var autoTranslate by remember { mutableStateOf(Settings.getString("auto_translate", "true").toBoolean()) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        currentFont = loadAppFont()
    }

    val appTypography = Typography().run {
        val fontFamily = currentFont ?: FontFamily.Default
        copy(
            displayLarge = displayLarge.copy(fontFamily = fontFamily, fontSize = displayLarge.fontSize * fontSizeScale),
            displayMedium = displayMedium.copy(fontFamily = fontFamily, fontSize = displayMedium.fontSize * fontSizeScale),
            displaySmall = displaySmall.copy(fontFamily = fontFamily, fontSize = displaySmall.fontSize * fontSizeScale),
            headlineLarge = headlineLarge.copy(fontFamily = fontFamily, fontSize = headlineLarge.fontSize * fontSizeScale),
            headlineMedium = headlineMedium.copy(fontFamily = fontFamily, fontSize = headlineMedium.fontSize * fontSizeScale),
            headlineSmall = headlineSmall.copy(fontFamily = fontFamily, fontSize = headlineSmall.fontSize * fontSizeScale),
            titleLarge = titleLarge.copy(fontFamily = fontFamily, fontSize = titleLarge.fontSize * fontSizeScale),
            titleMedium = titleMedium.copy(fontFamily = fontFamily, fontSize = titleMedium.fontSize * fontSizeScale),
            titleSmall = titleSmall.copy(fontFamily = fontFamily, fontSize = titleSmall.fontSize * fontSizeScale),
            bodyLarge = bodyLarge.copy(fontFamily = fontFamily, fontSize = bodyLarge.fontSize * fontSizeScale),
            bodyMedium = bodyMedium.copy(fontFamily = fontFamily, fontSize = bodyMedium.fontSize * fontSizeScale),
            bodySmall = bodySmall.copy(fontFamily = fontFamily, fontSize = bodySmall.fontSize * fontSizeScale),
            labelLarge = labelLarge.copy(fontFamily = fontFamily, fontSize = labelLarge.fontSize * fontSizeScale),
            labelMedium = labelMedium.copy(fontFamily = fontFamily, fontSize = labelMedium.fontSize * fontSizeScale),
            labelSmall = labelSmall.copy(fontFamily = fontFamily, fontSize = labelSmall.fontSize * fontSizeScale)
        )
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF5D4037),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD7CCC8),
            background = Color(0xFFF5F5F5)
        ),
        typography = appTypography
    ) {
        // Init State from Settings
        val savedTranslationId = Settings.getString("last_translation", availableTranslations.first().id)
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
        var selectedTranslation by remember { mutableStateOf(availableTranslations.find { it.id == savedTranslationId } ?: availableTranslations.first()) }
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

        // Marker Note State
        var showMarkerNote by remember { mutableStateOf<String?>(null) }
        var markerNotePosition by remember { mutableStateOf(IntOffset.Zero) }

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
                    val book = loadedBible.books.find { it.id == savedBookId } ?: loadedBible.books.firstOrNull()
                    selectedBook = book
                    if (book != null && selectedChapter > book.chapterCount) selectedChapter = 1L
                    isInitialLoad = false
                } else {
                    // Standard logic when switching translation manually
                    val currentBookId = selectedBook?.id
                    val newBookInstance = if (currentBookId != null) loadedBible.books.find { it.id == currentBookId } else null
                    if (newBookInstance != null) selectedBook = newBookInstance
                    else {
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
                    val vocab = withContext(Dispatchers.Default) { getVocabularyForVerse(showVocabularyForVerse!!) }
                    currentVocabularyList = vocab
                    if (pendingStrongCode != null) {
                        selectedDefinition = vocab.find { it.strongCode.contains(pendingStrongCode!!) }
                        pendingStrongCode = null
                    } else selectedDefinition = null
                } catch (e: Exception) { currentVocabularyList = emptyList() }
            } else { currentVocabularyList = emptyList(); selectedDefinition = null }
        }

        // 4. Load Commentaries
        LaunchedEffect(showCommentariesForVerse) {
            if (showCommentariesForVerse != null && selectedBook != null) {
                try {
                    val comments = withContext(Dispatchers.Default) { getCommentariesForVerse(showCommentariesForVerse!!) }
                    currentCommentariesList = comments
                } catch (_: Exception) { currentCommentariesList = emptyList() }
            } else currentCommentariesList = emptyList()
        }

        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary).statusBarsPadding()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TopBarButton(selectedTranslation.id) { showTranslationSelection = true }
                            val locationLabel = if (selectedBook != null) "${selectedBook!!.name} $selectedChapter:$selectedVerse" else "Оберіть книгу"
                            TopBarButton(locationLabel) { navMode = NavMode.BOOK; showNavSelection = true }
                        }
                        IconButton(onClick = { showSettingsDialog = true }) { Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
        ) { padding ->
            var dragOffset by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragOffset = 0f },
                            onDragEnd = {
                                if (dragOffset < -100f) onNextChapter()
                                else if (dragOffset > 100f) onPreviousChapter()
                                dragOffset = 0f
                            }
                        ) { change, dragAmount -> dragOffset += dragAmount }
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
                                val mergeRegex = Regex("<n>(\\d+-\\d+)</n>")
                                val match = mergeRegex.find(verse.text)

                                val displayLabel = match?.groupValues?.get(1) ?: verse.number.toString()

                                val styledText = parseBibleText("$displayLabel  ${verse.text}")
                                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                Text(
                                    text = styledText,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp.times(fontSizeScale)),
                                    onTextLayout = { textLayoutResult = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                        .background(
                                            if (selectedVerseForMenu == verse) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .pointerInput(verse) {
                                            detectTapGestures(
                                                onTap = { pos ->
                                                    var markerClicked = false
                                                    textLayoutResult?.let { layoutResult ->
                                                        val offset = layoutResult.getOffsetForPosition(pos)
                                                        val annotations = styledText.getStringAnnotations(tag = "COMMENTARY_MARKER", start = offset, end = offset)
                                                        if (annotations.isNotEmpty()) {
                                                            markerClicked = true
                                                            val markerId = annotations.first().item
                                                            val source = selectedTranslation.commentarySource
                                                            if (source != null) {
                                                                markerNotePosition = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                                                selectedVerse = verse.number
                                                                scope.launch(Dispatchers.Default) {
                                                                    try {
                                                                        val note = getCommentaryForMarker(selectedBook!!.id, selectedChapter, markerId.toLong(), source)
                                                                        withContext(Dispatchers.Main) { showMarkerNote = note ?: "Примітку не знайдено." }
                                                                    } catch (e: Exception) {
                                                                        withContext(Dispatchers.Main) { showMarkerNote = "Цей переклад не підтримує примітки." }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (!markerClicked) {
                                                        selectedVerseForMenu = verse
                                                        selectedVerse = verse.number
                                                        showMarkerNote = null
                                                    }
                                                },
                                                onDoubleTap = { pos ->
                                                    textLayoutResult?.let { layoutResult ->
                                                        val offset = layoutResult.getOffsetForPosition(pos)
                                                        val annotations = styledText.getStringAnnotations(tag = "STRONG", start = offset, end = offset)
                                                        if (annotations.isNotEmpty()) {
                                                            val code = annotations.first().item
                                                            pendingStrongCode = normalizeStrongCode(code.replace("(", "").replace(")", ""))
                                                            showVocabularyForVerse = verse
                                                            selectedVerseForMenu = null
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                )

                                if (selectedVerseForMenu == verse) {
                                    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -100), onDismissRequest = { selectedVerseForMenu = null }) {
                                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.shadow(8.dp, RoundedCornerShape(24.dp))) {
                                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                BubbleActionButton(Icons.Default.ContentCopy, "Копіювати") {
                                                    clipboardManager.setText(AnnotatedString("${selectedBook!!.name} $selectedChapter:$displayLabel\n${stripTags(verse.text)}"))
                                                    selectedVerseForMenu = null
                                                }
                                                VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                                                BubbleActionButton(Icons.AutoMirrored.Filled.MenuBook, "Коментарі") { showCommentariesForVerse = verse; selectedVerseForMenu = null }
                                                VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                                                BubbleActionButton(Icons.Default.School, "Словник") { showVocabularyForVerse = verse; selectedVerseForMenu = null }
                                                VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                                                BubbleActionButton(Icons.Default.AutoAwesome, "AI") { showAIPopupForVerse = verse; selectedVerseForMenu = null }
                                            }
                                        }
                                    }
                                }

                                if (showMarkerNote != null && selectedVerse == verse.number && selectedVerseForMenu == null) {
                                    MarkerNotePopup(
                                        text = showMarkerNote!!,
                                        offset = markerNotePosition,
                                        onDismiss = { showMarkerNote = null }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Не вдалося завантажити текст Біблії.")
                        if (bible != null && selectedBook == null) {
                            Text("В цьому перекладі бракує книг.", style = MaterialTheme.typography.bodySmall)
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
                bible = bible!!, navMode = navMode, selectedBook = selectedBook, selectedChapter = selectedChapter, currentVerseCount = currentVerses.size,
                onNavModeChange = { navMode = it }, onSelectBook = { selectedBook = it; selectedChapter = 1L; selectedVerse = 1L; navMode = NavMode.CHAPTER },
                onSelectChapter = { selectedChapter = it; selectedVerse = 1L; navMode = NavMode.VERSE },
                onSelectVerse = { selectedVerse = it; showNavSelection = false; scope.launch { listState.scrollToItem(it.toInt()) } },
                onDismiss = { showNavSelection = false }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                currentFontSizeScale = fontSizeScale, currentAutoTranslate = autoTranslate,
                onFontSizeChange = { fontSizeScale = it; Settings.setString("font_scale", it.toString()) },
                onAutoTranslateChange = { autoTranslate = it; Settings.setString("auto_translate", it.toString()) },
                onReloadAllData = {
                    scope.launch {
                        isLoading = true; clearAllLocalData(); try { bible = loadBibleData(selectedTranslation) } catch(e: Exception) {}
                        isLoading = false; showSettingsDialog = false
                    }
                },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}