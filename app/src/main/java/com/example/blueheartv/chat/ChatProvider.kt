package com.example.blueheartv.chat

import com.example.blueheartv.model.Message

interface ChatProvider {
    suspend fun streamReply(
        messages: List<Message>,
        onEvent: (ChatStreamEvent) -> Unit,
    )
}
