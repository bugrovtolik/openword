package com.abuhrov.openword

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class NavMode { BOOK, CHAPTER, VERSE }

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
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Show BACK button if we are in detailed view
                        if (selectedDefinition != null) {
                            IconButton(onClick = { onSelectDefinition(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }

                        Column {
                            Text(
                                text = if (selectedDefinition != null) selectedDefinition.strongCode else "Vocabulary",
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
                        Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                "Transliteration: ${entry.transliteration}",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Gloss (Short Definition)
        Text("Gloss", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(entry.gloss, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // Full Definition
        if (!entry.definition.isNullOrBlank()) {
            Text("Definition", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
            Text("No vocabulary data found.", textAlign = TextAlign.Center, color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            // Table Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Strong", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
                    Text("Original", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
                    Text("Definition", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        title = { Text("Select Translation") },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
                Text(
                    when (navMode) {
                        NavMode.BOOK -> "Select Book"
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}