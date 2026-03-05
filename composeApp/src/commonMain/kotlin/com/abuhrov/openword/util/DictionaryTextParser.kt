package com.abuhrov.openword.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

fun parseDictionaryText(text: String): AnnotatedString {
    // Regex to match <a href='...'>...</a>
    val linkPattern = Regex("<a\\s+href='([^']+)'>([^<]+)</a>")

    return buildAnnotatedString {
        var lastIndex = 0

        linkPattern.findAll(text).forEach { matchResult ->
            val before = text.substring(lastIndex, matchResult.range.first)
            // Replace <br> and <BR> with \n in regular text
            append(before.replace("<br>", "\n", ignoreCase = true))

            val url = matchResult.groupValues[1]
            val linkText = matchResult.groupValues[2]

            pushStringAnnotation(tag = "REFERENCE", annotation = url)
            withStyle(
                SpanStyle(
                    color = Color(0xFF0D47A1), // Darker blue
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(linkText.replace("<br>", "\n", ignoreCase = true))
            }
            pop()

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex).replace("<br>", "\n", ignoreCase = true))
        }
    }
}
