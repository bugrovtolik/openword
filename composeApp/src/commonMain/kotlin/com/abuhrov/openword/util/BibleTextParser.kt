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
    val cleanText = text.removeNotes()
        .removeWTags()
        .replace(Regex("^(\\s*<pb/>)+"), "")
        .replace(Regex("<f>[\u24D0-\u24E9]+</f>"), "")
    val wordChars = "a-zA-Zа-яА-ЯіІїЇєЄґҐ"
    val tagPattern = Regex("(<[^>]+>)|(\\{[^}]+\\})|(\\[(\\d+)\\])|([$wordChars'-]+)\\*")

    return buildAnnotatedString {
        var lastIndex = 0
        val plainText = StringBuilder()
        var fMarkerStart = -1
        val fMarkerOriginal = StringBuilder()
        var fIsParenthesized = false

        fun appendText(str: String) {
            append(str)
            plainText.append(str)
        }

        fun attachToPreviousWord(markerAnnotation: String): Boolean {
            if (plainText.isEmpty()) return false
            var end = plainText.length - 1
            while (end >= 0 && (plainText[end].isWhitespace() || plainText[end].isPunctuation())) {
                end--
            }
            if (end < 0) return false
            
            var start = end
            while (start >= 0 && !plainText[start].isWhitespace() && !plainText[start].isPunctuation()) {
                start--
            }
            start++
            
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
                fMarkerOriginal.append(before)
            } else {
                appendText(before)
            }

            val tag = matchResult.value

            if (fMarkerStart >= 0) {
                if (tag == "</f>") {
                    val markerContent = fMarkerOriginal.toString()
                    if (fIsParenthesized) {
                        appendText(markerContent)
                    } else {
                        val cleanMarker = markerContent.replace(Regex("[\\[\\]()]"), "")
                        val isNumeric = cleanMarker.isNotEmpty() && cleanMarker.all { it.isDigit() || it.isWhitespace() || it in ".,- " }
                            && cleanMarker.any { it.isDigit() }
                        var attached = false
                        if (isNumeric) {
                            attached = attachToPreviousWord(cleanMarker)
                        }
                        if (!attached) {
                            pushStringAnnotation(tag = "COMMENTARY_MARKER", annotation = cleanMarker)
                            withStyle(SpanStyle(color = Color.Blue, fontWeight = FontWeight.Bold, baselineShift = BaselineShift(0.2f), fontSize = 12.sp)) {
                                appendText(decodeCircledLetters(markerContent))
                            }
                            pop()
                        }
                    }
                    fMarkerOriginal.clear()
                    fMarkerStart = -1
                    fIsParenthesized = false
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
                        fIsParenthesized = plainText.isNotEmpty() && plainText.last() == '('
                        fMarkerStart = this.length
                    }
                    "</f>" -> {
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

private fun String.removeNotes() = replace(Regex("<n>.*?</n>"), "")

private fun String.removeWTags() = replace(Regex("<w>[^<]*</w>"), "")

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
    // Remove tags and clean extra spaces at the same time
    val stripped = Regex("(<[^>]+>)|(\\{[^}]+\\})|(\\[\\d+\\])").replace(
        text.removeNotes().removeWTags().replace(Regex("<f>.*?</f>"), ""), ""
    ).replace(Regex("([$wordChars'-]+)\\*"), "$1")
    
    // Normalize spaces: multiple spaces become one
    return stripped.replace(Regex("\\s+"), " ").trim()
}

fun normalizeStrongCode(code: String): String {
    if (code.isEmpty()) return code
    val prefix = code[0].uppercaseChar()
    if (prefix != 'H' && prefix != 'G') return code
    val digits = code.substring(1).takeWhile { it.isDigit() }
    if (digits.isEmpty()) return code
    return prefix + digits.padStart(4, '0')
}
