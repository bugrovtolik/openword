package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.Translation

@Composable
fun TranslationSelectionDialog(
    availableTranslations: List<Translation>,
    selectedTranslation: Translation,
    onSelect: (Translation) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оберіть переклад") },
        text = {
            Column {
                availableTranslations.forEach { translation ->
                    TextButton(onClick = { onSelect(translation) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = translation.id,
                            modifier = Modifier.width(60.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = translation.displayName,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (translation == selectedTranslation) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Назад") } }
    )
}
