package com.example.blueheartv.viewmodel

import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatSessionsSnapshot
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.chat.StoredChatSession
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun sendMessage_streamSuccess_updatesMessagesAndState() = runTest {
        val provider = ScriptedProvider { _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("hello"))
            onEvent(ChatStreamEvent.TextDelta(" world"))
            onEvent(ChatStreamEvent.Completed)
        }

        val viewModel = createViewModel(chatProvider = provider)
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
    fun sendMessage_debounce_blocksRapidDuplicateSends() = runTest {
        var now = 1000L
        var called = 0
        val provider = ScriptedProvider { _, onEvent ->
            called += 1
            onEvent(ChatStreamEvent.TextDelta("ok"))
            onEvent(ChatStreamEvent.Completed)
        }

        val viewModel = createViewModel(
            chatProvider = provider,
            timeProvider = { now },
        )

        viewModel.onInputChanged("hello")
        viewModel.sendMessage()
        viewModel.onInputChanged("hello")
        viewModel.sendMessage()

        advanceUntilIdle()
        assertEquals(1, called)

        now += 600
        viewModel.onInputChanged("hello again")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(2, called)
    }

    @Test
    fun streamError_setsRetryState() = runTest {
        val provider = ScriptedProvider { _, onEvent ->
            onEvent(ChatStreamEvent.Error("network down", retryable = true))
        }

        val viewModel = createViewModel(chatProvider = provider)
        viewModel.onInputChanged("hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(ChatSessionState.ERROR, uiState.sessionState)
        assertTrue(uiState.canRetry)
        assertEquals("network down", uiState.lastError)
        assertNotNull(uiState.messages.last().errorMessage)
    }

    @Test
    fun sendMessage_persistsSnapshot() = runTest {
        var savedSnapshot: ChatSessionsSnapshot? = null
        val provider = ScriptedProvider { _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("ok"))
            onEvent(ChatStreamEvent.Completed)
        }

        val viewModel = createViewModel(
            chatProvider = provider,
            saver = { savedSnapshot = it },
        )
        viewModel.onInputChanged("persist me")
        viewModel.sendMessage()
        advanceUntilIdle()

        val snapshot = savedSnapshot
        assertNotNull(snapshot)
        assertFalse(snapshot!!.sessions.isEmpty())
        assertFalse(snapshot.sessions.first().messages.isEmpty())
    }

    @Test
    fun restoreSessions_recoversLastConversation() = runTest {
        val snapshot = ChatSessionsSnapshot(
            activeSessionId = "session-1",
            sessions = listOf(
                StoredChatSession(
                    id = "session-1",
                    title = "恢复会话",
                    updatedAtMillis = 2000L,
                    messages = listOf(
                        Message("m1", "hi", isUser = true),
                        Message("m2", "hello", isUser = false),
                    ),
                ),
            ),
        )

        val viewModel = createViewModel(
            loader = { snapshot },
            saver = {},
        )

        val uiState = viewModel.uiState.value
        assertFalse(uiState.histories.isEmpty())
        assertEquals("恢复会话", uiState.histories.first().title)
        assertEquals(2, uiState.messages.size)
        assertEquals(ChatState.CHAT_SIMPLE, uiState.chatState)
    }

    private fun createViewModel(
        chatProvider: ChatProvider = ScriptedProvider { _, onEvent ->
            onEvent(ChatStreamEvent.Completed)
        },
        loader: () -> ChatSessionsSnapshot = { ChatSessionsSnapshot(null, emptyList()) },
        saver: (ChatSessionsSnapshot) -> Unit = {},
        timeProvider: () -> Long = { System.currentTimeMillis() },
    ): ChatViewModel {
        return ChatViewModel(
            chatProvider = chatProvider,
            snapshotLoader = loader,
            snapshotSaver = saver,
            timeProvider = timeProvider,
            idProvider = { "id-${System.nanoTime()}" },
        )
    }

    private class ScriptedProvider(
        private val script: suspend (String, (ChatStreamEvent) -> Unit) -> Unit,
    ) : ChatProvider {
        override suspend fun streamReply(
            prompt: String,
            onEvent: (ChatStreamEvent) -> Unit,
        ) {
            script(prompt, onEvent)
        }
    }
}
