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
    val tagPattern = Regex("(<[^>]+>)|(\\{[^}]+\\})|(\\[(\\d+)\\])")

    return buildAnnotatedString {
        var lastIndex = 0
        val shadowText = StringBuilder()
        var fMarkerStart = -1 // Position where <f> content starts in the annotated string
        val fMarkerOriginal = StringBuilder() // Accumulates original (undecoded) marker text for DB lookup

        tagPattern.findAll(cleanText).forEach { matchResult ->
            val before = cleanText.substring(lastIndex, matchResult.range.first)
            if (fMarkerStart >= 0) {
                // Inside <f>...</f> — append decoded for display, accumulate original for annotation
                fMarkerOriginal.append(before)
                withStyle(SpanStyle(color = Color.Blue, fontWeight = FontWeight.Bold, baselineShift = BaselineShift(0.2f), fontSize = 12.sp)) {
                    append(decodeCircledLetters(before))
                }
            } else {
                append(before)
            }
            shadowText.append(before)

            val tag = matchResult.value

            // When inside <f>...</f>, only handle </f> closing tag, append everything else as marker text
            if (fMarkerStart >= 0) {
                if (tag == "</f>") {
                    addStringAnnotation(tag = "COMMENTARY_MARKER", annotation = fMarkerOriginal.toString(), start = fMarkerStart, end = this.length)
                    fMarkerOriginal.clear()
                    fMarkerStart = -1
                } else {
                    // Append matched content (like [1]) as styled marker text
                    fMarkerOriginal.append(tag)
                    withStyle(SpanStyle(color = Color.Blue, fontWeight = FontWeight.Bold, baselineShift = BaselineShift(0.2f), fontSize = 12.sp)) {
                        append(decodeCircledLetters(tag))
                    }
                }
                lastIndex = matchResult.range.last + 1
                return@forEach
            }

            if (tag.startsWith("{")) {
                val code = tag.removeSurrounding("{", "}")
                val currentLength = shadowText.length
                if (currentLength > 0) {
                    var wordStart = currentLength - 1
                    while (wordStart >= 0 && shadowText[wordStart].isWhitespace()) wordStart--
                    val wordEnd = wordStart + 1
                    while (wordStart >= 0 && !shadowText[wordStart].isWhitespace() && !shadowText[wordStart].isPunctuation()) wordStart--
                    wordStart++

                    if (wordStart < wordEnd) {
                        addStringAnnotation(tag = "STRONG", annotation = code, start = wordStart, end = wordEnd)
                    }
                }
            } else if (tag.startsWith("[")) {
                val number = matchResult.groupValues[4]
                pushStringAnnotation(tag = "COMMENTARY_MARKER", annotation = number)
                withStyle(SpanStyle(color = Color.Blue, fontWeight = FontWeight.Bold, baselineShift = BaselineShift(0.2f), fontSize = 12.sp)) {
                    append(number)
                }
                pop()
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
                    append(remaining)
                }
            } else {
                append(remaining)
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
    return Regex("(<[^>]+>)|(\\{[^}]+\\})|(\\[\\d+\\])").replace(
        text.removeNotes().replace(Regex("<f>.*?</f>\\s*"), ""), ""
    )
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
