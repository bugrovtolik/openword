package com.abuhrov.openword.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.model.Book
import com.abuhrov.openword.model.Translation
import com.abuhrov.openword.ui.components.TopBarButton

@Composable
fun BibleTopBar(
    selectedTranslation: Translation,
    selectedBook: Book?,
    selectedChapter: Long,
    selectedVerse: Long,
    onTranslationClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopBarButton(selectedTranslation.id) { onTranslationClick() }
                val locationLabel = if (selectedBook != null) "${selectedBook.name} $selectedChapter:$selectedVerse" else "Оберіть книгу"
                TopBarButton(locationLabel) { onNavigationClick() }
            }
            IconButton(onClick = { onSettingsClick() }) {
                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
