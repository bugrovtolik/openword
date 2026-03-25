package com.abuhrov.openword.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import com.abuhrov.openword.model.CommentarySource
import com.abuhrov.openword.model.Verse
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
    selectedVerse: Long,
    currentVerses: List<Verse>,
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
                item {
                    val hasDictRef = selectedBook.name.endsWith("*") && DictionaryRepository.hasDefinitionSync(selectedBook.name)
                    val headerText = remember(selectedBook.name, selectedChapter, hasDictRef) {
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
                            append(" $selectedChapter")
                        }
                    }
                    var headerLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    var headerWindowOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                            .onGloballyPositioned { coords ->
                                headerWindowOffset = coords.positionInWindow()
                            }
                            .pointerInput(hasDictRef) {
                                if (hasDictRef) {
                                    detectTapGestures(onTap = { pos ->
                                        headerLayoutResult?.let { layout ->
                                            val offset = layout.getOffsetForPosition(pos)
                                            val annotations = headerText.getStringAnnotations("DICTIONARY_WORD", offset, offset)
                                            if (annotations.isNotEmpty()) {
                                                val word = annotations.first().item
                                                dictionaryPopupPosition = IntOffset(
                                                    (headerWindowOffset.x + pos.x).roundToInt(),
                                                    (headerWindowOffset.y + pos.y).roundToInt() - 300
                                                )
                                                scope.launch(Dispatchers.Default) {
                                                    val def = DictionaryRepository.findDefinition(word)
                                                    withContext(Dispatchers.Main) {
                                                        showDictionaryWord = word
                                                        dictionaryDefinition = def ?: "Не знайдено у словнику."
                                                    }
                                                }
                                            }
                                        }
                                    })
                                }
                            },
                        onTextLayout = { headerLayoutResult = it },
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(currentVerses) { verse ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val mergeRegex = remember { Regex("<n>(\\d+-\\d+)</n>") }
                        val match = mergeRegex.find(verse.text)

                        val displayLabel = match?.groupValues?.get(1) ?: verse.number.toString()

                        val rawStyledText = "$displayLabel  ${verse.text}"
                        val styledText = remember(rawStyledText) { parseBibleText(rawStyledText) }
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                        var textWindowOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                        val isSelected = verse in selectedVerses

                        Text(
                            text = styledText,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp.times(fontSizeScale)),
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .onGloballyPositioned { coords ->
                                    textWindowOffset = coords.positionInWindow()
                                }
                                .pointerInput(verse, selectedVerses.isNotEmpty()) {
                                    detectTapGestures(
                                        onTap = { pos ->
                                            if (selectedVerses.isNotEmpty()) {
                                                onVerseTapped(verse)
                                                return@detectTapGestures
                                            }
                                            textLayoutResult?.let { layoutResult ->
                                                val offset = layoutResult.getOffsetForPosition(pos)
                                                val dictAnnotations = styledText.getStringAnnotations(tag = "DICTIONARY_WORD", start = offset, end = offset)
                                                if (dictAnnotations.isNotEmpty()) {
                                                    val word = dictAnnotations.first().item
                                                    dictionaryPopupPosition = IntOffset(
                                                        (textWindowOffset.x + pos.x).roundToInt(),
                                                        (textWindowOffset.y + pos.y).roundToInt() - 100
                                                    )
                                                    onVerseSelected(verse.number)
                                                    scope.launch(Dispatchers.Default) {
                                                        val def = com.abuhrov.openword.data.repository.DictionaryRepository.findDefinition(word)
                                                        withContext(Dispatchers.Main) {
                                                            if (def != null) {
                                                                showDictionaryWord = word
                                                                dictionaryDefinition = def
                                                            } else {
                                                                showDictionaryWord = word
                                                                dictionaryDefinition = "Не знайдено у словнику."
                                                            }
                                                        }
                                                    }
                                                    return@detectTapGestures
                                                }

                                                val annotations = styledText.getStringAnnotations(tag = "COMMENTARY_MARKER", start = offset, end = offset)
                                                if (annotations.isNotEmpty()) {
                                                    val markerId = annotations.first().item
                                                    val source = commentarySource
                                                    if (source != null) {
                                                        markerNotePosition = IntOffset(
                                                            (textWindowOffset.x + pos.x).roundToInt(),
                                                            (textWindowOffset.y + pos.y).roundToInt() - 300
                                                        )
                                                        onVerseSelected(verse.number)
                                                        scope.launch(Dispatchers.Default) {
                                                            try {
                                                                val note = getCommentaryForMarker(selectedBook.id, selectedChapter, verse.number, markerId, source)
                                                                withContext(Dispatchers.Main) { showMarkerNote = note ?: "Примітку не знайдено." }
                                                            } catch (e: Exception) {
                                                                withContext(Dispatchers.Main) { showMarkerNote = "Цей переклад не підтримує примітки." }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onLongPress = { _ ->
                                            onVerseLongPressed(verse)
                                            onVerseSelected(verse.number)
                                            showMarkerNote = null
                                            showDictionaryWord = null
                                            dictionaryDefinition = null
                                        },
                                        onDoubleTap = { pos ->
                                            if (selectedVerses.isNotEmpty()) return@detectTapGestures
                                            textLayoutResult?.let { layoutResult ->
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

            if (showMarkerNote != null && selectedVerses.isEmpty()) {
                MarkerNotePopup(
                    text = showMarkerNote!!,
                    offset = markerNotePosition,
                    onDismiss = { showMarkerNote = null },
                    onReferenceClick = { referenceUrl ->
                        try {
                            if (bible != null) {
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
                            }
                        } catch (_: Exception) {}
                    }
                )
            }

            if (showDictionaryWord != null && dictionaryDefinition != null && selectedVerses.isEmpty()) {
                com.abuhrov.openword.ui.dialog.DictionaryPopup(
                    word = showDictionaryWord!!,
                    definition = dictionaryDefinition!!,
                    offset = dictionaryPopupPosition,
                    onDismiss = { 
                        showDictionaryWord = null
                        dictionaryDefinition = null
                    },
                    onReferenceClicked = { referenceUrl ->
                        try {
                            if (bible != null) {
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
