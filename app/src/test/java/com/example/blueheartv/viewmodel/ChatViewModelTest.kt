package com.example.blueheartv.viewmodel

import com.example.blueheartv.chat.ChatPrompt
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.chat.RemoteChatThread
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun sendMessage_streamSuccess_updatesMessagesAndState() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("hello"))
            onEvent(ChatStreamEvent.TextDelta(" world"))
            onEvent(ChatStreamEvent.Completed)
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("test")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(ChatSessionState.IDLE, uiState.sessionState)
        assertEquals(2, uiState.messages.size)
        assertTrue(uiState.messages.first().isUser)
        assertEquals("hello world", uiState.messages.last().content)
        assertEquals(MessageDeliveryState.COMPLETED, uiState.messages.last().deliveryState)
    }

    @Test
    fun streamError_setsRetryState() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.Error("network down", retryable = true))
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(ChatSessionState.ERROR, uiState.sessionState)
        assertTrue(uiState.canRetry)
        assertEquals("network down", uiState.lastError)
        assertEquals(MessageDeliveryState.FAILED, uiState.messages.last().deliveryState)
    }

    @Test
    fun repeatedModelInvocations_keepLatestOutputOnly() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("thinking", invocationId = "model-1"))
            onEvent(ChatStreamEvent.TextDelta(" plan", invocationId = "model-1"))
            onEvent(ChatStreamEvent.TextDelta("final", invocationId = "model-2"))
            onEvent(ChatStreamEvent.TextDelta(" answer", invocationId = "model-2"))
            onEvent(ChatStreamEvent.Completed)
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("test")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(2, uiState.messages.size)
        assertEquals("final answer", uiState.messages.last().content)
        assertEquals(MessageDeliveryState.COMPLETED, uiState.messages.last().deliveryState)
    }

    @Test
    fun restoreSessions_loadsRemoteThreads() = runTest {
        val provider = ScriptedProvider(
            remoteThreads = listOf(
                RemoteChatThread(
                    id = "thread-1",
                    title = "远端会话",
                    updatedAtMillis = 2000L,
                    messages = listOf(
                        Message("m1", "hi", isUser = true),
                        Message("m2", "hello", isUser = false),
                    ),
                ),
            ),
        )

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals("远端会话", uiState.histories.first().title)
        assertEquals(2, uiState.messages.size)
        assertEquals(ChatState.CHAT_SIMPLE, uiState.chatState)
    }

    private fun createViewModel(
        chatProvider: ChatProvider = ScriptedProvider(),
        timeProvider: () -> Long = { System.currentTimeMillis() },
    ): ChatViewModel {
        val repo = ChatSessionRepository(
            timeProvider = timeProvider,
            idProvider = { "id-${System.nanoTime()}" },
        )
        return ChatViewModel(
            chatProvider = chatProvider,
            repo = repo,
        )
    }

    private class ScriptedProvider(
        private val remoteThreads: List<RemoteChatThread> = emptyList(),
        private val script: suspend (String, ChatPrompt, (ChatStreamEvent) -> Unit) -> Unit = { _, _, onEvent ->
            onEvent(ChatStreamEvent.Completed)
        },
    ) : ChatProvider {
        override suspend fun createThread(titleHint: String?): RemoteChatThread {
            return RemoteChatThread("thread-${System.nanoTime()}", titleHint ?: "当前对话", 0L, emptyList())
        }

        override suspend fun loadThreads(limit: Int): List<RemoteChatThread> = remoteThreads

        override suspend fun loadThread(threadId: String): RemoteChatThread? {
            return remoteThreads.firstOrNull { it.id == threadId }
        }

        override suspend fun renameThread(threadId: String, title: String) = Unit

        override suspend fun deleteThread(threadId: String) = Unit

        override suspend fun streamReply(
            threadId: String,
            prompt: ChatPrompt,
            onEvent: (ChatStreamEvent) -> Unit,
        ) {
            script(threadId, prompt, onEvent)
        }
    }
}
