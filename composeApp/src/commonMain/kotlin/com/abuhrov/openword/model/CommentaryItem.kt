package com.abuhrov.openword.model

import kotlinx.serialization.Serializable

@Serializable
data class CommentaryItem(
    val sourceName: String,
    val chapter: Long,
    val verseStart: Long,
    val verseEnd: Long,
    val text: String
)
