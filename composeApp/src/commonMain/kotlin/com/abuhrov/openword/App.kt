package com.abuhrov.openword

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// NavMode is now defined in BibleDialogs.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF5D4037),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD7CCC8),
            background = Color(0xFFF5F5F5)
        )
    ) {
        // App State
        var bible by remember { mutableStateOf<Bible?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var loadError by remember { mutableStateOf<String?>(null) }

        // Selection State
        var selectedTranslation by remember { mutableStateOf(availableTranslations.first()) }
        var selectedBook by remember { mutableStateOf<Book?>(null) }
        var selectedChapter by remember { mutableStateOf(1L) }
        var selectedVerse by remember { mutableStateOf(1L) }

        // Data State (Lazy Loaded)
        var currentVerses by remember { mutableStateOf<List<Verse>>(emptyList()) }

        // Popup States
        var selectedVerseForMenu by remember { mutableStateOf<Verse?>(null) }
        var showVocabularyForVerse by remember { mutableStateOf<Verse?>(null) }
        var currentVocabularyList by remember { mutableStateOf<List<LexiconEntry>>(emptyList()) }

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
            } catch (e: Exception) {
                println("Error loading bible: $e")
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
                    println("Error loading vocabulary: $e")
                    currentVocabularyList = emptyList()
                }
            } else {
                currentVocabularyList = emptyList()
                selectedDefinition = null
            }
        }

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
                        TopBarButton(selectedTranslation.displayName) { showTranslationSelection = true }

                        val locationLabel = if (selectedBook != null) {
                            "${selectedBook!!.name} $selectedChapter:$selectedVerse"
                        } else "Select Book"

                        TopBarButton(locationLabel) {
                            navMode = NavMode.BOOK
                            showNavSelection = true
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                Text(
                                    text = styledText,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
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
                                                onTap = {
                                                    selectedVerseForMenu = verse
                                                    selectedVerse = verse.number
                                                },
                                                onDoubleTap = { pos ->
                                                    textLayoutResult?.let { layoutResult ->
                                                        val offset = layoutResult.getOffsetForPosition(pos)
                                                        val annotations = styledText.getStringAnnotations(tag = "STRONG", start = offset, end = offset)

                                                        if (annotations.isNotEmpty()) {
                                                            val code = annotations.first().item
                                                            val cleanCode = code.replace("(", "").replace(")", "")

                                                            pendingStrongCode = normalizeStrongCode(cleanCode)

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
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                BubbleActionButton(Icons.Default.ContentCopy, "Copy") {
                                                    val rawText = "${selectedBook!!.name} $selectedChapter:${verse.number}\n${stripTags(verse.text)}"
                                                    clipboardManager.setText(AnnotatedString(rawText))
                                                    selectedVerseForMenu = null
                                                }
                                                VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                                                BubbleActionButton(Icons.Default.MenuBook, "Comments") { selectedVerseForMenu = null }
                                                VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                                                BubbleActionButton(Icons.Default.School, "Vocabulary") {
                                                    showVocabularyForVerse = verse
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
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Failed to load Bible data.")
                        if (bible != null && selectedBook == null) {
                            Text("No books found in this translation.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (loadError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(loadError!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // --- VOCABULARY POPUP ---
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

        // --- DIALOGS ---
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
}