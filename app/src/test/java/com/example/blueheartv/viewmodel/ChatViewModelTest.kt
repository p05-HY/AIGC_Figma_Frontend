package com.example.blueheartv.viewmodel

import com.example.blueheartv.chat.AgentServerConfigStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        AgentServerConfigStore.setForTesting(baseUrl = "http://localhost:2024", apiKey = "test-key")
    }

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
    fun sendMessage_blankInput_doesNotCreateThreadOrConsumeDebounce() = runTest {
        var now = 1_000L
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("ok"))
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(
            chatProvider = provider,
            timeProvider = { now },
        )
        advanceUntilIdle()

        viewModel.onInputChanged("   ")
        viewModel.sendMessage()
        now += 100L
        viewModel.onInputChanged("after blank")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1, provider.createThreadCalls)
        assertEquals(1, provider.streamReplyCalls)
        assertEquals("after blank", viewModel.uiState.value.messages.first().content)
    }

    @Test
    fun deleteUserMessage_thenUndo_restoresUserAndPairedAssistantMessages() = runTest {
        val provider = ScriptedProvider { _, prompt, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("reply:${prompt.text}"))
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        val userId = viewModel.uiState.value.messages.first().id
        val undo = viewModel.deleteMessage(userId)
        assertNotNull(undo)
        assertEquals(0, viewModel.uiState.value.messages.size)

        viewModel.undoLastMessageMutation(undo!!.token)

        val restored = viewModel.uiState.value.messages
        assertEquals(2, restored.size)
        assertEquals("hello", restored.first().content)
        assertEquals("reply:hello", restored.last().content)
    }

    @Test
    fun deleteAiMessage_thenUndo_restoresAssistantMessage() = runTest {
        val provider = ScriptedProvider { _, prompt, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("reply:${prompt.text}"))
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        val assistantId = viewModel.uiState.value.messages.last().id
        val undo = viewModel.deleteAiMessage(assistantId)
        assertNotNull(undo)
        assertEquals(1, viewModel.uiState.value.messages.size)

        viewModel.undoLastMessageMutation(undo!!.token)

        val restored = viewModel.uiState.value.messages
        assertEquals(2, restored.size)
        assertEquals("reply:hello", restored.last().content)
    }

    @Test
    fun editAndResend_thenUndo_restoresOriginalConversation() = runTest {
        var now = 1_000L
        val provider = ScriptedProvider { _, prompt, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("reply:${prompt.text}"))
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(chatProvider = provider, timeProvider = { now })
        advanceUntilIdle()
        viewModel.onInputChanged("first")
        viewModel.sendMessage()
        advanceUntilIdle()
        now += 1_000L
        viewModel.onInputChanged("second")
        viewModel.sendMessage()
        advanceUntilIdle()

        val original = viewModel.uiState.value.messages.map { it.content }
        val firstUserId = viewModel.uiState.value.messages.first().id
        now += 1_000L
        val undo = viewModel.editAndResend(firstUserId, "changed")
        assertNotNull(undo)
        advanceUntilIdle()
        assertEquals(listOf("changed", "reply:changed"), viewModel.uiState.value.messages.map { it.content })

        viewModel.undoLastMessageMutation(undo!!.token)

        assertEquals(original, viewModel.uiState.value.messages.map { it.content })
    }

    @Test
    fun editAndResend_whenConfigMissing_doesNotRemoveOriginalMessages() = runTest {
        val provider = ScriptedProvider { _, prompt, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("reply:${prompt.text}"))
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        val original = viewModel.uiState.value.messages.map { it.content }
        val userId = viewModel.uiState.value.messages.first().id
        AgentServerConfigStore.setForTesting(baseUrl = "", apiKey = "")

        val undo = viewModel.editAndResend(userId, "changed")

        assertNull(undo)
        assertEquals(original, viewModel.uiState.value.messages.map { it.content })
        assertEquals(1, provider.streamReplyCalls)
    }

    @Test
    fun undoLastMessageMutation_ignoresStaleUndoToken() = runTest {
        var now = 1_000L
        val provider = ScriptedProvider { _, prompt, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("reply:${prompt.text}"))
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(chatProvider = provider, timeProvider = { now })
        advanceUntilIdle()
        viewModel.onInputChanged("first")
        viewModel.sendMessage()
        advanceUntilIdle()
        now += 1_000L
        viewModel.onInputChanged("second")
        viewModel.sendMessage()
        advanceUntilIdle()

        val firstUserId = viewModel.uiState.value.messages.first().id
        val firstUndo = viewModel.deleteMessage(firstUserId)
        assertNotNull(firstUndo)
        val secondUserId = viewModel.uiState.value.messages.first().id
        val secondUndo = viewModel.deleteMessage(secondUserId)
        assertNotNull(secondUndo)

        assertFalse(viewModel.undoLastMessageMutation(firstUndo!!.token))
        assertEquals(emptyList<String>(), viewModel.uiState.value.messages.map { it.content })

        assertTrue(viewModel.undoLastMessageMutation(secondUndo!!.token))
        assertEquals(
            listOf("second", "reply:second"),
            viewModel.uiState.value.messages.map { it.content },
        )
    }

    @Test
    fun toolProgressAndLegacyToolEvents_shareOneToolCallRow() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.TaskProgress(
                    label = "观察屏幕",
                    status = "running",
                    phase = "observe",
                    toolName = "observe",
                    progressKey = "observe-step",
                    currentStep = 1,
                    totalSteps = 2,
                )
            )
            onEvent(ChatStreamEvent.ToolCallCompleted(label = "观察屏幕", result = "ok"))
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("tool")
        viewModel.sendMessage()
        advanceUntilIdle()

        val toolCalls = viewModel.uiState.value.messages.last().toolCalls.orEmpty()
        assertEquals(1, toolCalls.size)
        assertEquals("观察屏幕", toolCalls.single().label)
        assertEquals("ok", toolCalls.single().result)
        assertEquals(1, toolCalls.single().currentStep)
        assertEquals(2, toolCalls.single().totalSteps)
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
        var createThreadCalls: Int = 0
            private set
        var streamReplyCalls: Int = 0
            private set

        override suspend fun createThread(titleHint: String?): RemoteChatThread {
            createThreadCalls += 1
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
            streamReplyCalls += 1
            script(threadId, prompt, onEvent)
        }
    }
}
