package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.abuhrov.openword.data.repository.Bible
import com.abuhrov.openword.model.CommentaryItem
import com.abuhrov.openword.network.DeepLApiClient
import com.abuhrov.openword.ui.util.safeDismissClick
import com.abuhrov.openword.util.parseCommentaryText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CommentariesPopup(
    bookName: String?,
    chapter: Long,
    verse: Long,
    commentaries: List<CommentaryItem>,
    isLoading: Boolean,
    bible: Bible?,
    onDismiss: () -> Unit
) {
    // Per-commentary translation state
    var translatedTexts by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var translatingIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showTranslatedFlags by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var versePreviewRef by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).safeDismissClick { onDismiss() }) {
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.9f).fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Commentaries", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("$bookName $chapter:$verse", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }

                if (versePreviewRef != null) {
                    VersePreviewPopup(
                        referenceUrl = versePreviewRef!!,
                        bible = bible,
                        onDismiss = { versePreviewRef = null }
                    )
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (commentaries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No commentaries.", color = Color.Gray) }
                } else {
                    SelectionContainer {
                        LazyColumn(modifier = Modifier.padding(16.dp)) {
                            itemsIndexed(commentaries) { index, comment ->
                                val isTranslating = index in translatingIndices
                                val isShowingTranslated = index in showTranslatedFlags
                                val translatedText = translatedTexts[index]

                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            comment.sourceName,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isTranslating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else if (comment.sourceName != "Далласька богословська семінарія") {
                                            TextButton(
                                                onClick = {
                                                    if (isShowingTranslated) {
                                                        // Toggle back to original
                                                        showTranslatedFlags = showTranslatedFlags - index
                                                    } else {
                                                        // Toggle to translated
                                                        showTranslatedFlags = showTranslatedFlags + index
                                                        if (translatedText == null) {
                                                            // Need to fetch translation
                                                            translatingIndices = translatingIndices + index
                                                            scope.launch(Dispatchers.Default) {
                                                                try {
                                                                    val result = DeepLApiClient.translateText(comment.text)
                                                                    withContext(Dispatchers.Main) {
                                                                        translatedTexts = translatedTexts + (index to result)
                                                                        translatingIndices = translatingIndices - index
                                                                    }
                                                                } catch (_: Exception) {
                                                                    withContext(Dispatchers.Main) {
                                                                        translatingIndices = translatingIndices - index
                                                                        showTranslatedFlags = showTranslatedFlags - index
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Translate, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    if (isShowingTranslated && translatedText != null) "Оригінал" else "Переклад",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val displayText = if (isShowingTranslated && translatedText != null) translatedText else comment.text
                                    val styledText = remember(displayText) { parseCommentaryText(displayText.replace("<br>", "\n")) }

                                    ClickableText(
                                        text = styledText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        onClick = { pos ->
                                            styledText.getStringAnnotations(tag = "REFERENCE", start = pos, end = pos)
                                                .firstOrNull()?.let { annotation ->
                                                    versePreviewRef = annotation.item
                                                }
                                        }
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
