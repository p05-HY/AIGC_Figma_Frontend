package com.example.blueheartv.viewmodel

import com.example.blueheartv.model.Message

data class ConversationSession(
    val id: String,
    var title: String,
    var updatedAtMillis: Long,
    var isPinned: Boolean = false,
    val messages: MutableList<Message>,
)
