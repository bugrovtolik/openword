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

        tagPattern.findAll(cleanText).forEach { matchResult ->
            val before = cleanText.substring(lastIndex, matchResult.range.first)
            append(before)
            shadowText.append(before)

            val tag = matchResult.value

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
                }
            }
            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < cleanText.length) {
            append(cleanText.substring(lastIndex))
        }
    }
}

private fun Char.isPunctuation(): Boolean = this in ",.;:!?"

// Simplified note removal
private fun String.removeNotes() = replace(Regex("<n>.*?</n>\\s*"), "")

fun stripTags(text: String): String {
    return Regex("(<[^>]+>)|(\\{[^}]+\\})|(\\[\\d+\\])").replace(text.removeNotes(), "")
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
