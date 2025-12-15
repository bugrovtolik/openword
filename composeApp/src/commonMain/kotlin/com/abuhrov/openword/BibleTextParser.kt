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
                    while (wordStart >= 0 && shadowText[wordStart].isWhitespace()) {
                        wordStart--
                    }
                    val wordEnd = wordStart + 1

                    while (wordStart >= 0 && !shadowText[wordStart].isWhitespace() && !shadowText[wordStart].isPunctuation()) {
                        wordStart--
                    }
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
            val remaining = cleanText.substring(lastIndex)
            append(remaining)
        }
    }
}

private fun Char.isPunctuation(): Boolean {
    return this in ",.;:!?"
}

fun stripTags(text: String): String {
    return Regex("(<[^>]+>)|(\\{[^}]+\\})").replace(text, "")
}

// FIX: Helper to pad Strong's codes with leading zeros (H430 -> H0430)
fun normalizeStrongCode(code: String): String {
    if (code.isEmpty()) return code
    val prefix = code[0]
    // Only normalize Strong's codes (H/G)
    if (prefix != 'H' && prefix != 'G') return code

    // Find where the numeric part ends
    var digitEnd = 1
    while (digitEnd < code.length && code[digitEnd].isDigit()) {
        digitEnd++
    }

    // Nothing to normalize if no digits found or only 1 char
    if (digitEnd == 1) return code

    val numberPart = code.substring(1, digitEnd)
    val suffix = code.substring(digitEnd)

    // Pad to 4 digits: "430" -> "0430"
    val paddedNumber = numberPart.padStart(4, '0')

    return "$prefix$paddedNumber$suffix"
}