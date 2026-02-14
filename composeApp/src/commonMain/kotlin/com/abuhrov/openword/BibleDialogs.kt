package com.abuhrov.openword

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

// --- Marker Note Popup (Small, Contextual) ---
@Composable
fun MarkerNotePopup(
    text: String,
    offset: IntOffset,
    onDismiss: () -> Unit
) {
    Popup(
        offset = offset,
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(8.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Примітка",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Використовуємо логіку парсингу для очищення тексту
                val styledNote = parseCommentaryText(text.replace("<br>", "\n").replace("<BR>", "\n"))
                Text(
                    text = styledNote,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// --- Settings Dialog ---
@Composable
fun SettingsDialog(
    currentFontSizeScale: Float,
    currentAutoTranslate: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onAutoTranslateChange: (Boolean) -> Unit,
    onReloadAllData: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Налаштування", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрити")
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Розмір шрифту: ${(currentFontSizeScale * 100).toInt()}%")
                Slider(
                    value = currentFontSizeScale,
                    onValueChange = onFontSizeChange,
                    valueRange = 0.8f..1.5f,
                    steps = 6
                )
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = currentAutoTranslate,
                        onCheckedChange = onAutoTranslateChange
                    )
                    Text("Автопереклад (Коментарі/Словник)")
                }
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onReloadAllData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Перезавантажити всі дані")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Очищує кеш і завантажує бази даних наново.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AIPopup(verseRef: String, onDismiss: () -> Unit) {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f).clickable(enabled = false) {}, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI асистент", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Контекст: ${verseRef.take(30)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), maxLines = 1)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    if (messages.isNotEmpty()) {
                        LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(messages) { msg -> ChatBubble(msg) }
                            if (isLoading) { item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } } }
                        }
                    }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Поставте питання...") }, maxLines = 3, shape = RoundedCornerShape(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val userMsg = ChatMessage("user", inputText)
                            messages = messages + userMsg
                            val currentInput = inputText
                            inputText = ""
                            isLoading = true
                            scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                            scope.launch(Dispatchers.Default) {
                                val apiPrompt = "Контекст:\n$verseRef\nВідповідай коротко і лаконічно одним абзацем: $currentInput"
                                val apiHistory = messages.dropLast(1) + ChatMessage("user", apiPrompt)
                                val responseText = GeminiApi.generateChatResponse(apiHistory)
                                withContext(Dispatchers.Main) {
                                    messages = messages + ChatMessage("model", responseText)
                                    isLoading = false
                                    scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                                }
                            }
                        }
                    }, enabled = !isLoading && inputText.isNotBlank(), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.AutoMirrored.Filled.Send, "Надіслати") }
                }
            }
        }
    }
}

// ... Helpers ...
private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

