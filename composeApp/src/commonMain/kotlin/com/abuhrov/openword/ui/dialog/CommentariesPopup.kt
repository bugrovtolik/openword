package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.abuhrov.openword.model.ChatMessage
import com.abuhrov.openword.model.CommentaryItem
import com.abuhrov.openword.network.GeminiApiClient
import com.abuhrov.openword.util.parseCommentaryText
import com.abuhrov.openword.util.stripJsonMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

@Composable
fun CommentariesPopup(
    bookName: String?,
    chapter: Long,
    verse: Long,
    commentaries: List<CommentaryItem>,
    autoTranslateEnabled: Boolean,
    onDismiss: () -> Unit
) {
    var showTranslated by remember { mutableStateOf(autoTranslateEnabled) }
    var translatedCommentaries by remember { mutableStateOf<List<CommentaryItem>?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(commentaries, showTranslated) {
        if (showTranslated && commentaries.isNotEmpty() && translatedCommentaries == null) {
            isTranslating = true
            scope.launch(Dispatchers.Default) {
                try {
                    val ukrainian = commentaries.find { it.sourceName != "Далласька богословська семінарія" }
                    val jsonList = json.encodeToString(commentaries.filter { it != ukrainian })
                    val prompt = "Translate the 'text' field to Ukrainian. Keep JSON structure. JSON: $jsonList"
                    val response = GeminiApiClient.generateChatResponse(listOf(ChatMessage("user", prompt)))
                    val cleanJson = stripJsonMarkdown(response)
                    val result = json.decodeFromString<List<CommentaryItem>>(cleanJson)
                    withContext(Dispatchers.Main) {
                        translatedCommentaries = if (ukrainian != null) listOf(ukrainian) + result else result
                        isTranslating = false
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) { isTranslating = false }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (commentaries.isNotEmpty()) {
                            TextButton(onClick = { showTranslated = !showTranslated }) {
                                Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showTranslated) "Оригінал" else "Переклад")
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                }

                val listToShow = if (showTranslated && translatedCommentaries != null) translatedCommentaries!! else commentaries

                if (isTranslating && showTranslated && translatedCommentaries == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                if (listToShow.isEmpty() && !isTranslating) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No commentaries.", color = Color.Gray) }
                } else {
                    SelectionContainer {
                        LazyColumn(modifier = Modifier.padding(16.dp)) {
                            items(listToShow) { comment ->
                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    Text(comment.sourceName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(parseCommentaryText(comment.text.replace("<br>", "\n")), style = MaterialTheme.typography.bodyMedium)
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
