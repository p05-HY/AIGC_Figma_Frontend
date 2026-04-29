package com.example.blueheartv.chat

import com.example.blueheartv.model.ChatAttachment
import com.example.blueheartv.model.Message

data class ChatPrompt(
    val text: String,
    val attachments: List<ChatAttachment> = emptyList(),
)

data class RemoteChatThread(
    val id: String,
    val title: String,
    val updatedAtMillis: Long,
    val messages: List<Message>,
)

interface ChatProvider {
    suspend fun createThread(titleHint: String? = null): RemoteChatThread

    suspend fun loadThreads(limit: Int = 20): List<RemoteChatThread>

    suspend fun loadThread(threadId: String): RemoteChatThread?

    suspend fun renameThread(threadId: String, title: String)

    suspend fun deleteThread(threadId: String)

    suspend fun streamReply(
        threadId: String,
        prompt: ChatPrompt,
        onEvent: (ChatStreamEvent) -> Unit,
    )
}
