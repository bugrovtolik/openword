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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.data.repository.Bible
import com.abuhrov.openword.model.Book
import com.abuhrov.openword.model.NavMode
import com.abuhrov.openword.ui.components.NavigationGridItem

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
