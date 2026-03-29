package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.Book
import com.abuhrov.openword.ui.util.safeDismissClick

data class HistoryItem(
    val bookId: Long,
    val bookShortName: String,
    val chapter: Long,
    val verse: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDialog(
    history: List<HistoryItem>,
    onSelect: (Long, Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .safeDismissClick { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.6f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Історія навігації", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрити")
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (history.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Історія порожня", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(history) { item ->
                            ListItem(
                                headlineContent = { 
                                    Text("${item.bookShortName} ${item.chapter}:${item.verse}") 
                                },
                                modifier = Modifier.clickable { 
                                    onSelect(item.bookId, item.chapter, item.verse) 
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}
