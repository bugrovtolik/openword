package com.abuhrov.openword.model

import kotlinx.serialization.Serializable

@Serializable
data class CommentarySource(
    val displayName: String,
    val fileName: String
)
