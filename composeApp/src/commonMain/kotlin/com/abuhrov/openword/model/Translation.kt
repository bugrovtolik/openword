package com.abuhrov.openword.model

data class Translation(
    val id: String,
    val displayName: String,
    val fileName: String,
    val commentarySource: CommentarySource? = null
)
