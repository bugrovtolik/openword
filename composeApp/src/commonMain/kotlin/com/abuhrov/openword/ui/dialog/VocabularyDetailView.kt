package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.LexiconEntry

@Composable
fun VocabularyDetailView(entry: LexiconEntry) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(entry.originalWord, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        if (entry.transliteration != null) Text("Transliteration: ${entry.transliteration}", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        Text("Gloss", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(entry.gloss, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        if (!entry.definition.isNullOrBlank()) {
            Text("Definition", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(entry.definition.replace("<br>", "\n").replace("<BR>", "\n"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