private fun stripJsonMarkdown(text: String): String {
    return text.replace(Regex("^```json"), "").replace(Regex("^```"), "").replace(Regex("```$"), "").trim()
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val align = if (isUser) Alignment.End else Alignment.Start
    val shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.align(align), verticalAlignment = Alignment.Bottom) {
            if (!isUser) Icon(Icons.Default.SmartToy, "AI", Modifier.size(24.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.secondary)
            Surface(color = bubbleColor, shape = shape, modifier = Modifier.widthIn(max = 280.dp)) {
                Text(text = parseMarkdown(message.text), modifier = Modifier.padding(12.dp), color = textColor, style = MaterialTheme.typography.bodyMedium)
            }
            if (isUser) Icon(Icons.Default.Person, "User", Modifier.size(24.dp).padding(start = 4.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun parseMarkdown(text: String): AnnotatedString {
    val pattern = Regex("(\\*\\*(.*?)\\*\\*)|(\\*(.*?)\\*)")
    return buildAnnotatedString {
        var lastIndex = 0
        pattern.findAll(text).forEach { matchResult ->
            append(text.substring(lastIndex, matchResult.range.first))
            val value = matchResult.value
            if (value.startsWith("**")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(matchResult.groupValues[2]) }
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(matchResult.groupValues[4]) }
            }
            lastIndex = matchResult.range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
}

// --- Commentaries Popup ---
@Composable
fun CommentariesPopup(
    bookName: String?,
    chapter: Long,
    verse: Long,
    commentaries: List<CommentaryItem>,
    autoTranslateEnabled: Boolean,
    onDismiss: () -> Unit
) {
    var showTranslated by remember { mutableStateOf(autoTranslateEnabled) }
    var translatedCommentaries by remember { mutableStateOf<List<CommentaryItem>?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-translate if enabled OR if user toggles it ON later
    LaunchedEffect(commentaries, showTranslated) {
        if (showTranslated && commentaries.isNotEmpty() && translatedCommentaries == null) {
            isTranslating = true
            scope.launch(Dispatchers.Default) {
                try {
                    val ukrainian = commentaries.find { it.sourceName != "Далласька богословська семінарія" }
                    val jsonList = json.encodeToString(commentaries.filter { it != ukrainian })
                    val prompt = "Translate the 'text' field to Ukrainian. Keep JSON structure. JSON: $jsonList"
                    val response = GeminiApi.generateChatResponse(listOf(ChatMessage("user", prompt)))
                    val cleanJson = stripJsonMarkdown(response)
                    val result = json.decodeFromString<List<CommentaryItem>>(cleanJson)
                    withContext(Dispatchers.Main) {
                        translatedCommentaries = if (ukrainian != null) result + ukrainian else result
                        isTranslating = false
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) { isTranslating = false }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() }) {
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.9f).fillMaxHeight(0.8f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Commentaries", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("$bookName $chapter:$verse", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (commentaries.isNotEmpty()) {
                            TextButton(onClick = { showTranslated = !showTranslated }) {
                                Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showTranslated) "Оригінал" else "Переклад")
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                }

                val listToShow = if (showTranslated && translatedCommentaries != null) translatedCommentaries!! else commentaries

                if (isTranslating && showTranslated && translatedCommentaries == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                if (listToShow.isEmpty() && !isTranslating) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No commentaries.", color = Color.Gray) }
                } else {
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        items(listToShow) { comment ->
                            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(comment.sourceName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(parseCommentaryText(comment.text.replace("<br>", "\n")), style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

// --- Vocabulary Popup ---
@Composable
fun VocabularyPopup(
    selectedBookName: String?,
    chapter: Long,
    verse: Long,
    vocabularyList: List<LexiconEntry>,
    selectedDefinition: LexiconEntry?,
    autoTranslateEnabled: Boolean,
    onSelectDefinition: (LexiconEntry?) -> Unit,
    onDismiss: () -> Unit
) {
    var showTranslated by remember { mutableStateOf(autoTranslateEnabled) }
    var translatedVocabulary by remember { mutableStateOf<List<LexiconEntry>?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-translate if enabled OR if user toggles it ON later
    LaunchedEffect(vocabularyList, showTranslated) {
        if (showTranslated && vocabularyList.isNotEmpty() && translatedVocabulary == null) {
            isTranslating = true
            scope.launch(Dispatchers.Default) {
                try {
                    val jsonList = json.encodeToString(vocabularyList)
                    val prompt = "Translate 'gloss' and 'definition' to Ukrainian. Keep JSON structure. JSON: $jsonList"
                    val response = GeminiApi.generateChatResponse(listOf(ChatMessage("user", prompt)))
                    val cleanJson = stripJsonMarkdown(response)
                    val result = json.decodeFromString<List<LexiconEntry>>(cleanJson)
                    withContext(Dispatchers.Main) { translatedVocabulary = result; isTranslating = false }
                } catch (e: Exception) { isTranslating = false }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() }) {
        Surface(modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.9f).fillMaxHeight(0.8f)) {
            Column {
                Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedDefinition != null) {
                            IconButton(onClick = { onSelectDefinition(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                        Column {
                            Text(selectedDefinition?.strongCode ?: "Vocabulary", style = MaterialTheme.typography.titleMedium)
                            Text("$selectedBookName $chapter:$verse", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row {
                        if (vocabularyList.isNotEmpty()) {
                            TextButton(onClick = { showTranslated = !showTranslated }) {
                                Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showTranslated) "Оригінал" else "Переклад")
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                }

                // Determine active list (Original or Translated)
                val currentList = if (showTranslated && translatedVocabulary != null) translatedVocabulary!! else vocabularyList

                // FIX: Look up the definition from the active list based on ID
                val definitionToShow = if (selectedDefinition != null) {
                    currentList.find { it.strongCode == selectedDefinition.strongCode } ?: selectedDefinition
                } else null

                if (isTranslating && showTranslated && translatedVocabulary == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                if (definitionToShow != null) {
                    VocabularyDetailView(definitionToShow)
                } else {
                    VocabularyListView(currentList, onSelectDefinition)
                }
            }
        }
    }
}

// ... (Rest of file: VocabularyDetailView, VocabularyListView, TranslationSelectionDialog, NavigationSelectionDialog same as provided) ...
@Composable
private fun VocabularyDetailView(entry: LexiconEntry) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(entry.originalWord, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        if (entry.transliteration != null) Text("Transliteration: ${entry.transliteration}", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        Text("Gloss", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(entry.gloss, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        if (!entry.definition.isNullOrBlank()) {
            Text("Definition", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(entry.definition.replace("<br>", "\n").replace("<BR>", "\n"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VocabularyListView(
    vocabularyList: List<LexiconEntry>,
    onSelectDefinition: (LexiconEntry) -> Unit
) {
    if (vocabularyList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No vocabulary data found.", color = Color.Gray) }
    } else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Strong", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                    Text("Original", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                    Text("Definition", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
            }
            items(vocabularyList) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectDefinition(entry) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(entry.strongCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(60.dp))
                    Text(entry.originalWord, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(90.dp))
                    Text(entry.gloss, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun TranslationSelectionDialog(
    availableTranslations: List<Translation>,
    selectedTranslation: Translation,
    onSelect: (Translation) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оберіть переклад") },
        text = {
            LazyColumn {
                items(availableTranslations) { translation ->
                    TextButton(onClick = { onSelect(translation) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = translation.id,
                            modifier = Modifier.width(60.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = translation.displayName,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (translation == selectedTranslation) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Назад") } }
    )
}

@Composable
fun NavigationSelectionDialog(
    bible: Bible,
    navMode: NavMode,
    selectedBook: Book?,
    selectedChapter: Long,
    currentVerseCount: Int,
    onNavModeChange: (NavMode) -> Unit,
    onSelectBook: (Book) -> Unit,
    onSelectChapter: (Long) -> Unit,
    onSelectVerse: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (navMode != NavMode.BOOK) {
                    IconButton(onClick = { onNavModeChange(if (navMode == NavMode.VERSE) NavMode.CHAPTER else NavMode.BOOK) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
                Text(when (navMode) {
                    NavMode.BOOK -> "Select Book"
                    NavMode.CHAPTER -> "${selectedBook?.name}"
                    NavMode.VERSE -> "${selectedBook?.name} $selectedChapter"
                }, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Box(modifier = Modifier.height(400.dp).width(300.dp)) {
                when (navMode) {
                    NavMode.BOOK -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(bible.books) { book ->
                                TextButton(onClick = { onSelectBook(book) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                                    Text(book.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (book == selectedBook) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth())
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                    NavMode.CHAPTER -> {
                        val chapters = (1L..(selectedBook?.chapterCount ?: 0L)).toList()
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 60.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(chapters) { chapter ->
                                NavigationGridItem(number = chapter, isSelected = chapter == selectedChapter) { onSelectChapter(chapter) }
                            }
                        }
                    }
                    NavMode.VERSE -> {
                        val verses = (1L..currentVerseCount).toList()
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 60.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(verses) { verseNum ->
                                NavigationGridItem(number = verseNum, isSelected = false) { onSelectVerse(verseNum) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}