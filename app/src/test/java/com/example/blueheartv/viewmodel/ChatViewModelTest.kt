package com.example.blueheartv.viewmodel

import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.db.MessageEntity
import com.example.blueheartv.db.SessionEntity
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
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
    fun sendMessage_persistsToDao() = runTest {
        val dao = FakeChatDao()
        val provider = ScriptedProvider { _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("ok"))
            onEvent(ChatStreamEvent.Completed)
        }

        val viewModel = createViewModel(chatProvider = provider, dao = dao)
        viewModel.onInputChanged("persist me")
        viewModel.sendMessage()
        advanceUntilIdle()

        val sessions = dao.getAllSessions()
        assertFalse(sessions.isEmpty())
        val messages = dao.getMessagesForSession(sessions.first().id)
        assertFalse(messages.isEmpty())
    }

    @Test
    fun restoreSessions_recoversLastConversation() = runTest {
        val dao = FakeChatDao()
        dao.upsertSession(SessionEntity("session-1", "恢复会话", 2000L, false))
        dao.upsertMessages(listOf(
            MessageEntity("m1", "session-1", "hi", true, "COMPLETED", null, null, 0),
            MessageEntity("m2", "session-1", "hello", false, "COMPLETED", null, null, 1),
        ))

        val viewModel = createViewModel(dao = dao)

        val uiState = viewModel.uiState.value
        assertFalse(uiState.histories.isEmpty())
        assertEquals("恢复会话", uiState.histories.first().title)
        assertEquals(2, uiState.messages.size)
        assertEquals(ChatState.CHAT_SIMPLE, uiState.chatState)
    }

    @Test
    fun persistSessions_debounced() = runTest {
        val dao = FakeChatDao()
        val provider = ScriptedProvider { _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("ok"))
            onEvent(ChatStreamEvent.Completed)
        }

        val viewModel = createViewModel(
            chatProvider = provider,
            dao = dao,
            persistDebounceMs = 100L,
        )

        viewModel.onInputChanged("first")
        viewModel.sendMessage()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.histories.firstOrNull()?.id
        require(!sessionId.isNullOrBlank())

        viewModel.renameSession(sessionId, "新标题")

        // Before debounce, DAO should not have the new title yet
        val beforeTitle = dao.getAllSessions().firstOrNull { it.id == sessionId }?.title
        assertTrue(beforeTitle == null || beforeTitle != "新标题")

        advanceTimeBy(150L)
        advanceUntilIdle()

        val afterTitle = dao.getAllSessions().firstOrNull { it.id == sessionId }?.title
        assertEquals("新标题", afterTitle)
    }

    private fun createViewModel(
        chatProvider: ChatProvider = ScriptedProvider { _, onEvent ->
            onEvent(ChatStreamEvent.Completed)
        },
        dao: FakeChatDao = FakeChatDao(),
        timeProvider: () -> Long = { System.currentTimeMillis() },
        persistDebounceMs: Long = 0L,
    ): ChatViewModel {
        val repo = ChatSessionRepository(
            dao = dao,
            timeProvider = timeProvider,
            idProvider = { "id-${System.nanoTime()}" },
            persistDebounceMs = persistDebounceMs,
            scope = this,
        )
        return ChatViewModel(
            chatProvider = chatProvider,
            repo = repo,
        )
    }

    private class ScriptedProvider(
        private val script: suspend (List<Message>, (ChatStreamEvent) -> Unit) -> Unit,
    ) : ChatProvider {
        override suspend fun streamReply(
            messages: List<Message>,
            onEvent: (ChatStreamEvent) -> Unit,
        ) {
            script(messages, onEvent)
        }
    }
}
