package com.abuhrov.openword.model

import kotlinx.serialization.Serializable

@Serializable
data class LexiconEntry(
    val strongCode: String,
    val originalWord: String,
    val gloss: String,
    val transliteration: String?,
    val definition: String?
)

@Serializable
data class WordTagMapping(
    val heb: String,
    val tags: String
)

@Serializable
data class VerseLexiconPayload(
    val verse: String,
    val source: List<WordTagMapping>
)

@Serializable
data class AILinkedWord(
    val word: String,
    val tags: String
)
