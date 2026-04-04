package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.ChatMessage
import com.abuhrov.openword.network.GeminiApiClient
import com.abuhrov.openword.ui.util.safeDismissClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AIPopup(verseRef: String, onDismiss: () -> Unit) {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).safeDismissClick { onDismiss() }, contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI асистент", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Контекст: ${verseRef.take(30)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), maxLines = 1)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    if (messages.isNotEmpty()) {
                        LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(messages) { msg -> ChatBubble(msg) }
                            if (isLoading) { item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } } }
                        }
                    }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Поставте питання...") }, maxLines = 3, shape = RoundedCornerShape(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val userMsg = ChatMessage("user", inputText)
                            messages = messages + userMsg
                            val currentInput = inputText
                            inputText = ""
                            isLoading = true
                            // Add empty model message to be updated via stream
                            messages = messages + ChatMessage("model", "")
                            
                            scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                            scope.launch(Dispatchers.Default) {
                                val apiPrompt = "Контекст:\n$verseRef\nВідповідай коротко і лаконічно одним абзацем: $currentInput"
                                // Exclude the newly added empty model message and replace last user msg with prompted one
                                val apiHistory = messages.dropLast(2) + ChatMessage("user", apiPrompt)
                                
                                try {
                                    GeminiApiClient.generateChatResponseStream(apiHistory).collect { chunk ->
                                        withContext(Dispatchers.Main) {
                                            if (messages.isNotEmpty()) {
                                                val lastMsg = messages.last()
                                                messages = messages.dropLast(1) + lastMsg.copy(text = lastMsg.text + chunk)
                                                scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        if (messages.isNotEmpty()) {
                                            val lastMsg = messages.last()
                                            if (lastMsg.text.isEmpty()) {
                                                messages = messages.dropLast(1) + lastMsg.copy(text = "Помилка: ${e.message}")
                                            }
                                        }
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    }, enabled = !isLoading && inputText.isNotBlank(), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.AutoMirrored.Filled.Send, "Надіслати") }
                }
            }
        }
    }
}
