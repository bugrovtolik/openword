package com.abuhrov.openword.network.model

import com.abuhrov.openword.model.ChatMessage
import kotlinx.serialization.Serializable

@Serializable
data class ProxyRequest(val history: List<ChatMessage>, val stream: Boolean = false)
