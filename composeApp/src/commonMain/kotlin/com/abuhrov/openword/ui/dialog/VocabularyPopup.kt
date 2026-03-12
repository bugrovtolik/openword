package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.ChatMessage
import com.abuhrov.openword.model.LexiconEntry
import com.abuhrov.openword.network.GeminiApiClient
import com.abuhrov.openword.util.stripJsonMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

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

    LaunchedEffect(vocabularyList, showTranslated) {
        if (showTranslated && vocabularyList.isNotEmpty() && translatedVocabulary == null) {
            isTranslating = true
            scope.launch(Dispatchers.Default) {
                try {
                    val jsonList = json.encodeToString(vocabularyList)
                    val prompt = "Translate 'gloss' and 'definition' to Ukrainian. Keep JSON structure. JSON: $jsonList"
                    val response = GeminiApiClient.generateChatResponse(listOf(ChatMessage("user", prompt)))
                    val cleanJson = stripJsonMarkdown(response)
                    val result = json.decodeFromString<List<LexiconEntry>>(cleanJson)
                    withContext(Dispatchers.Main) { translatedVocabulary = result; isTranslating = false }
                } catch (e: Exception) { isTranslating = false }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Surface(modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.9f).fillMaxHeight(0.8f)) {
            Column {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedDefinition != null) {
                            IconButton(onClick = { onSelectDefinition(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
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
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                }

                val currentList = if (showTranslated && translatedVocabulary != null) translatedVocabulary!! else vocabularyList

                val definitionToShow = if (selectedDefinition != null) {
                    currentList.find { it.strongCode == selectedDefinition.strongCode } ?: selectedDefinition
                } else null

                if (isTranslating && showTranslated && translatedVocabulary == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                if (definitionToShow != null) {
                    SelectionContainer {
                        VocabularyDetailView(definitionToShow)
                    }
                } else {
                    VocabularyListView(currentList, onSelectDefinition)
                }
            }
        }
    }
}
