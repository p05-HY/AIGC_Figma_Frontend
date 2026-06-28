package com.example.blueheartv.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentServerChatProvider(
    private val client: AgentServerClient,
) : ChatProvider {

    override suspend fun createThread(titleHint: String?): RemoteChatThread = withContext(Dispatchers.IO) {
        client.createThread(titleHint)
    }

    override suspend fun loadThreads(limit: Int): List<RemoteChatThread> = withContext(Dispatchers.IO) {
        client.searchThreads(limit)
    }

    override suspend fun loadThread(threadId: String): RemoteChatThread? = withContext(Dispatchers.IO) {
        runCatching { client.getThread(threadId) }.getOrNull()
    }

    override suspend fun renameThread(threadId: String, title: String) = withContext(Dispatchers.IO) {
        client.updateThreadTitle(threadId, title)
    }

    override suspend fun deleteThread(threadId: String) = withContext(Dispatchers.IO) {
        client.deleteThread(threadId)
    }

    override suspend fun transcribeVoice(
        audio: ByteArray,
        audioFormat: String,
        sampleRate: Int,
        language: String,
    ): VoiceTranscriptionResult = withContext(Dispatchers.IO) {
        client.transcribeVoice(audio, audioFormat, sampleRate, language)
    }

    override suspend fun streamReply(
        threadId: String,
        prompt: ChatPrompt,
        onEvent: (ChatStreamEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        client.streamRun(threadId, prompt, onEvent = onEvent)
    }

    override suspend fun streamReplyWithRun(
        threadId: String,
        prompt: ChatPrompt,
        runId: String,
        onEvent: (ChatStreamEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        client.streamRun(threadId, prompt, runId, onEvent)
    }

    override suspend fun cancelRun(
        threadId: String,
        runId: String,
        cancelSource: String,
    ): MobileRunCancellation = withContext(Dispatchers.IO) {
        client.cancelRun(threadId, runId, cancelSource)
    }

    override suspend fun getRunStatus(threadId: String, runId: String): MobileRunStatus = withContext(Dispatchers.IO) {
        client.getRunStatus(threadId, runId)
    }
}
