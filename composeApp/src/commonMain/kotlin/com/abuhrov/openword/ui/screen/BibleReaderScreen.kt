package com.abuhrov.openword.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abuhrov.openword.data.repository.Bible
import com.abuhrov.openword.data.repository.DictionaryRepository
import com.abuhrov.openword.data.repository.getCommentaryForMarker
import com.abuhrov.openword.model.Book
import com.abuhrov.openword.model.ChapterItem
import com.abuhrov.openword.model.CommentarySource
import com.abuhrov.openword.model.Verse
import com.abuhrov.openword.ui.dialog.DictionaryPopup
import com.abuhrov.openword.ui.dialog.MarkerNotePopup
import com.abuhrov.openword.util.normalizeStrongCode
import com.abuhrov.openword.util.parseBibleText
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
    chapterItems: List<ChapterItem>,
    selectedVerses: Set<Verse>,
    fontSizeScale: Float,
    commentarySource: CommentarySource?,
    listState: LazyListState,
    scope: CoroutineScope,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onVerseSelected: (Long) -> Unit,
    onVerseLongPressed: (Verse) -> Unit,
    onVerseTapped: (Verse) -> Unit,
    onDoubleTapStrong: (Verse, String) -> Unit
) {
    var showMarkerNote by remember { mutableStateOf<String?>(null) }
    var markerNotePosition by remember { mutableStateOf(IntOffset.Zero) }
    var showDictionaryWord by remember { mutableStateOf<String?>(null) }
    var dictionaryDefinition by remember { mutableStateOf<String?>(null) }
    var dictionaryPopupPosition by remember { mutableStateOf(IntOffset.Zero) }
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
                itemsIndexed(chapterItems, key = { index, item ->
                    when (item) {
                        is ChapterItem.Header -> "header_${item.chapter}"
                        is ChapterItem.VerseItem -> "verse_${item.chapter}_${item.verse.number}"
                    }
                }) { _, item ->
                    when (item) {
                        is ChapterItem.Header -> {
                            val hasDictRef = selectedBook.name.endsWith("*") && DictionaryRepository.hasDefinitionSync(selectedBook.name)
                            val headerText = remember(selectedBook.name, item.chapter, hasDictRef) {
                                buildAnnotatedString {
                                    if (hasDictRef) {
                                        pushStringAnnotation(tag = "DICTIONARY_WORD", annotation = selectedBook.name)
                                        withStyle(
                                            SpanStyle(textDecoration = TextDecoration.Underline)
                                        ) {
                                            append(selectedBook.name)
                                        }
                                        pop()
                                    } else {
                                        append(selectedBook.name)
                                    }
                                    append(" ${item.chapter}")
                                }
                            }
                            val headerLayoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
                            val headerWindowOffset = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(vertical = 16.dp)
                                    .onGloballyPositioned { coords ->
                                        headerWindowOffset.value = coords.positionInWindow()
                                    }
                                    .pointerInput(hasDictRef) {
                                        if (hasDictRef) {
                                            detectTapGestures(onTap = { pos ->
                                                headerLayoutResult.value?.let { layout ->
                                                    val offset = layout.getOffsetForPosition(pos)
                                                    val annotations = headerText.getStringAnnotations("DICTIONARY_WORD", offset, offset)
                                                    if (annotations.isNotEmpty()) {
                                                        val word = annotations.first().item
                                                        scope.launch {
                                                            val def = DictionaryRepository.findDefinition(word)
                                                            dictionaryPopupPosition = IntOffset(
                                                                (headerWindowOffset.value.x + pos.x).roundToInt(),
                                                                (headerWindowOffset.value.y + pos.y).roundToInt() - 300
                                                            )
                                                            showDictionaryWord = word
                                                            dictionaryDefinition = def ?: "Не знайдено у словнику."
                                                        }
                                                    }
                                                }
                                            })
                                        }
                                    },
                                onTextLayout = { headerLayoutResult.value = it },
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ChapterItem.VerseItem -> {
                            val verse = item.verse
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val mergeRegex = remember { Regex("<n>(\\d+-\\d+)</n>") }
                                val match = mergeRegex.find(verse.text)

                                val displayLabel = match?.groupValues?.get(1) ?: verse.number.toString()

                                val rawStyledText = "$displayLabel  ${verse.text}"
                                val styledText = remember(rawStyledText) { parseBibleText(rawStyledText) }
                                val textLayoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
                                val textWindowOffset = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                                val isSelected = verse in selectedVerses

                                Text(
                                    text = styledText,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp.times(fontSizeScale)),
                                    onTextLayout = { textLayoutResult.value = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .onGloballyPositioned { coords ->
                                            textWindowOffset.value = coords.positionInWindow()
                                        }
                                        .pointerInput(verse, selectedVerses.isNotEmpty()) {
                                            detectTapGestures(
                                                onTap = { pos ->
                                                    if (selectedVerses.isNotEmpty()) {
                                                        onVerseTapped(verse)
                                                        return@detectTapGestures
                                                    }
                                                    textLayoutResult.value?.let { layoutResult ->
                                                        val offset = layoutResult.getOffsetForPosition(pos)
                                                        val dictAnnotations = styledText.getStringAnnotations(tag = "DICTIONARY_WORD", start = offset, end = offset)
                                                        if (dictAnnotations.isNotEmpty()) {
                                                            val word = dictAnnotations.first().item
                                                            dictionaryPopupPosition = IntOffset(
                                                                (textWindowOffset.value.x + pos.x).roundToInt(),
                                                                (textWindowOffset.value.y + pos.y).roundToInt() - 100
                                                            )
                                                            onVerseSelected(verse.number)
                                                            scope.launch {
                                                                val def = DictionaryRepository.findDefinition(word)
                                                                if (def != null) {
                                                                    showDictionaryWord = word
                                                                    dictionaryDefinition = def
                                                                } else {
                                                                    showDictionaryWord = word
                                                                    dictionaryDefinition = "Не знайдено у словнику."
                                                                }
                                                            }
                                                            return@detectTapGestures
                                                        }

                                                        val annotations = styledText.getStringAnnotations(tag = "COMMENTARY_MARKER", start = offset, end = offset)
                                                        if (annotations.isNotEmpty()) {
                                                            val markerId = annotations.first().item
                                                            if (commentarySource != null) {
                                                                markerNotePosition = IntOffset(
                                                                    (textWindowOffset.value.x + pos.x).roundToInt(),
                                                                    (textWindowOffset.value.y + pos.y).roundToInt() - 300
                                                                )
                                                                scope.launch {
                                                                    try {
                                                                        val note = getCommentaryForMarker(selectedBook.id, item.chapter, verse.number, markerId, commentarySource)
                                                                        showMarkerNote = note ?: "Примітку не знайдено."
                                                                    } catch (_: Exception) {
                                                                        showMarkerNote = "Цей переклад не підтримує примітки."
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                onLongPress = { _ ->
                                                    onVerseLongPressed(verse)
                                                    showMarkerNote = null
                                                    showDictionaryWord = null
                                                    dictionaryDefinition = null
                                                },
                                                onDoubleTap = { pos ->
                                                    if (selectedVerses.isNotEmpty()) return@detectTapGestures
                                                    textLayoutResult.value?.let { layoutResult ->
                                                        val offset = layoutResult.getOffsetForPosition(pos)
                                                        val annotations = styledText.getStringAnnotations(tag = "STRONG", start = offset, end = offset)
                                                        if (annotations.isNotEmpty()) {
                                                            val code = annotations.first().item
                                                            val normalizedCode = normalizeStrongCode(code.replace("(", "").replace(")", ""))
                                                            onDoubleTapStrong(verse, normalizedCode)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Non-interactive dead zone at the bottom to prevent accidental touches
            // during iOS swipe-up gesture
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )


            if (showMarkerNote != null && selectedVerses.isEmpty()) {
                MarkerNotePopup(
                    text = showMarkerNote!!,
                    offset = markerNotePosition,
                    onDismiss = { showMarkerNote = null },
                    onReferenceClick = { referenceUrl ->
                        try {
                            val parts = referenceUrl.replace("'", "").trim().split(Regex("\\s+"))
                            if (parts.size >= 2) {
                                val bookId = parts[0].removePrefix("B:").toLong()
                                val chapVerse = parts[1].split(":")
                                val c = chapVerse[0].toLong()
                                val v = chapVerse[1].toLong()
                                val targetBook = bible.books.find { it.id == bookId }
                                scope.launch {
                                    val verses = bible.getVerses(bookId, c)
                                    val targetV = verses.find { it.number == v }
                                    if (targetV != null) {
                                        withContext(Dispatchers.Main) {
                                            val cleanText = com.abuhrov.openword.util.stripTags(targetV.text)
                                            showMarkerNote = "📜 ${targetBook?.shortName ?: "?"} $c:$v\n\n$cleanText"
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                )
            }

            if (showDictionaryWord != null && dictionaryDefinition != null && selectedVerses.isEmpty()) {
                DictionaryPopup(
                    word = showDictionaryWord!!,
                    definition = dictionaryDefinition!!,
                    offset = dictionaryPopupPosition,
                    onDismiss = { 
                        showDictionaryWord = null
                        dictionaryDefinition = null
                    },
                    onReferenceClicked = { referenceUrl ->
                        try {
                            val parts = referenceUrl.replace("'", "").trim().split(Regex("\\s+"))
                            if (parts.size >= 2) {
                                val bookId = parts[0].removePrefix("B:").toLong()
                                val currentChapVerse = parts[1].split(":")
                                val c = currentChapVerse[0].toLong()
                                val v = currentChapVerse[1].toLong()
                                scope.launch {
                                    val verses = bible.getVerses(bookId, c)
                                    val targetV = verses.find { it.number == v }
                                    if (targetV != null) {
                                        withContext(Dispatchers.Main) {
                                            val cleanText = com.abuhrov.openword.util.stripTags(targetV.text)
                                            dictionaryDefinition = "📜 ВІДДІЛ: $referenceUrl\n\n$cleanText"
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
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
