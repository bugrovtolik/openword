package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.abuhrov.openword.data.repository.CrossReferenceItem
import com.abuhrov.openword.model.Book
import com.abuhrov.openword.util.stripTags

data class CrossReferenceUiItem(
    val reference: CrossReferenceItem,
    val book: Book?,
    val verseText: String // Stripped verse text
)

@Composable
fun CrossReferencesPopup(
    bookName: String?,
    chapter: Long,
    verse: Long,
    crossReferences: List<CrossReferenceUiItem>,
    isLoading: Boolean,
    onReferenceClick: (Long, Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Перехресні посилання",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Закрити")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "$bookName $chapter:$verse",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (crossReferences.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Немає перехресних посилань для цього вірша",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(crossReferences) { uiItem ->
                            val refBookName = uiItem.book?.shortName ?: ""
                            val refVerseRange = if (uiItem.reference.verseToEnd != null && uiItem.reference.verseToEnd != uiItem.reference.verseToStart) {
                                "${uiItem.reference.verseToStart}-${uiItem.reference.verseToEnd}"
                            } else {
                                "${uiItem.reference.verseToStart}"
                            }

                            Card(
                                onClick = {
                                    onReferenceClick(
                                        uiItem.reference.bookTo,
                                        uiItem.reference.chapterTo,
                                        uiItem.reference.verseToStart
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "$refBookName ${uiItem.reference.chapterTo}:$refVerseRange",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Text(
                                        text = stripTags(uiItem.verseText),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити")
            }
        }
    )
}
