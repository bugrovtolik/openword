package com.abuhrov.openword.util

import com.abuhrov.openword.model.ChatMessage
import kotlin.time.TimeSource

object AIChatHistoryManager {
    private var messages: List<ChatMessage> = emptyList()
    private var lastUpdatedTime = TimeSource.Monotonic.markNow()

    fun getMessagesWithTTL(): List<ChatMessage> {
        if (messages.isNotEmpty() && lastUpdatedTime.elapsedNow().inWholeHours >= 1) {
            messages = emptyList()
        }
        return messages
    }

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages = newMessages
        lastUpdatedTime = TimeSource.Monotonic.markNow()
    }
    
    fun clear() {
        messages = emptyList()
    }
}
