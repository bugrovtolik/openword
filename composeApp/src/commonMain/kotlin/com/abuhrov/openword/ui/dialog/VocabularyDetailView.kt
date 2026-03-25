package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.LexiconEntry
import com.abuhrov.openword.util.parseDictionaryText

@Composable
fun VocabularyDetailView(
    entry: LexiconEntry,
    isTranslating: Boolean = false,
    onReferenceClick: (String) -> Unit = {}
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                entry.originalWord ?: "",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            if (isTranslating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (entry.transliteration != null) Text("Транслітерація: ${entry.transliteration}", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        if (entry.morphology != null) Text("Морфологія: ${entry.morphology}", style = MaterialTheme.typography.bodyMedium)

        Text(entry.shortDefinition, style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))
        if (!entry.fullDefinition.isNullOrBlank()) {
            Text("Визначення", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            val defText = entry.fullDefinition
            val styledDef = remember(defText) { parseDictionaryText(defText?.replace("<br>", "\n")?.replace("<BR>", "\n") ?: "") }

            ClickableText(
                text = styledDef,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                onClick = { pos ->
                    styledDef.getStringAnnotations(tag = "REFERENCE", start = pos, end = pos)
                        .firstOrNull()?.let { annotation -> onReferenceClick(annotation.item) }

                    styledDef.getStringAnnotations(tag = "BIBLE_REF", start = pos, end = pos)
                        .firstOrNull()?.let { annotation -> onReferenceClick(annotation.item) }
                }
            )
        }
    }
}
