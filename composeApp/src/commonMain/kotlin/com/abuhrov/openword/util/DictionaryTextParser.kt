package com.abuhrov.openword.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

fun parseDictionaryText(text: String): AnnotatedString {
    var cleanText = text.replace("<br>", "\n", ignoreCase = true)
        .replace("<br />", "\n", ignoreCase = true)
        .replace("<p />", "\n", ignoreCase = true)
        .replace("<p/>", "\n", ignoreCase = true)
        .replace("<BR>", "\n", ignoreCase = true)
        .replace("<BR />", "\n", ignoreCase = true)

    cleanText = decodeHtmlEntities(cleanText)

    val tagPattern = Regex("(<[^>]+>)")

    return buildAnnotatedString {
        var lastIndex = 0

        tagPattern.findAll(cleanText).forEach { matchResult ->
            val before = cleanText.substring(lastIndex, matchResult.range.first)
            append(before)

            val tag = matchResult.value
            
            when {
                tag.startsWith("<a ", ignoreCase = true) -> {
                    val urlMatch = Regex("href=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).find(tag)
                    val url = urlMatch?.groupValues?.get(1) ?: ""
                    pushStringAnnotation(tag = "REFERENCE", annotation = url)
                    pushStyle(SpanStyle(color = Color(0xFF0D47A1), textDecoration = TextDecoration.Underline))
                }
                tag.equals("</a>", ignoreCase = true) -> {
                    try { pop(); pop() } catch (e: Exception) {}
                }
                tag.startsWith("<ref", ignoreCase = true) -> {
                    val refMatch = Regex("ref=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).find(tag)
                    val ref = refMatch?.groupValues?.get(1) ?: ""
                    pushStringAnnotation(tag = "BIBLE_REF", annotation = ref)
                    pushStyle(SpanStyle(color = Color(0xFF0D47A1), textDecoration = TextDecoration.Underline))
                }
                tag.equals("</ref>", ignoreCase = true) -> {
                    try { pop(); pop() } catch (e: Exception) {}
                }
                tag.equals("<b>", ignoreCase = true) -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                }
                tag.equals("</b>", ignoreCase = true) -> {
                    try { pop() } catch (e: Exception) {}
                }
                tag.equals("<i>", ignoreCase = true) -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                }
                tag.equals("</i>", ignoreCase = true) -> {
                    try { pop() } catch (e: Exception) {}
                }
                tag.equals("<u>", ignoreCase = true) -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                }
                tag.equals("</u>", ignoreCase = true) -> {
                    try { pop() } catch (e: Exception) {}
                }
                tag.startsWith("<p", ignoreCase = true) -> {
                    if (length > 0) append("\n")
                }
                tag.equals("</p>", ignoreCase = true) -> {
                    if (length > 0) append("\n")
                }
                tag.startsWith("<li", ignoreCase = true) -> {
                    if (length > 0) append("\n")
                    append("• ")
                }
                tag.equals("</li>", ignoreCase = true) -> {
                    if (length > 0) append("\n")
                }
                tag.equals("<el>", ignoreCase = true) -> {
                    pushStyle(SpanStyle(color = Color(0xFFD32F2F)))
                }
                tag.equals("</el>", ignoreCase = true) -> {
                    try { pop() } catch (e: Exception) {}
                }
                // Ignore other unknown/invalid tags implicitly by not appending them
            }

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < cleanText.length) {
            append(cleanText.substring(lastIndex))
        }
    }
}

private fun decodeHtmlEntities(text: String): String {
    if ("&" !in text) return text
    var result = text
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&apos;", "'")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
    
    // Hex: &#x[0-9a-fA-F]+;
    result = Regex("&#x([0-9a-fA-F]+);").replace(result) { 
        try { it.groupValues[1].toInt(16).toChar().toString() } catch (_: Exception) { it.value } 
    }
    
    // Decimal: &#[0-9]+;
    result = Regex("&#([0-9]+);").replace(result) { 
        try { it.groupValues[1].toInt().toChar().toString() } catch (_: Exception) { it.value } 
    }
    
    return result
}
