package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abuhrov.openword.domain.search.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDialog(
    query: String,
    onSearch: suspend (String) -> List<SearchResult>,
    onResultClick: (Long, Long, Long) -> Unit, // bookId, chapter, verse
    onDismiss: () -> Unit
) {
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(true) }
    var displayCount by remember { mutableStateOf(20) }
    val listState = rememberLazyListState()

    LaunchedEffect(query) {
        if (query.length >= 3) {
            isSearching = true
            displayCount = 20
            results = onSearch(query)
            isSearching = false
        } else {
            results = emptyList()
            isSearching = false
        }
    }

    // Trigger pagination when reaching the end of the currently displayed list
    LaunchedEffect(listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo.size) {
        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        if (lastVisibleItem != null && lastVisibleItem.index >= displayCount - 5) {
            if (displayCount < results.size) {
                displayCount += 20
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(text = "Результати: \"$query\" (${results.size})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                )

                if (isSearching) {
                } else if (query.length >= 3 && results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Результатів не знайдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (query.length >= 3) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(results.take(displayCount)) { result ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResultClick(result.bookId, result.chapter, result.verseNumber) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "${result.displayBookName} ${result.chapter}:${result.verseNumber}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    
                                    val highlightedText = buildAnnotatedString {
                                        val lowerText = result.text.lowercase()
                                        var currentIndex = 0
                                        
                                        // A brutal simplistic highlight algorithm: highlight anything that contains our match terms roughly.
                                        // For better results we just find any occurrence of any match.
                                        val matchPositions = mutableListOf<Pair<Int, Int>>()
                                        for (m in result.matches) {
                                            var idx = lowerText.indexOf(m)
                                            while (idx >= 0) {
                                                matchPositions.add(idx to idx + m.length)
                                                idx = lowerText.indexOf(m, idx + 1)
                                            }
                                        }
                                        
                                        val sortedMatches = matchPositions.sortedBy { it.first }
                                        val mergedMatches = mutableListOf<Pair<Int, Int>>()
                                        for (m in sortedMatches) {
                                            if (mergedMatches.isEmpty()) {
                                                mergedMatches.add(m)
                                            } else {
                                                val last = mergedMatches.last()
                                                if (m.first <= last.second) {
                                                    mergedMatches[mergedMatches.lastIndex] = last.first to maxOf(last.second, m.second)
                                                } else {
                                                    mergedMatches.add(m)
                                                }
                                            }
                                        }

                                        for (m in mergedMatches) {
                                            if (m.first > currentIndex) {
                                                append(result.text.substring(currentIndex, m.first))
                                            }
                                            withStyle(SpanStyle(background = MaterialTheme.colorScheme.primaryContainer, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)) {
                                                append(result.text.substring(m.first, m.second))
                                            }
                                            currentIndex = m.second
                                        }
                                        if (currentIndex < result.text.length) {
                                            append(result.text.substring(currentIndex))
                                        }
                                    }

                                    Text(
                                        text = highlightedText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
