package com.abuhrov.openword.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.Book
import com.abuhrov.openword.model.Translation
import com.abuhrov.openword.model.Verse
import com.abuhrov.openword.ui.components.TopBarButton

@Composable
fun BibleTopBar(
    selectedTranslation: Translation,
    selectedBook: Book?,
    selectedChapter: Long,
    selectedVerse: Long,
    selectedVerses: Set<Verse>,
    onTranslationClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCopyVerses: () -> Unit,
    onShowCommentaries: () -> Unit,
    onShowVocabulary: () -> Unit,
    onShowAI: () -> Unit,
    onClearSelection: () -> Unit
) {
    val isSelectionMode = selectedVerses.isNotEmpty()
    val isSingleSelection = selectedVerses.size == 1

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                // Selection mode: action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${selectedVerses.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = onCopyVerses) {
                        Icon(Icons.Default.ContentCopy, "Копіювати", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onShowCommentaries, enabled = isSingleSelection) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook, "Коментарі",
                            tint = if (isSingleSelection) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(onClick = onShowVocabulary, enabled = isSingleSelection) {
                        Icon(
                            Icons.Default.School, "Словник",
                            tint = if (isSingleSelection) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(onClick = onShowAI) {
                        Icon(Icons.Default.Assistant, "AI", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, "Скасувати вибір", tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                // Normal mode: translation, location, settings
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopBarButton(selectedTranslation.id) { onTranslationClick() }
                    val locationLabel = if (selectedBook != null) "${selectedBook.shortName} $selectedChapter:$selectedVerse" else "Оберіть книгу"
                    TopBarButton(locationLabel) { onNavigationClick() }
                }
                IconButton(onClick = { onSettingsClick() }) {
                    Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
