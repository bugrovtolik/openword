package com.abuhrov.openword.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

// --- Commentary Parsing ---

fun parseCommentaryText(text: String): AnnotatedString {
    val textWithoutNotes = text.replace(Regex("<n>.*?</n>\\s*"), "")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("\\s*\\([^)]*\\)"), "")
    val trimmed = textWithoutNotes.trimStart()

    if (trimmed.startsWith("{") || trimmed.startsWith("\\")) {
        return AnnotatedString(stripRtf(textWithoutNotes))
    }

    val linkPattern = Regex("<a\\s+href='([^']+)'>([^<]+)</a>")

    val withNewlines = textWithoutNotes
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")

    return buildAnnotatedString {
        var lastIndex = 0

        linkPattern.findAll(withNewlines).forEach { matchResult ->
            val before = withNewlines.substring(lastIndex, matchResult.range.first)
            append(before.replace(Regex("<[^>]+>"), ""))
            
            val url = matchResult.groupValues[1]
            val linkText = matchResult.groupValues[2]

            pushStringAnnotation(tag = "REFERENCE", annotation = url)
            withStyle(
                SpanStyle(
                    color = androidx.compose.ui.graphics.Color(0xFF0D47A1),
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                )
            ) {
                append(linkText)
            }
            pop()

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < withNewlines.length) {
            append(withNewlines.substring(lastIndex).replace(Regex("<[^>]+>"), ""))
        }
    }
}

fun stripRtf(rtf: String): String {
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
                    val start = i
                    while (i < len && rtf[i].isLetter()) i++
                    val word = rtf.substring(start, i)
                    var hasParam = false
                    val paramStart = i
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
                                "ldblquote" -> result.append("\u201C")
                                "rdblquote" -> result.append("\u201D")
                                "lquote" -> result.append("\u2018")
                                "rquote" -> result.append("\u2019")
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
