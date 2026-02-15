package com.abuhrov.openword.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

fun parseMarkdown(text: String): AnnotatedString {
    val pattern = Regex("(\\*\\*(.*?)\\*\\*)|(\\*(.*?)\\*)")
    return buildAnnotatedString {
        var lastIndex = 0
        pattern.findAll(text).forEach { matchResult ->
            append(text.substring(lastIndex, matchResult.range.first))
            val value = matchResult.value
            if (value.startsWith("**")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(matchResult.groupValues[2]) }
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(matchResult.groupValues[4]) }
            }
            lastIndex = matchResult.range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
}

fun stripJsonMarkdown(text: String): String {
    return text.replace(Regex("^```json"), "").replace(Regex("^```"), "").replace(Regex("```$"), "").trim()
}
