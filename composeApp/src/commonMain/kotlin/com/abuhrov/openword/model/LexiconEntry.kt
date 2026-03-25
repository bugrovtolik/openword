package com.abuhrov.openword.model

import kotlinx.serialization.Serializable

@Serializable
data class LexiconEntry(
    val strongCode: String,
    var shortDefinition: String,
    var fullDefinition: String?,
    val transliteration: String?,
    val originalWord: String?,
    val morphology: String?
)

@Serializable
data class WordTagMapping(
    val orig: String,
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
    var tags: String
)
