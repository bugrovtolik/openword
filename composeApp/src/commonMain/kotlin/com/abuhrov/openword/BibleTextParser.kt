package com.abuhrov.openword

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle

fun parseBibleText(text: String): AnnotatedString {
    val cleanText = text.replace(Regex("^(\\s*<pb/>)+"), "")
    val tagPattern = Regex("(<[^>]+>)|(\\{[^}]+\\})")

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

fun stripTags(text: String): String = Regex("(<[^>]+>)|(\\{[^}]+\\})").replace(text, "")

fun normalizeStrongCode(code: String): String {
    if (code.isEmpty()) return code
    val prefix = code[0]
    if (prefix != 'H' && prefix != 'G') return code

    var digitEnd = 1
    while (digitEnd < code.length && code[digitEnd].isDigit()) {
        digitEnd++
    }

    if (digitEnd == 1) return code

    val numberPart = code.substring(1, digitEnd)
    val suffix = code.substring(digitEnd)
    val paddedNumber = numberPart.padStart(4, '0')

    return "$prefix$paddedNumber$suffix"
}

fun parseCommentaryText(text: String): AnnotatedString {
    val trimmed = text.trimStart()
    if (trimmed.startsWith("{") || trimmed.startsWith("\\")) {
        return AnnotatedString(stripRtf(text))
    }

    // 2. Handle HTML-like tags (IVP, UBIO)
    val clean = text
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")

    return AnnotatedString(clean.trim())
}

/**
 * Robust RTF stripper that handles nested groups, ignorable destinations (headers/info),
 * and unicode characters.
 */
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
            '{' -> {
                ignoreStack.addLast(ignoring)
                i++
            }
            '}' -> {
                if (ignoreStack.isNotEmpty()) {
                    ignoring = ignoreStack.removeLast()
                }
                i++
            }
            '\\' -> {
                i++
                if (i >= len) break
                val next = rtf[i]

                // 1. Check for \* (Ignorable Destination)
                if (next == '*' && !ignoring) {
                    ignoring = true
                    i++
                }
                // 2. Control Word (starts with letter)
                else if (next.isLetter()) {
                    val start = i
                    while (i < len && rtf[i].isLetter()) i++
                    val word = rtf.substring(start, i)

                    // Parameter (optional digits/hyphen)
                    var hasParam = false
                    val paramStart = i
                    if (i < len && (rtf[i] == '-' || rtf[i].isDigit())) {
                        hasParam = true
                        if (rtf[i] == '-') i++
                        while (i < len && rtf[i].isDigit()) i++
                    }
                    val param = if (hasParam) rtf.substring(paramStart, i).toIntOrNull() else null

                    // Delimiter (space) consumes the space
                    if (i < len && rtf[i] == ' ') i++

                    if (!ignoring) {
                        if (ignoreDestinations.contains(word)) {
                            ignoring = true
                        } else {
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
                // 3. Hex Character \'xx
                else if (next == '\'') {
                    i++
                    if (!ignoring && i + 1 < len) {
                        try {
                            val hex = rtf.substring(i, i + 2)
                            result.append(hex.toInt(16).toChar())
                        } catch (e: Exception) {}
                        i += 2
                    }
                }
                // 4. Escaped Symbols \\, \{, \}
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
            else -> {
                if (!ignoring) result.append(c)
                i++
            }
        }
    }

    return result.toString()
        .replace(Regex("\\n\\s*\\n+"), "\n\n")
        .trim()
}