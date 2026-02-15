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
