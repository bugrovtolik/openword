package com.abuhrov.openword

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

// --- Commentary Parsing ---

fun parseCommentaryText(text: String): AnnotatedString {
    val textWithoutNotes = text.removeNotes()
    val trimmed = textWithoutNotes.trimStart()

    if (trimmed.startsWith("{") || trimmed.startsWith("\\")) {
        return AnnotatedString(stripRtf(textWithoutNotes))
    }

    val clean = textWithoutNotes
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")

    return AnnotatedString(clean.trim())
}

private fun stripRtf(rtf: String): String {
    val result = StringBuilder()
    var i = 0
    val len = rtf.length
    val ignoreStack = ArrayDeque<Boolean>()
    var ignoring = false
    val ignoreDestinations = setOf(
        "info", "stylesheet", "fonttbl", "colortbl", "header", "footer",
        "pict", "private", "xe", "tc", "txe", "mmath"
    )
    var ucSkip = 1

    while (i < len) {
        val c = rtf[i]
        when (c) {
            '{' -> { ignoreStack.addLast(ignoring); i++ }
            '}' -> { if (ignoreStack.isNotEmpty()) ignoring = ignoreStack.removeLast(); i++ }
            '\\' -> {
                i++
                if (i >= len) break
                val next = rtf[i]
                if (next == '*' && !ignoring) { ignoring = true; i++ }
                else if (next.isLetter()) {
                    var start = i
                    while (i < len && rtf[i].isLetter()) i++
                    val word = rtf.substring(start, i)
                    var hasParam = false
                    var paramStart = i
                    if (i < len && (rtf[i] == '-' || rtf[i].isDigit())) {
                        hasParam = true
                        if (rtf[i] == '-') i++
                        while (i < len && rtf[i].isDigit()) i++
                    }
                    val param = if (hasParam) rtf.substring(paramStart, i).toIntOrNull() else null
                    if (i < len && rtf[i] == ' ') i++
                    if (!ignoring) {
                        if (ignoreDestinations.contains(word)) ignoring = true
                        else {
                            when (word) {
                                "par", "line", "row", "page" -> result.append('\n')
                                "tab" -> result.append('\t')
                                "emdash" -> result.append("—")
                                "endash" -> result.append("–")
                                "ldblquote" -> result.append("“")
                                "rdblquote" -> result.append("”")
                                "lquote" -> result.append("‘")
                                "rquote" -> result.append("’")
                                "uc" -> if (param != null) ucSkip = param
                                "u" -> {
                                    if (param != null) {
                                        val code = if (param < 0) param + 65536 else param
                                        result.append(code.toChar())
                                        var skipCount = ucSkip
                                        while (skipCount > 0 && i < len) {
                                            if (rtf[i] == '?') i++
                                            skipCount--
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else if (next == '\'') {
                    i++
                    if (!ignoring && i + 1 < len) {
                        try { result.append(rtf.substring(i, i + 2).toInt(16).toChar()) } catch (e: Exception) {}
                        i += 2
                    }
                }
                else {
                    if (!ignoring) {
                        when (next) {
                            '\\', '{', '}' -> result.append(next)
                            '~' -> result.append(' ')
                            '_' -> result.append('-')
                        }
                    }
                    i++
                }
            }
            '\r', '\n' -> i++
            else -> { if (!ignoring) result.append(c); i++ }
        }
    }
    return result.toString().replace(Regex("\\n\\s*\\n+"), "\n\n").trim()
}