package com.example.blueheartv.chat

interface ChatProvider {
    suspend fun streamReply(
        prompt: String,
        onEvent: (ChatStreamEvent) -> Unit,
    )
}
