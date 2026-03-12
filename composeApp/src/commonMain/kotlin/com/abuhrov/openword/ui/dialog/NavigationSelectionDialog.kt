package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.abuhrov.openword.data.repository.Bible
import com.abuhrov.openword.model.Book
import com.abuhrov.openword.model.NavMode
import com.abuhrov.openword.model.NavigationViewMode
import com.abuhrov.openword.ui.components.NavigationGridItem

private fun String?.toColorOrNull(): Color? {
    if (this == null) return null
    try {
        if (this.startsWith("#")) {
            val hex = this.substring(1)
            val fullHex = if (hex.length == 6) "FF$hex" else hex
            return Color(fullHex.toLong(16)).copy(alpha = 0.1f)
        }
    } catch (e: Exception) {}
    return null
}

@Composable
fun NavigationSelectionDialog(
    bible: Bible,
    navMode: NavMode,
    navViewMode: NavigationViewMode,
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (navMode != NavMode.BOOK) {
                    IconButton(onClick = { onNavModeChange(if (navMode == NavMode.VERSE) NavMode.CHAPTER else NavMode.BOOK) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
                Text(when (navMode) {
                    NavMode.BOOK -> "Оберіть книгу"
                    NavMode.CHAPTER -> "${selectedBook?.name}"
                    NavMode.VERSE -> "${selectedBook?.name} $selectedChapter"
                }, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxSize()) {
                when (navMode) {
                    NavMode.BOOK -> {
                        when (navViewMode) {
                            NavigationViewMode.LIST -> {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(bible.books) { book ->
                                        val bgColor = book.color.toColorOrNull() ?: Color.Transparent
                                        Surface(color = bgColor, onClick = { onSelectBook(book) }, modifier = Modifier.fillMaxWidth()) {
                                            Text(book.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (book == selectedBook) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().padding(12.dp))
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                            NavigationViewMode.GRID -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(6),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(bible.books) { book ->
                                        NavigationGridItem(
                                            text = book.shortName,
                                            isSelected = book == selectedBook,
                                            bgColor = book.color.toColorOrNull()
                                        ) { onSelectBook(book) }
                                    }
                                }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрити") } }
    )
}
