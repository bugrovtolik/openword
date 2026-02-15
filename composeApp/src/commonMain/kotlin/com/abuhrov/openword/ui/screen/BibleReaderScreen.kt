package com.abuhrov.openword.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.abuhrov.openword.data.repository.Bible
import com.abuhrov.openword.data.repository.getCommentaryForMarker
import com.abuhrov.openword.model.Book
import com.abuhrov.openword.model.CommentarySource
import com.abuhrov.openword.model.Verse
import com.abuhrov.openword.ui.components.BubbleActionButton
import com.abuhrov.openword.ui.dialog.MarkerNotePopup
import com.abuhrov.openword.util.normalizeStrongCode
import com.abuhrov.openword.util.parseBibleText
import com.abuhrov.openword.util.stripTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun BibleReaderScreen(
    padding: PaddingValues,
    isLoading: Boolean,
    loadError: String?,
    bible: Bible?,
    selectedBook: Book?,
    selectedChapter: Long,
    selectedVerse: Long,
    currentVerses: List<Verse>,
    fontSizeScale: Float,
    commentarySource: CommentarySource?,
    listState: LazyListState,
    scope: CoroutineScope,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onVerseSelected: (Long) -> Unit,
    onShowCommentaries: (Verse) -> Unit,
    onShowVocabulary: (Verse) -> Unit,
    onShowAI: (Verse) -> Unit,
    onDoubleTapStrong: (Verse, String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedVerseForMenu by remember { mutableStateOf<Verse?>(null) }
    var showMarkerNote by remember { mutableStateOf<String?>(null) }
    var markerNotePosition by remember { mutableStateOf(IntOffset.Zero) }
    var dragOffset by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffset = 0f },
                    onDragEnd = {
                        if (dragOffset < -100f) onNextChapter()
                        else if (dragOffset > 100f) onPreviousChapter()
                        dragOffset = 0f
                    }
                ) { change, dragAmount -> dragOffset += dragAmount }
            }
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (bible != null && selectedBook != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                item {
                    Text(
                        text = "${selectedBook.name} $selectedChapter",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(currentVerses) { verse ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val mergeRegex = Regex("<n>(\\d+-\\d+)</n>")
                        val match = mergeRegex.find(verse.text)

                        val displayLabel = match?.groupValues?.get(1) ?: verse.number.toString()

                        val styledText = parseBibleText("$displayLabel  ${verse.text}")
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        Text(
                            text = styledText,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp.times(fontSizeScale)),
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .background(
                                    if (selectedVerseForMenu == verse) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .pointerInput(verse) {
                                    detectTapGestures(
                                        onTap = { pos ->
                                            var markerClicked = false
                                            textLayoutResult?.let { layoutResult ->
                                                val offset = layoutResult.getOffsetForPosition(pos)
                                                val annotations = styledText.getStringAnnotations(tag = "COMMENTARY_MARKER", start = offset, end = offset)
                                                if (annotations.isNotEmpty()) {
                                                    markerClicked = true
                                                    val markerId = annotations.first().item
                                                    val source = commentarySource
                                                    if (source != null) {
                                                        markerNotePosition = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                                        onVerseSelected(verse.number)
                                                        scope.launch(Dispatchers.Default) {
                                                            try {
                                                                val note = getCommentaryForMarker(selectedBook.id, selectedChapter, markerId.toLong(), source)
                                                                withContext(Dispatchers.Main) { showMarkerNote = note ?: "Примітку не знайдено." }
                                                            } catch (e: Exception) {
                                                                withContext(Dispatchers.Main) { showMarkerNote = "Цей переклад не підтримує примітки." }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            if (!markerClicked) {
                                                selectedVerseForMenu = verse
                                                onVerseSelected(verse.number)
                                                showMarkerNote = null
                                            }
                                        },
                                        onDoubleTap = { pos ->
                                            textLayoutResult?.let { layoutResult ->
                                                val offset = layoutResult.getOffsetForPosition(pos)
                                                val annotations = styledText.getStringAnnotations(tag = "STRONG", start = offset, end = offset)
                                                if (annotations.isNotEmpty()) {
                                                    val code = annotations.first().item
                                                    val normalizedCode = normalizeStrongCode(code.replace("(", "").replace(")", ""))
                                                    onDoubleTapStrong(verse, normalizedCode)
                                                    selectedVerseForMenu = null
                                                }
                                            }
                                        }
                                    )
                                }
                        )

                        if (selectedVerseForMenu == verse) {
                            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -100), onDismissRequest = { selectedVerseForMenu = null }) {
                                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.shadow(8.dp, RoundedCornerShape(24.dp))) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        BubbleActionButton(Icons.Default.ContentCopy, "Копіювати") {
                                            clipboardManager.setText(AnnotatedString("${selectedBook.name} $selectedChapter:$displayLabel\n${stripTags(verse.text)}"))
                                            selectedVerseForMenu = null
                                        }
                                        VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                                        BubbleActionButton(Icons.AutoMirrored.Filled.MenuBook, "Коментарі") { onShowCommentaries(verse); selectedVerseForMenu = null }
                                        VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                                        BubbleActionButton(Icons.Default.School, "Словник") { onShowVocabulary(verse); selectedVerseForMenu = null }
                                        VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                                        BubbleActionButton(Icons.Default.AutoAwesome, "AI") { onShowAI(verse); selectedVerseForMenu = null }
                                    }
                                }
                            }
                        }

                        if (showMarkerNote != null && selectedVerse == verse.number && selectedVerseForMenu == null) {
                            MarkerNotePopup(
                                text = showMarkerNote!!,
                                offset = markerNotePosition,
                                onDismiss = { showMarkerNote = null }
                            )
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Не вдалося завантажити текст Біблії.")
                if (bible != null && selectedBook == null) {
                    Text("В цьому перекладі бракує книг.", style = MaterialTheme.typography.bodySmall)
                }
                if (loadError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(loadError, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
