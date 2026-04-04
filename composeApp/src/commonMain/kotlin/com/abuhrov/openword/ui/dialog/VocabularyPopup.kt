package com.abuhrov.openword.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abuhrov.openword.data.repository.Bible
import com.abuhrov.openword.data.repository.VocabularyRepository
import com.abuhrov.openword.model.AILinkedWord
import com.abuhrov.openword.model.LexiconEntry
import com.abuhrov.openword.model.VerseLexiconPayload
import com.abuhrov.openword.network.DeepLApiClient
import com.abuhrov.openword.network.GroqApiClient
import com.abuhrov.openword.ui.util.safeDismissClick
import com.abuhrov.openword.util.stripJsonMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyPopup(
    selectedBookName: String?,
    chapter: Long,
    verse: Long,
    vocabularyList: List<LexiconEntry>,
    verseLexiconPayload: VerseLexiconPayload?,
    bible: Bible?,
    onDismiss: () -> Unit
) {
    var aiLinkedWords by remember(verseLexiconPayload) { mutableStateOf<List<AILinkedWord>?>(null) }
    var isLinking by remember(verseLexiconPayload) { mutableStateOf(verseLexiconPayload != null && aiLinkedWords == null) }

    var clickedLinkedWord by remember { mutableStateOf<AILinkedWord?>(null) }


    var isTranslating by remember(vocabularyList) { mutableStateOf(true) }
    var versePreviewRef by remember { mutableStateOf<String?>(null) }
    val navigationStack = remember { mutableStateListOf<LexiconEntry>() }
    val scope = rememberCoroutineScope()
    var isFetchingReference by remember { mutableStateOf(false) }

    val handleReferenceClick: (String) -> Unit = { ref ->
        if (ref.startsWith("S:")) {
            var code = ref.substring(2)
            if (" " in code) code = code.split(" ").first()
            if (!code.startsWith("H") && !code.startsWith("G")) code = "H$code"
            if (code.length != 5) code = code[0] + code.substring(1).padStart(4, '0')
            scope.launch {
                isFetchingReference = true
                try {
                    val entry = VocabularyRepository.getLexiconEntry(code)
                    if (entry != null) {
                        entry.shortDefinition = DeepLApiClient.translateText(entry.shortDefinition)
                        entry.fullDefinition = entry.fullDefinition?.let { DeepLApiClient.translateText(it) }
                        navigationStack.add(entry)
                    }
                } catch (_: Exception) {
                } finally {
                    isFetchingReference = false
                }
            }
        } else {
            versePreviewRef = ref
        }
    }

    LaunchedEffect(vocabularyList) {
        isTranslating = true
        try {
            // Collect all texts to translate in one batch
            val textsToTranslate = mutableListOf<String>()
            val mapping = mutableListOf<Pair<Int, String>>() // (vocabIndex, "short"|"full")

            for ((i, def) in vocabularyList.withIndex()) {
                textsToTranslate.add(def.shortDefinition)
                mapping.add(i to "short")
                def.fullDefinition?.let {
                    textsToTranslate.add(it)
                    mapping.add(i to "full")
                }
            }

            val translated = DeepLApiClient.translateBatch(textsToTranslate)

            for ((idx, pair) in mapping.withIndex()) {
                val (vocabIdx, field) = pair
                when (field) {
                    "short" -> vocabularyList[vocabIdx].shortDefinition = translated[idx]
                    "full" -> vocabularyList[vocabIdx].fullDefinition = translated[idx]
                }
            }
        } catch (_: Exception) {} finally {
            isTranslating = false
        }
    }

    LaunchedEffect(verseLexiconPayload) {
        if (verseLexiconPayload != null && aiLinkedWords == null) {
            isLinking = true
            try {
                // Strip accents for AI mapping to avoid fragmentation
                val cleanedVerse = verseLexiconPayload.verse.replace("\u0301", "").replace("\u0300", "").replace("́", "")
                val cleanedPayload = verseLexiconPayload.copy(verse = cleanedVerse)

                val prompt = """
Task: Map each Ukrainian word in 'verse' to exactly one Strong's tag from 'source'.

Input: A JSON object with 'verse' (Ukrainian text) and 'source' (array of Hebrew/Greek entries).
Each source entry has 'orig' (original text) and 'tags' (Strong's codes).

CRITICAL — Compound words:
Hebrew often attaches prefixes to root words, separated by "/" in both 'orig' and 'tags'.
Example: orig="וְ/הָ/אָרֶץ" tags="H9002/H9009/{H0776G}"
This means THREE parts: וְ=H9002 (conjunction "and"), הָ=H9009 (article "the"), אָרֶץ=H0776G (earth).
Each part maps to a SEPARATE Ukrainian word:
- "І" → "H9002" (conjunction)
- "земля" → "H0776G" (root word)
- H9009 (article) has no Ukrainian equivalent, so skip it.

Rules:
1. Every Ukrainian word should get exactly ONE tag (not the full compound tag string).
2. Split compound tags by "/" and assign each part to the corresponding Ukrainian word.
3. Conjunctions (і, й, та, а) typically come from prefix H9002 or H9001.
4. Articles (H9009) usually have no separate Ukrainian word — skip them.
5. Tags in curly braces like {H0776G} — use only the code inside: H0776G.
6. Backslash (\) separates punctuation marks — ignore those parts.
7. If a Ukrainian word has no matching tag, omit it from the output.

Output: Return ONLY a JSON array: [{"word": "І", "tags": "H9002"}, {"word": "земля", "tags": "H0776G"}]
Input JSON: ${json.encodeToString(VerseLexiconPayload.serializer(), cleanedPayload)}
                """.trimIndent()
                val response = withContext(Dispatchers.Default) {
                    GroqApiClient.generateResponse(prompt)
                }
                val cleanJson = stripJsonMarkdown(response)
                val result = json.decodeFromString<List<AILinkedWord>>(cleanJson)
                aiLinkedWords = result.onEach { it.tags = it.tags.take(5) } // take only the prefix and 4 digits
            } catch (_: Exception) {
                // Keep aiLinkedWords null on failure
            } finally {
                isLinking = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).safeDismissClick { onDismiss() }) {
        Surface(modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.9f).fillMaxHeight(0.8f)) {
            Column {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Інтерактивний Словник AI", style = MaterialTheme.typography.titleMedium)
                        Text("$selectedBookName $chapter:$verse", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (verseLexiconPayload == null) {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Завантаження даних вірша...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (isLinking) {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Прив'язка тексту AI...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (aiLinkedWords != null) {
                        val wordChars = "a-zA-Zа-яА-ЯіІїЇєЄґҐ\\u0300-\\u036f'"
                        val tokens = Regex("([$wordChars]+)|(\\s+)|([^$wordChars\\s]+)")
                            .findAll(verseLexiconPayload.verse)
                            .map { it.value }
                            .toList()

                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            for (token in tokens) {
                                if (token.isEmpty()) continue

                                val isWord = token.any { it.isLetterOrDigit() }

                                if (isWord) {
                                    val cleanToken = normalizeForMatch(token)
                                    val linked = aiLinkedWords?.find { normalizeForMatch(it.word) == cleanToken }

                                    Surface(
                                        color = if (linked != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .padding(vertical = 4.dp, horizontal = 2.dp)
                                            .clickable { if (linked != null) clickedLinkedWord = linked }
                                    ) {
                                        Text(
                                            text = token,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (linked != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (linked != null) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                } else {
                                    Text(
                                        text = token,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Не вдалося завантажити карту слів.", modifier = Modifier.align(Alignment.Center).padding(16.dp))
                    }
                }
            }
        }

        // Sub-dialog for specific Word Click
        if (clickedLinkedWord != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).safeDismissClick { clickedLinkedWord = null }) {
                Surface(modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.95f).fillMaxHeight(0.7f)) {
                    Column {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(clickedLinkedWord!!.word, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            IconButton(onClick = { clickedLinkedWord = null }) { Icon(Icons.Default.Close, "Закрити", tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                        }

                        if (versePreviewRef != null) {
                            VersePreviewPopup(
                                referenceUrl = versePreviewRef!!,
                                bible = bible,
                                onDismiss = { versePreviewRef = null }
                            )
                        }

                        if (navigationStack.isNotEmpty()) {
                            val currentEntry = navigationStack.last()
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { navigationStack.removeAt(navigationStack.size - 1) }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                                    }
                                    Text(currentEntry.strongCode, style = MaterialTheme.typography.titleMedium)
                                }
                                
                                Box(modifier = Modifier.weight(1f)) {
                                    VocabularyDetailView(
                                        entry = currentEntry,
                                        isTranslating = isFetchingReference,
                                        onReferenceClick = handleReferenceClick
                                    )
                                }
                            }
                        } else {
                            val activeTags = clickedLinkedWord!!.tags
                            val matchingDefs = vocabularyList.filter { def ->
                                val rawNumbers = def.strongCode.filter { it.isDigit() }
                                if (rawNumbers.isNotEmpty()) {
                                    activeTags.contains(rawNumbers)
                                } else {
                                    activeTags.contains(def.strongCode)
                                }
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                if (matchingDefs.isEmpty()) {
                                    item { Text("Словникових визначень не знайдено для: $activeTags", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                items(matchingDefs) { def ->
                                    SelectionContainer {
                                        VocabularyDetailView(
                                            entry = def,
                                            isTranslating = isTranslating,
                                            onReferenceClick = handleReferenceClick
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeForMatch(text: String): String {
    return text.lowercase()
        .replace("\u0301", "") // Combining Acute Accent
        .replace("\u0300", "") // Combining Grave Accent
        .replace("́", "")      // Literal accent if any
        .trim { !it.isLetterOrDigit() }
}
