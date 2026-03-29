package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.data.repository.Bible
import com.abuhrov.openword.util.stripTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parses a reference URL like "B:120 13:20" into (bookId, chapter, verse).
 * Returns null if the format is invalid.
 */
fun parseReferenceUrl(url: String): Triple<Long, Long, Long>? {
    return try {
        val parts = url.replace("'", "").trim().split(Regex("\\s+"))
        if (parts.size >= 2) {
            val bookId = parts[0].removePrefix("B:").toLong()
            val chapVerse = parts[1].split(":")
            val chapter = chapVerse[0].toLong()
            val verse = chapVerse[1].takeWhile { it.isDigit() }.toLong()
            Triple(bookId, chapter, verse)
        } else null
    } catch (_: Exception) {
        null
    }
}

/**
 * A popup that shows the text of a referenced verse.
 * Used when a user clicks an <a href="B:id ch:vs"> link in commentary/vocabulary/marker notes.
 */
@Composable
fun VersePreviewPopup(
    referenceUrl: String,
    bible: Bible?,
    onDismiss: () -> Unit
) {
    val parsed = remember(referenceUrl) { parseReferenceUrl(referenceUrl) }
    var verseText by remember(referenceUrl) { mutableStateOf<String?>(null) }
    var bookName by remember(referenceUrl) { mutableStateOf<String?>(null) }
    var isLoading by remember(referenceUrl) { mutableStateOf(true) }

    LaunchedEffect(referenceUrl, bible) {
        if (bible != null && parsed != null) {
            isLoading = true
            try {
                val (bookId, chapter, verse) = parsed
                val book = bible.books.find { it.id == bookId }
                bookName = book?.shortName ?: "?"
                val verses = withContext(Dispatchers.Default) { bible.getVerses(bookId, chapter) }
                val targetVerse = verses.find { it.number == verse }
                verseText = if (targetVerse != null) stripTags(targetVerse.text) else "Вірш не знайдено."
            } catch (_: Exception) {
                verseText = "Помилка завантаження вірша."
            } finally {
                isLoading = false
            }
        } else {
            verseText = "Невірне посилання."
            isLoading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    if (parsed != null) {
                        Text(
                            "${bookName ?: "..."} ${parsed.second}:${parsed.third}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    verseText ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
