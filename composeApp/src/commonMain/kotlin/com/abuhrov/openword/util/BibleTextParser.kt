package com.abuhrov.openword.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

fun parseBibleText(text: String): AnnotatedString {
    val cleanText = text.removeNotes().replace(Regex("^(\\s*<pb/>)+"), "")
    val wordChars = "a-zA-Zа-яА-ЯіІїЇєЄґҐ"
    val tagPattern = Regex("(<[^>]+>)|(\\{[^}]+\\})|(\\[(\\d+)\\])|([$wordChars'-]+)\\*")

    return buildAnnotatedString {
        var lastIndex = 0
        val plainText = StringBuilder()
        var fMarkerStart = -1 // Position where <f> content starts in the annotated string
        val fMarkerOriginal = StringBuilder() // Accumulates original (undecoded) marker text for DB lookup

        fun appendText(str: String) {
            append(str)
            plainText.append(str)
        }

        fun attachToPreviousWord(markerAnnotation: String): Boolean {
            if (plainText.isEmpty()) return false
            var end = plainText.length - 1
            // skip trailing spaces/punctuation
            while (end >= 0 && (plainText[end].isWhitespace() || plainText[end].isPunctuation())) {
                end--
            }
            if (end < 0) return false // No word found
            
            var start = end
            while (start >= 0 && !plainText[start].isWhitespace() && !plainText[start].isPunctuation()) {
                start--
            }
            start++ // first char of the word
            
            if (start <= end) {
                addStringAnnotation(tag = "COMMENTARY_MARKER", annotation = markerAnnotation, start = start, end = end + 1)
                addStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, end + 1)
                return true
            }
            return false
        }

        tagPattern.findAll(cleanText).forEach { matchResult ->
            val before = cleanText.substring(lastIndex, matchResult.range.first)
            if (fMarkerStart >= 0) {
                // Inside <f>...</f> — ONLY accumulate original for annotation, don't append yet
                fMarkerOriginal.append(before)
            } else {
                appendText(before)
            }

            val tag = matchResult.value

            // When inside <f>...</f>, only handle </f> closing tag, append everything else as marker text
            if (fMarkerStart >= 0) {
                if (tag == "</f>") {
                    val markerContent = fMarkerOriginal.toString()
                    val isNumeric = markerContent.isNotEmpty() && markerContent.all { it.isDigit() || it.isWhitespace() || it in ".,- " }
                        && markerContent.any { it.isDigit() }
                    var attached = false
                    if (isNumeric) {
                        attached = attachToPreviousWord(markerContent)
                    }
                    if (!attached) {
                        pushStringAnnotation(tag = "COMMENTARY_MARKER", annotation = markerContent)
                        withStyle(SpanStyle(color = Color.Blue, fontWeight = FontWeight.Bold, baselineShift = BaselineShift(0.2f), fontSize = 12.sp)) {
                            appendText(decodeCircledLetters(markerContent))
                        }
                        pop()
                    }
                    fMarkerOriginal.clear()
                    fMarkerStart = -1
                } else {
                    fMarkerOriginal.append(tag)
                }
                lastIndex = matchResult.range.last + 1
                return@forEach
            }

            if (tag.endsWith("*") && !tag.startsWith("<")) {
                val word = matchResult.groupValues[5]
                val exists = com.abuhrov.openword.data.repository.DictionaryRepository.hasDefinitionSync(word)
                if (exists) {
                    pushStringAnnotation(tag = "DICTIONARY_WORD", annotation = word)
                    withStyle(SpanStyle(color = Color(0xFF00796B), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                        appendText(word)
                    }
                    pop()
                } else {
                    appendText(word)
                }
            } else if (tag.startsWith("{")) {
                val code = tag.removeSurrounding("{", "}")
                val currentLength = plainText.length
                if (currentLength > 0) {
                    var wordStart = currentLength - 1
                    while (wordStart >= 0 && plainText[wordStart].isWhitespace()) wordStart--
                    val wordEnd = wordStart + 1
                    while (wordStart >= 0 && !plainText[wordStart].isWhitespace() && !plainText[wordStart].isPunctuation()) wordStart--
                    wordStart++

                    if (wordStart < wordEnd) {
                        addStringAnnotation(tag = "STRONG", annotation = code, start = wordStart, end = wordEnd)
                    }
                }
            } else if (tag.startsWith("[")) {
                val number = matchResult.groupValues[4]
                val attached = attachToPreviousWord(number)
                if (!attached) {
                    pushStringAnnotation(tag = "COMMENTARY_MARKER", annotation = number)
                    withStyle(SpanStyle(color = Color.Blue, fontWeight = FontWeight.Bold, baselineShift = BaselineShift(0.2f), fontSize = 12.sp)) {
                        appendText(number)
                    }
                    pop()
                }
            } else {
                when (tag) {
                    "<J>" -> pushStyle(SpanStyle(color = Color(0xFFB71C1C)))
                    "</J>" -> try { pop() } catch (e: Exception) {}
                    "<i>" -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    "</i>" -> try { pop() } catch (e: Exception) {}
                    "<f>" -> {
                        fMarkerStart = this.length
                    }
                    "</f>" -> {
                        // Orphan </f> without <f> — ignore
                    }
                }
            }
            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < cleanText.length) {
            val remaining = cleanText.substring(lastIndex)
            if (fMarkerStart >= 0) {
                withStyle(SpanStyle(color = Color.Blue, fontWeight = FontWeight.Bold, baselineShift = BaselineShift(0.2f), fontSize = 12.sp)) {
                    appendText(remaining)
                }
            } else {
                appendText(remaining)
            }
        }
    }
}

private fun Char.isPunctuation(): Boolean = this in ",.;:!?"

// Simplified note removal
private fun String.removeNotes() = replace(Regex("<n>.*?</n>\\s*"), "")

// Convert circled Unicode letters (ⓐ-ⓩ U+24D0..U+24E9) to regular lowercase letters for display
private fun decodeCircledLetters(text: String): String {
    return buildString {
        for (ch in text) {
            if (ch in '\u24D0'..'\u24E9') {
                append(('a' + (ch - '\u24D0')))
            } else {
                append(ch)
            }
        }
    }
}

fun stripTags(text: String): String {
    val wordChars = "a-zA-Zа-яА-ЯіІїЇєЄґҐ"
    return Regex("(<[^>]+>)|(\\{[^}]+\\})|(\\[\\d+\\])").replace(
        text.removeNotes().replace(Regex("<f>.*?</f>\\s*"), ""), ""
    ).replace(Regex("([$wordChars'-]+)\\*"), "$1")
}

fun normalizeStrongCode(code: String): String {
    if (code.isEmpty()) return code
    val prefix = code[0]
    if (prefix != 'H' && prefix != 'G') return code
    var digitEnd = 1
    while (digitEnd < code.length && code[digitEnd].isDigit()) digitEnd++
    if (digitEnd == 1) return code
    return "$prefix${code.substring(1, digitEnd).padStart(4, '0')}${code.substring(digitEnd)}"
}
