package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.abuhrov.openword.model.ChatMessage
import com.abuhrov.openword.util.parseMarkdown

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val align = if (isUser) Alignment.End else Alignment.Start
    val shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.align(align), verticalAlignment = Alignment.Bottom) {
            if (!isUser) Icon(Icons.Default.SmartToy, "AI", Modifier.size(24.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.secondary)
            Surface(color = bubbleColor, shape = shape, modifier = Modifier.widthIn(max = 280.dp)) {
                SelectionContainer {
                    Text(text = parseMarkdown(message.text), modifier = Modifier.padding(12.dp), color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (isUser) Icon(Icons.Default.Person, "User", Modifier.size(24.dp).padding(start = 4.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
