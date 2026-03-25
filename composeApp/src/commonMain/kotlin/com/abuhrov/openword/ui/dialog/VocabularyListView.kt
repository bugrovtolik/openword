package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.LexiconEntry

@Composable
fun VocabularyListView(
    vocabularyList: List<LexiconEntry>,
    onSelectDefinition: (LexiconEntry) -> Unit
) {
    if (vocabularyList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No vocabulary data found.", color = Color.Gray) }
    } else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Strong", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                    Text("Original", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                    Text("Definition", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
            }
            items(vocabularyList) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectDefinition(entry) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(entry.strongCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(60.dp))
                    Text(entry.originalWord ?: "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(90.dp))
                    Text(entry.shortDefinition, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}
