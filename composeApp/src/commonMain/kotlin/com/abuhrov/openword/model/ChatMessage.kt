package com.abuhrov.openword.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String
)
