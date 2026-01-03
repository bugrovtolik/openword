package com.abuhrov.openword

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AIPopup(
    verseRef: String,
    onDismiss: () -> Unit
) {
    // Chat state
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
                .fillMaxHeight(0.8f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI асистент",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Контекст: ${verseRef.take(30)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    if (messages.isNotEmpty()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages) { msg ->
                                ChatBubble(msg)
                            }
                            if (isLoading) {
                                item {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Поставте питання...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val userMsg = ChatMessage("user", inputText)
                                messages = messages + userMsg

                                val currentInput = inputText
                                inputText = ""
                                isLoading = true

                                scope.launch {
                                    listState.animateScrollToItem(messages.lastIndex)
                                }

                                scope.launch(Dispatchers.Default) {
                                    val apiPrompt = "Контекст:\n$verseRef\nВідповідай коротко і лаконічно одним абзацем: $currentInput"
                                    val apiHistory = messages.dropLast(1) + ChatMessage("user", apiPrompt)
                                    val responseText = GeminiApi.generateChatResponse(apiHistory)

                                    withContext(Dispatchers.Main) {
                                        messages = messages + ChatMessage("model", responseText)
                                        isLoading = false
                                        scope.launch {
                                            listState.animateScrollToItem(messages.lastIndex)
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Надіслати")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val align = if (isUser) Alignment.End else Alignment.Start
    val shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(align),
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = "AI",
                    modifier = Modifier.size(24.dp).padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            Surface(
                color = bubbleColor,
                shape = shape,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = parseMarkdown(message.text),
                    modifier = Modifier.padding(12.dp),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (isUser) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "User",
                    modifier = Modifier.size(24.dp).padding(start = 4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun parseMarkdown(text: String): AnnotatedString {
    // Regex for **bold** (group 2) or *italic* (group 4)
    val pattern = Regex("(\\*\\*(.*?)\\*\\*)|(\\*(.*?)\\*)")

    return buildAnnotatedString {
        var lastIndex = 0
        pattern.findAll(text).forEach { matchResult ->
            // Append text before the match
            append(text.substring(lastIndex, matchResult.range.first))

            val value = matchResult.value
            if (value.startsWith("**")) {
                // Bold
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(matchResult.groupValues[2])
                }
            } else {
                // Italic
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(matchResult.groupValues[4])
                }
            }
            lastIndex = matchResult.range.last + 1
        }
        // Append remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

@Composable
fun CommentariesPopup(
    bookName: String?,
    chapter: Long,
    verse: Long,
    commentaries: List<CommentaryItem>,
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
                .fillMaxHeight(0.8f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Коментарі",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$bookName $chapter:$verse",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                if (commentaries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Для цього вірша немає коментарів.", textAlign = TextAlign.Center, color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        items(commentaries) { comment ->
                            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(
                                    text = "${comment.sourceName} - ${comment.chapter}:${comment.verseStart}" + if(comment.verseEnd > comment.verseStart) "-${comment.verseEnd}" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                val cleanText = comment.text.replace("<br>", "\n").replace("<BR>", "\n")
                                Text(parseCommentaryText(cleanText), style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VocabularyPopup(
    selectedBookName: String?,
    chapter: Long,
    verse: Long,
    vocabularyList: List<LexiconEntry>,
    selectedDefinition: LexiconEntry?,
    onSelectDefinition: (LexiconEntry?) -> Unit,
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
                .fillMaxHeight(0.8f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedDefinition != null) {
                            IconButton(onClick = { onSelectDefinition(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }

                        Column {
                            Text(
                                text = if (selectedDefinition != null) selectedDefinition.strongCode else "Словник",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$selectedBookName $chapter:$verse",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                // Content Area
                if (selectedDefinition != null) {
                    VocabularyDetailView(selectedDefinition)
                } else {
                    VocabularyListView(vocabularyList, onSelectDefinition)
                }
            }
        }
    }
}

@Composable
private fun VocabularyDetailView(entry: LexiconEntry) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
        // Original Word
        Text(entry.originalWord, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        // Transliteration
        if (entry.transliteration != null) {
            Text(
                "Транслітерація: ${entry.transliteration}",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Gloss (Short Definition)
        Text("Глосарій", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(entry.gloss, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // Full Definition
        if (!entry.definition.isNullOrBlank()) {
            Text("Визначення", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // Handle <br> tags by replacing them with newlines for basic display
            val cleanDefinition = entry.definition.replace("<br>", "\n").replace("<BR>", "\n")
            Text(cleanDefinition, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VocabularyListView(
    vocabularyList: List<LexiconEntry>,
    onSelectDefinition: (LexiconEntry) -> Unit
) {
    if (vocabularyList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Словник не знайдено.", textAlign = TextAlign.Center, color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Стронг", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
                    Text("Оригінал", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
                    Text("Визначення", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            items(vocabularyList) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDefinition(entry) }
                        .padding(vertical = 12.dp),
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
                    TextButton(
                        onClick = { onSelect(translation) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            translation.displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (translation == selectedTranslation) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Відмінити") } }
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
                    IconButton(onClick = {
                        onNavModeChange(if (navMode == NavMode.VERSE) NavMode.CHAPTER else NavMode.BOOK)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
                Text(
                    when (navMode) {
                        NavMode.BOOK -> "Оберіть книгу"
                        NavMode.CHAPTER -> "${selectedBook?.name}"
                        NavMode.VERSE -> "${selectedBook?.name} $selectedChapter"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Box(modifier = Modifier.height(400.dp).width(300.dp)) {
                when (navMode) {
                    NavMode.BOOK -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(bible.books) { book ->
                                TextButton(
                                    onClick = { onSelectBook(book) },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(12.dp)
                                ) {
                                    Text(
                                        book.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (book == selectedBook) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                    NavMode.CHAPTER -> {
                        val chapters = (1L..(selectedBook?.chapterCount ?: 0L)).toList()
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 60.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(chapters) { chapter ->
                                NavigationGridItem(
                                    number = chapter,
                                    isSelected = chapter == selectedChapter,
                                    onClick = { onSelectChapter(chapter) }
                                )
                            }
                        }
                    }
                    NavMode.VERSE -> {
                        val verses = (1L..currentVerseCount).toList()
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 60.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(verses) { verseNum ->
                                NavigationGridItem(
                                    number = verseNum,
                                    isSelected = false,
                                    onClick = { onSelectVerse(verseNum) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрити") } }
    )
}