package com.abuhrov.openword.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
    onSearchClick: (String) -> Unit,
    onSearchError: (String) -> Unit,
    onCopyVerses: () -> Unit,
    onShowCommentaries: () -> Unit,
    onShowVocabulary: () -> Unit,
    onShowCrossReferences: () -> Unit,
    onShowAI: () -> Unit,
    onClearSelection: () -> Unit
) {
    val isSelectionMode = selectedVerses.isNotEmpty()
    val isSingleSelection = selectedVerses.size == 1
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchErrorTrigger by remember { mutableStateOf(false) }

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
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
                    IconButton(onClick = onShowCrossReferences, enabled = isSingleSelection) {
                        Icon(
                            Icons.Default.CompareArrows, "Перехресні посилання",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopBarButton(selectedTranslation.id) { onTranslationClick() }
                    Spacer(modifier = Modifier.width(8.dp))
                    val locationLabel = if (selectedBook != null) "${selectedBook.shortName} $selectedChapter:$selectedVerse" else "Оберіть книгу"
                    TopBarButton(locationLabel) { onNavigationClick() }
                    
                    if (isSearchExpanded) {
                        androidx.compose.runtime.LaunchedEffect(searchErrorTrigger) {
                            if (searchErrorTrigger) {
                                kotlinx.coroutines.delay(500)
                                searchErrorTrigger = false
                            }
                        }

                        val backgroundColor by androidx.compose.animation.animateColorAsState(
                            if (searchErrorTrigger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        )
                        val textColor by androidx.compose.animation.animateColorAsState(
                            if (searchErrorTrigger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                        )

                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = textColor,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    if (searchQuery.length >= 3) {
                                        onSearchClick(searchQuery)
                                        isSearchExpanded = false
                                    } else {
                                        searchErrorTrigger = true
                                        onSearchError("Введіть принаймні 3 символи для пошуку")
                                    }
                                }
                            }),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(textColor),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = backgroundColor,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text("Пошук...", color = textColor.copy(alpha = 0.6f))
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        IconButton(onClick = {
                            if (searchQuery.isNotBlank()) {
                                if (searchQuery.length >= 3) {
                                    onSearchClick(searchQuery)
                                    isSearchExpanded = false
                                } else {
                                    searchErrorTrigger = true
                                    onSearchError("Введіть принаймні 3 символи для пошуку")
                                }
                            } else {
                                isSearchExpanded = false
                            }
                        }) {
                            Icon(Icons.Default.Search, "Знайти", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { isSearchExpanded = true; searchQuery = "" }) {
                            Icon(Icons.Default.Search, "Пошук", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Налаштування", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
