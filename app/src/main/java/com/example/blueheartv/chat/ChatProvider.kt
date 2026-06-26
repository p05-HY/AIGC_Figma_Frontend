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

/** 服务端取消请求的确认；accepted 仅表示请求已送达，不代表任务已经终止。 */
data class MobileRunCancellation(
    val runId: String,
    val accepted: Boolean,
    val backendStatus: String,
)

/** 由移动安全门面查询的真实 LangGraph 运行状态。 */
data class MobileRunStatus(
    val runId: String,
    val localStatus: String,
    val backendStatus: String,
    val terminal: Boolean,
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

    /**
     * A caller-owned run id allows cancellation before the first SSE trace
     * frame arrives. Legacy providers keep the original stream implementation.
     */
    suspend fun streamReplyWithRun(
        threadId: String,
        prompt: ChatPrompt,
        runId: String,
        onEvent: (ChatStreamEvent) -> Unit,
    ) = streamReply(threadId, prompt, onEvent)

    suspend fun cancelRun(threadId: String, runId: String): MobileRunCancellation =
        MobileRunCancellation(runId, accepted = false, backendStatus = "unavailable")

    suspend fun getRunStatus(threadId: String, runId: String): MobileRunStatus =
        MobileRunStatus(runId, localStatus = "unknown", backendStatus = "unavailable", terminal = false)
}
