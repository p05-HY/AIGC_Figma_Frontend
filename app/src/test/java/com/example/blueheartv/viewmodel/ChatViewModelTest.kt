package com.example.blueheartv.viewmodel

import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.chat.ChatPrompt
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.chat.MobileRunCancellation
import com.example.blueheartv.chat.MobileRunStatus
import com.example.blueheartv.chat.RemoteChatThread
import com.example.blueheartv.chat.stream.StreamLifecycleState
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.model.ToolCallStatus
import com.example.blueheartv.model.TraceEvent
import com.example.blueheartv.model.TraceRunStatus
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus
import com.example.blueheartv.test.MainDispatcherRule
import com.example.blueheartv.voice.InputMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
    fun sendVoiceText_returnsToTextModeForReviewWithoutSendingMessage() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("should not run"))
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()

        viewModel.toggleInputMode()
        assertEquals(InputMode.VOICE, viewModel.uiState.value.inputMode)
        viewModel.sendVoiceText("打开微信")
        advanceUntilIdle()

        assertEquals("打开微信", viewModel.uiState.value.inputText)
        assertEquals(InputMode.TEXT, viewModel.uiState.value.inputMode)
        assertEquals(emptyList<Message>(), viewModel.uiState.value.messages)
        assertEquals(0, provider.createThreadCalls)
        assertEquals(0, provider.streamReplyCalls)
    }

    @Test
    fun setInputMode_setsTheRequestedModeWithoutToggling() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setInputMode(InputMode.VOICE)
        viewModel.setInputMode(InputMode.VOICE)

        assertEquals(InputMode.VOICE, viewModel.uiState.value.inputMode)

        viewModel.setInputMode(InputMode.TEXT)

        assertEquals(InputMode.TEXT, viewModel.uiState.value.inputMode)
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
            onEvent(
                ChatStreamEvent.TaskProgress(
                    label = "观察屏幕",
                    status = "completed",
                    phase = "observe",
                    toolName = "observe",
                    progressKey = "observe-step",
                    currentStep = 2,
                    totalSteps = 2,
                ),
            )
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
        assertEquals(ToolCallStatus.COMPLETED, toolCalls.single().status)
        assertEquals(2, toolCalls.single().currentStep)
        assertEquals(2, toolCalls.single().totalSteps)
    }

    @Test
    fun taskProgress_updatesSharedUiStateForFloatingWindow() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.TaskProgress(
                    label = "会议通知",
                    taskTitle = "为会议通知创建提醒",
                    status = "waiting_confirmation",
                    phase = "confirmation",
                    stepTitle = "等待确认是否创建会议提醒",
                    message = "检测到会议通知，是否创建提醒？",
                    toolName = "needs_confirmation",
                    progressKey = "scenario3-demo",
                    currentStep = 2,
                    totalSteps = 3,
                    requiresConfirmation = true,
                    confirmationId = "confirm-123",
                    canCancel = true,
                    canTakeOver = true,
                )
            )
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()

        viewModel.onInputChanged("模拟收到一条会议通知")
        viewModel.sendMessage()
        advanceUntilIdle()

        val taskProgress = viewModel.uiState.value.taskProgress
        assertNotNull(taskProgress)
        assertEquals("为会议通知创建提醒", taskProgress!!.taskTitle)
        assertEquals("waiting_confirmation", taskProgress.status)
        assertEquals(2, taskProgress.currentStep)
        assertEquals(3, taskProgress.totalSteps)
        assertEquals("等待确认是否创建会议提醒", taskProgress.stepTitle)
        assertEquals("needs_confirmation", taskProgress.toolName)
        assertTrue(taskProgress.requiresConfirmation)
        assertEquals("confirm-123", taskProgress.confirmationId)
    }

    @Test
    fun taskProgress_keepsFirstScenarioStepVisibleBeforeShowingWaitingConfirmation() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.TaskProgress(
                    label = "会议通知",
                    taskTitle = "为会议通知创建提醒",
                    status = "running",
                    phase = "phone_tool",
                    stepTitle = "检测到会议通知",
                    message = "检测到一条会议通知",
                    toolName = "list_notifications",
                    progressKey = "scenario3-demo",
                    currentStep = 1,
                    totalSteps = 3,
                    canCancel = true,
                    canTakeOver = true,
                ),
            )
            onEvent(
                ChatStreamEvent.TaskProgress(
                    label = "会议通知",
                    taskTitle = "为会议通知创建提醒",
                    status = "waiting_confirmation",
                    phase = "confirmation",
                    stepTitle = "等待确认是否创建会议提醒",
                    message = "检测到会议通知，是否创建提醒？",
                    toolName = "needs_confirmation",
                    progressKey = "scenario3-demo",
                    currentStep = 2,
                    totalSteps = 3,
                    requiresConfirmation = true,
                    confirmationId = "confirm-123",
                    canCancel = true,
                    canTakeOver = true,
                ),
            )
            awaitCancellation()
        }
        val viewModel = createViewModel(
            chatProvider = provider,
            timeProvider = { 1_000L + testScheduler.currentTime },
        )
        advanceUntilIdle()

        viewModel.onInputChanged("模拟收到一条会议通知")
        viewModel.sendMessage()
        runCurrent()

        assertEquals(1, viewModel.uiState.value.taskProgress?.currentStep)
        assertEquals("检测到会议通知", viewModel.uiState.value.taskProgress?.stepTitle)

        advanceTimeBy(999)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.taskProgress?.currentStep)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, viewModel.uiState.value.taskProgress?.currentStep)
        assertEquals("waiting_confirmation", viewModel.uiState.value.taskProgress?.status)

        viewModel.cancelActiveRun()
        advanceUntilIdle()
    }

    @Test
    fun cancelTaskProgress_withWaitingConfirmationUsesConfirmationCancelPath() = runTest {
        val provider = ScriptedProvider(
            rejectEvents = listOf(
                ChatStreamEvent.TaskProgress(
                    label = "会议通知",
                    taskTitle = "为会议通知创建提醒",
                    status = "cancelled",
                    phase = "finalizing",
                    stepTitle = "已取消创建会议提醒",
                    message = "已取消创建会议提醒",
                    toolName = "needs_confirmation",
                    currentStep = 2,
                    totalSteps = 3,
                    canCancel = false,
                    canTakeOver = false,
                ),
            ),
        ) { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.TaskProgress(
                    label = "会议通知",
                    taskTitle = "为会议通知创建提醒",
                    status = "waiting_confirmation",
                    phase = "confirmation",
                    stepTitle = "等待确认是否创建会议提醒",
                    message = "检测到会议通知，是否创建提醒？",
                    toolName = "needs_confirmation",
                    currentStep = 2,
                    totalSteps = 3,
                    requiresConfirmation = true,
                    confirmationId = "confirm-123",
                    canCancel = true,
                    canTakeOver = true,
                ),
            )
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()

        viewModel.onInputChanged("模拟收到一条会议通知")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.cancelTaskProgress()
        advanceUntilIdle()

        assertEquals(listOf("confirm-123"), provider.rejectedConfirmationIds)
        assertEquals("cancelled", viewModel.uiState.value.taskProgress?.status)
        assertEquals("已取消创建会议提醒", viewModel.uiState.value.taskProgress?.stepTitle)
    }

    @Test
    fun takeOverTaskProgress_withoutConfirmationCancelsRunWithTakeOverSource() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.TaskProgress(
                    label = "长任务",
                    taskTitle = "执行长任务",
                    status = "running",
                    phase = "phone_tool",
                    stepTitle = "正在执行长任务",
                    message = "正在执行长任务",
                    toolName = "observe",
                    currentStep = 1,
                    totalSteps = 3,
                    canCancel = true,
                    canTakeOver = true,
                ),
            )
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()

        viewModel.onInputChanged("执行长任务")
        viewModel.sendMessage()
        runCurrent()

        viewModel.takeOverTaskProgress()
        advanceUntilIdle()

        assertEquals("take_over", provider.cancelledRuns.single().third)
        assertEquals("taken_over", viewModel.uiState.value.taskProgress?.status)
        assertEquals("已停止自动执行，请手动接管", viewModel.uiState.value.taskProgress?.message)
    }

    @Test
    fun streamStarted_initializesTraceCardImmediately() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.StreamStarted(
                    runId = "run-1",
                    streamSeq = 1,
                    message = "已接收请求，正在连接 Agent。",
                ),
            )
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()

        viewModel.onInputChanged("打开微信")
        viewModel.sendMessage()
        runCurrent()

        val uiState = viewModel.uiState.value
        val assistant = uiState.messages.last()
        assertEquals("已接收请求，正在连接 Agent。", uiState.streamingStep)
        assertEquals("", assistant.content)
        assertEquals("run-1", assistant.trace?.runId)
        assertEquals("已接收请求，正在连接 Agent。", assistant.trace?.summary)
        assertNull(assistant.toolCalls)
        assertEquals(MessageDeliveryState.STREAMING, assistant.deliveryState)

        viewModel.cancelActiveRun()
        advanceUntilIdle()
    }

    @Test
    fun traceEvent_replacesLegacyToolCard_andTerminalControlsCompletion() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.TaskProgress(
                    label = "观察屏幕",
                    status = "running",
                    phase = "phone_tool",
                ),
            )
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.StepUpsert(
                        runId = "run-1",
                        eventId = "evt-1",
                        seq = 1,
                        step = TraceStep(
                            id = "tool-1",
                            kind = "tool",
                            title = "观察屏幕",
                            summary = "正在读取当前手机屏幕状态。",
                            status = TraceStepStatus.RUNNING,
                        ),
                    ),
                ),
            )
            onEvent(ChatStreamEvent.TextDelta("完成"))
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunTerminal(
                        runId = "run-1",
                        eventId = "evt-2",
                        seq = 2,
                        status = TraceRunStatus.SUCCEEDED,
                    ),
                ),
            )
            onEvent(ChatStreamEvent.StreamEof(streamSeq = 5))
        }

        val viewModel = createViewModel(chatProvider = provider, traceRenderEnabled = true)
        advanceUntilIdle()
        viewModel.onInputChanged("帮我看看屏幕")
        viewModel.sendMessage()
        advanceUntilIdle()

        val assistant = viewModel.uiState.value.messages.last()
        assertEquals("完成", assistant.content)
        assertEquals(TraceRunStatus.SUCCEEDED, assistant.trace?.runStatus)
        assertNull(assistant.toolCalls)
        assertEquals(MessageDeliveryState.COMPLETED, assistant.deliveryState)
        assertFalse(viewModel.uiState.value.canCancel)
    }

    @Test
    fun interleavedTextDeltaAndTrace_updateSameAssistantMessageInRealTime() = runTest {
        lateinit var push: (ChatStreamEvent) -> Unit
        var activeThreadId = ""
        val provider = ScriptedProvider { threadId, _, onEvent ->
            activeThreadId = threadId
            push = onEvent
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider, traceRenderEnabled = true)
        advanceUntilIdle()

        viewModel.onInputChanged("查北京天气")
        viewModel.sendMessage()
        runCurrent()

        push(
            ChatStreamEvent.StreamStarted(
                runId = "run-1",
                threadId = activeThreadId,
                message = "已接收请求，正在连接 Agent。",
                streamSeq = 1,
            ),
        )
        runCurrent()
        var assistant = viewModel.uiState.value.messages.last()
        assertEquals("", assistant.content)
        assertEquals("run-1", assistant.trace?.runId)

        push(ChatStreamEvent.TextDelta("北京"))
        runCurrent()
        assistant = viewModel.uiState.value.messages.last()
        assertEquals("北京", assistant.content)
        assertEquals("run-1", assistant.trace?.runId)

        push(
            ChatStreamEvent.Trace(
                TraceEvent.StepUpsert(
                    runId = "run-1",
                    eventId = "evt-1",
                    seq = 1,
                    step = TraceStep(
                        id = "weather-1",
                        kind = "weather_query",
                        title = "weather_query",
                        summary = "正在查询北京天气",
                        status = TraceStepStatus.RUNNING,
                    ),
                ),
                streamSeq = 3,
            ),
        )
        runCurrent()
        assistant = viewModel.uiState.value.messages.last()
        assertEquals("北京", assistant.content)
        assertEquals("weather-1", assistant.trace?.steps?.single()?.id)

        push(ChatStreamEvent.TextDelta("天气晴。"))
        runCurrent()
        assistant = viewModel.uiState.value.messages.last()
        assertEquals("北京天气晴。", assistant.content)
        assertEquals("weather-1", assistant.trace?.steps?.single()?.id)

        viewModel.cancelActiveRun()
        advanceUntilIdle()
    }

    @Test
    fun duplicateAndOutOfOrderStreamSeqEvents_doNotAppendTextTwice() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("A", streamSeq = 2))
            onEvent(ChatStreamEvent.TextDelta("A", streamSeq = 2))
            onEvent(ChatStreamEvent.TextDelta("B", streamSeq = 1))
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunTerminal(
                        runId = "run-1",
                        eventId = "evt-1",
                        seq = 1,
                        status = TraceRunStatus.SUCCEEDED,
                    ),
                    streamSeq = 3,
                ),
            )
            onEvent(ChatStreamEvent.StreamEof(streamSeq = 4))
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("测试去重")
        viewModel.sendMessage()
        advanceUntilIdle()

        val assistant = viewModel.uiState.value.messages.last()
        assertEquals("A", assistant.content)
        assertEquals(3L, assistant.lastReceivedStreamSeq)
        assertEquals("succeeded", assistant.terminalStatus)
        assertEquals(MessageDeliveryState.COMPLETED, assistant.deliveryState)
    }

    @Test
    fun textDeltaThenSucceededTerminalAndEof_finishesDoneWithoutErrorBanner() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("ok", streamSeq = 2))
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunTerminal(
                        runId = "run-1",
                        eventId = "evt-1",
                        seq = 1,
                        status = TraceRunStatus.SUCCEEDED,
                    ),
                    streamSeq = 3,
                ),
            )
            onEvent(ChatStreamEvent.StreamEof(streamSeq = 4))
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("回复我 ok")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val assistant = uiState.messages.last()
        assertEquals("ok", assistant.content)
        assertEquals(MessageDeliveryState.COMPLETED, assistant.deliveryState)
        assertEquals(ChatSessionState.IDLE, uiState.sessionState)
        assertEquals(StreamLifecycleState.DONE, uiState.streamLifecycleState)
        assertEquals("succeeded", assistant.terminalStatus)
        assertNull(assistant.errorMessage)
        assertNull(uiState.lastError)
        assertFalse(uiState.canRetry)
    }

    @Test
    fun succeededTerminalWithDuplicateStreamSeqStillFinishesDone() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.StreamStarted(
                    runId = "run-1",
                    threadId = "thread-1",
                    message = "已接收请求，正在连接 Agent。",
                    streamSeq = 1,
                ),
            )
            onEvent(ChatStreamEvent.TextDelta("ok", streamSeq = 2))
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunTerminal(
                        runId = "run-1",
                        eventId = "evt-terminal",
                        seq = 1,
                        status = TraceRunStatus.SUCCEEDED,
                    ),
                    streamSeq = 2,
                ),
            )
            onEvent(ChatStreamEvent.StreamEof(streamSeq = 3))
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("回复我 ok")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val assistant = uiState.messages.last()
        assertEquals("ok", assistant.content)
        assertEquals(MessageDeliveryState.COMPLETED, assistant.deliveryState)
        assertEquals(ChatSessionState.IDLE, uiState.sessionState)
        assertEquals(StreamLifecycleState.DONE, uiState.streamLifecycleState)
        assertEquals("succeeded", assistant.terminalStatus)
        assertNull(uiState.lastError)
        assertFalse(uiState.canRetry)
    }

    @Test
    fun succeededTerminalWithLowerTraceSeqThanStepStillFinishesDone() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.StreamStarted(
                    runId = "run-1",
                    threadId = "thread-1",
                    message = "已接收请求，正在连接 Agent。",
                    streamSeq = 1,
                ),
            )
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.StepUpsert(
                        runId = "run-1",
                        eventId = "evt-step",
                        seq = 5,
                        step = TraceStep(
                            id = "tool-1",
                            kind = "tool",
                            title = "执行操作",
                            summary = "正在执行操作。",
                            status = TraceStepStatus.RUNNING,
                        ),
                    ),
                    streamSeq = 2,
                ),
            )
            onEvent(ChatStreamEvent.TextDelta("ok", streamSeq = 3))
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunTerminal(
                        runId = "run-1",
                        eventId = "evt-terminal",
                        seq = 4,
                        status = TraceRunStatus.SUCCEEDED,
                    ),
                    streamSeq = 4,
                ),
            )
            onEvent(ChatStreamEvent.StreamEof(streamSeq = 5))
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("回复我 ok")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val assistant = uiState.messages.last()
        assertEquals("ok", assistant.content)
        assertEquals(MessageDeliveryState.COMPLETED, assistant.deliveryState)
        assertEquals(ChatSessionState.IDLE, uiState.sessionState)
        assertEquals(StreamLifecycleState.DONE, uiState.streamLifecycleState)
        assertEquals(TraceRunStatus.SUCCEEDED, assistant.trace?.runStatus)
        assertEquals("succeeded", assistant.terminalStatus)
        assertNull(uiState.lastError)
    }

    @Test
    fun succeededTerminalThenProviderThrows_finishesDoneWithoutCancel() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("ok", streamSeq = 1))
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunTerminal(
                        runId = "run-1",
                        eventId = "evt-terminal",
                        seq = 1,
                        status = TraceRunStatus.SUCCEEDED,
                    ),
                    streamSeq = 2,
                ),
            )
            throw RuntimeException("socket closed after terminal")
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("回复我 ok")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val assistant = uiState.messages.last()
        assertEquals("ok", assistant.content)
        assertEquals(MessageDeliveryState.COMPLETED, assistant.deliveryState)
        assertEquals(ChatSessionState.IDLE, uiState.sessionState)
        assertEquals(StreamLifecycleState.DONE, uiState.streamLifecycleState)
        assertEquals("succeeded", assistant.terminalStatus)
        assertNull(uiState.lastError)
        assertFalse(uiState.canRetry)
        assertTrue(provider.cancelledRuns.isEmpty())
    }

    @Test
    fun succeededTerminalWithoutEof_finishesDoneWithoutInterruptedTimeout() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("ok", streamSeq = 1))
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunTerminal(
                        runId = "run-1",
                        eventId = "evt-terminal",
                        seq = 1,
                        status = TraceRunStatus.SUCCEEDED,
                    ),
                    streamSeq = 2,
                ),
            )
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("回复我 ok")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val assistant = uiState.messages.last()
        assertEquals("ok", assistant.content)
        assertEquals(MessageDeliveryState.COMPLETED, assistant.deliveryState)
        assertEquals(ChatSessionState.IDLE, uiState.sessionState)
        assertEquals(StreamLifecycleState.DONE, uiState.streamLifecycleState)
        assertEquals("succeeded", assistant.terminalStatus)
        assertNull(uiState.lastError)
        assertFalse(uiState.canRetry)
        assertTrue(provider.cancelledRuns.isEmpty())
    }

    @Test
    fun traceFromDifferentRunAfterBinding_isIgnored() = runTest {
        lateinit var push: (ChatStreamEvent) -> Unit
        var activeThreadId = ""
        val provider = ScriptedProvider { threadId, _, onEvent ->
            activeThreadId = threadId
            push = onEvent
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider, traceRenderEnabled = true)
        advanceUntilIdle()
        viewModel.onInputChanged("测试旧 run 隔离")
        viewModel.sendMessage()
        runCurrent()

        push(
            ChatStreamEvent.StreamStarted(
                runId = "run-1",
                threadId = activeThreadId,
                message = "已接收请求，正在连接 Agent。",
                streamSeq = 1,
            ),
        )
        push(
            ChatStreamEvent.Trace(
                TraceEvent.StepUpsert(
                    runId = "run-stale",
                    eventId = "evt-stale",
                    seq = 1,
                    step = TraceStep(
                        id = "tool-stale",
                        kind = "tool",
                        title = "旧任务",
                        summary = "旧任务不应污染当前消息。",
                        status = TraceStepStatus.RUNNING,
                    ),
                ),
                streamSeq = 2,
            ),
        )
        runCurrent()

        val assistant = viewModel.uiState.value.messages.last()
        assertEquals("run-1", assistant.trace?.runId)
        assertTrue(assistant.trace?.steps.orEmpty().isEmpty())

        viewModel.cancelActiveRun()
        advanceUntilIdle()
    }

    @Test
    fun traceEvent_keepsLegacyToolCardWhenRenderDisabled() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.TaskProgress(
                    label = "观察屏幕",
                    status = "running",
                    phase = "phone_tool",
                ),
            )
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.StepUpsert(
                        runId = "run-1",
                        eventId = "evt-1",
                        seq = 1,
                        step = TraceStep(
                            id = "tool-1",
                            kind = "tool",
                            title = "观察屏幕",
                            summary = "正在读取当前手机屏幕状态。",
                            status = TraceStepStatus.RUNNING,
                        ),
                    ),
                ),
            )
            onEvent(ChatStreamEvent.TextDelta("完成"))
            onEvent(ChatStreamEvent.Completed)
        }

        val viewModel = createViewModel(chatProvider = provider, traceRenderEnabled = false)
        advanceUntilIdle()
        viewModel.onInputChanged("帮我看看屏幕")
        viewModel.sendMessage()
        advanceUntilIdle()

        val assistant = viewModel.uiState.value.messages.last()
        assertEquals("完成", assistant.content)
        assertNotNull(assistant.trace)
        assertNotNull(assistant.toolCalls)
        assertEquals("观察屏幕", assistant.toolCalls!!.single().label)
        assertEquals(MessageDeliveryState.COMPLETED, assistant.deliveryState)
    }

    @Test
    fun plainTextStreamEofWithoutTrace_marksMessageFailedEvenWhenTextArrived() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(ChatStreamEvent.TextDelta("纯文本回复"))
            onEvent(ChatStreamEvent.StreamEof(streamSeq = 2))
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("简单问题")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val assistant = uiState.messages.last()
        assertEquals("纯文本回复", assistant.content)
        assertEquals(MessageDeliveryState.FAILED, assistant.deliveryState)
        assertEquals(ChatSessionState.ERROR, uiState.sessionState)
        assertEquals(StreamLifecycleState.INTERRUPTED, uiState.streamLifecycleState)
        assertEquals(2L, assistant.lastReceivedStreamSeq)
        assertEquals("interrupted", assistant.terminalStatus)
        assertTrue(uiState.canRetry)
        assertEquals("连接中断，当前回复未确认完成。请重试。", assistant.errorMessage)
        assertNull(assistant.trace)
    }

    @Test
    fun streamEofWithoutTraceTerminal_marksRunInterrupted() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.StepUpsert(
                        runId = "run-1",
                        eventId = "evt-1",
                        seq = 1,
                        step = TraceStep(
                            id = "tool-1",
                            kind = "tool",
                            title = "观察屏幕",
                            summary = "正在读取当前手机屏幕状态。",
                            status = TraceStepStatus.RUNNING,
                        ),
                    ),
                ),
            )
            onEvent(ChatStreamEvent.StreamEof(streamSeq = 2))
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("帮我看看屏幕")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val assistant = uiState.messages.last()
        assertEquals(TraceRunStatus.INTERRUPTED, assistant.trace?.runStatus)
        assertEquals(MessageDeliveryState.FAILED, assistant.deliveryState)
        assertEquals(ChatSessionState.ERROR, uiState.sessionState)
        assertTrue(uiState.canRetry)
        assertTrue(provider.cancelledRuns.isEmpty())
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
        assertTrue(provider.cancelledRuns.isEmpty())
    }

    @Test
    fun streamErrorAfterTerminal_usesTerminalStateWithoutRequestingAnotherCancel() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunStarted("run-1", "evt-1", 1, "thread-1"),
                ),
            )
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunTerminal(
                        runId = "run-1",
                        eventId = "evt-2",
                        seq = 2,
                        status = TraceRunStatus.FAILED,
                        reason = "upstream_ended_without_terminal",
                    ),
                ),
            )
            onEvent(
                ChatStreamEvent.Error(
                    "任务未返回结束状态，已停止后续操作。",
                    retryable = true,
                    terminalStatus = "failed",
                    terminalReason = "upstream_ended_without_terminal",
                ),
            )
        }

        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("帮我看看屏幕")
        viewModel.sendMessage()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val assistant = uiState.messages.last()
        assertEquals(ChatSessionState.ERROR, uiState.sessionState)
        assertEquals("任务未返回结束状态，已停止后续操作。", uiState.lastError)
        assertEquals(TraceRunStatus.FAILED, assistant.trace?.runStatus)
        assertTrue(provider.cancelledRuns.isEmpty())
    }

    @Test
    fun cancelActiveRun_stopsLocalStreamAndCancelsTheSameServerRun() = runTest {
        val provider = ScriptedProvider { threadId, _, onEvent ->
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunStarted(
                        runId = "run-1",
                        eventId = "evt-1",
                        seq = 1,
                        threadId = threadId,
                    ),
                ),
            )
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()

        viewModel.onInputChanged("打开微信")
        viewModel.sendMessage()
        runCurrent()
        assertTrue(viewModel.uiState.value.canCancel)

        viewModel.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(1, provider.cancelledRuns.size)
        assertEquals("run-1", provider.cancelledRuns.single().second)
        assertEquals("user", provider.cancelledRuns.single().third)
        assertFalse(viewModel.uiState.value.canCancel)
        assertEquals(ChatSessionState.CANCELLED, viewModel.uiState.value.sessionState)
        assertEquals("已停止当前任务。", viewModel.uiState.value.messages.last().content)
    }

    @Test
    fun cancelActiveRun_withBackendStillRunningResponseDoesNotPollIntoCancelled() = runTest {
        val provider = ScriptedProvider(
            cancellationStatus = "backend_still_running",
            cancellationBackendStatus = "running",
            polledBackendStatus = "cancelled",
            polledStatusTerminal = true,
        ) { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunStarted("run-1", "evt-1", 1, "thread-1"),
                ),
            )
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("打开飞书")
        viewModel.sendMessage()
        runCurrent()

        viewModel.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(ChatSessionState.BACKEND_STILL_RUNNING, viewModel.uiState.value.sessionState)
        assertEquals(MessageDeliveryState.FAILED, viewModel.uiState.value.messages.last().deliveryState)
        assertTrue(viewModel.uiState.value.canCancel)
        assertEquals(0, provider.statusPollCalls)
    }

    @Test
    fun cancelActiveRun_withLocalFencedOnlyBlocksNextPromptOnSameThread() = runTest {
        val provider = ScriptedProvider(
            cancellationStatus = "local_fenced_only",
            cancellationBackendStatus = "not_started",
            cancellationDeviceStatus = "not_bound",
            polledBackendStatus = "running",
            polledStatusTerminal = false,
        ) { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunStarted("run-1", "evt-1", 1, "thread-1"),
                ),
            )
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("查天气")
        viewModel.sendMessage()
        runCurrent()

        viewModel.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(ChatSessionState.BACKEND_STILL_RUNNING, viewModel.uiState.value.sessionState)
        assertEquals(StreamLifecycleState.CANCELED_WITH_UNCONFIRMED_BACKEND, viewModel.uiState.value.streamLifecycleState)
        assertTrue(viewModel.uiState.value.canCancel)
        viewModel.onInputChanged("回复我 OK")
        viewModel.sendMessage()
        runCurrent()

        assertEquals(1, provider.streamReplyCalls)
    }

    @Test
    fun longTaskStaysAliveBeyondOldFrontendIdleTimeout() = runTest {
        var now = 1_000L
        lateinit var push: (ChatStreamEvent) -> Unit
        val provider = ScriptedProvider { _, _, onEvent ->
            push = onEvent
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunStarted("run-1", "evt-1", 1, "thread-1"),
                ),
            )
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider, timeProvider = { now })
        advanceUntilIdle()
        viewModel.onInputChanged("执行长任务")
        viewModel.sendMessage()
        runCurrent()

        repeat(3) { index ->
            advanceTimeBy(60_000L)
            now += 60_000L
            push(ChatStreamEvent.Heartbeat(runId = "run-1", streamSeq = (index + 2).toLong()))
            runCurrent()
            assertEquals(ChatSessionState.RESPONDING, viewModel.uiState.value.sessionState)
        }

        assertTrue(provider.cancelledRuns.isEmpty())
        assertEquals(ChatSessionState.RESPONDING, viewModel.uiState.value.sessionState)

        viewModel.cancelActiveRun()
        advanceTimeBy(10_500)
        runCurrent()
    }

    @Test
    fun heartbeatUpdatesStreamingLivenessWithoutChangingAssistantText() = runTest {
        lateinit var push: (ChatStreamEvent) -> Unit
        val provider = ScriptedProvider { _, _, onEvent ->
            push = onEvent
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("执行长任务")
        viewModel.sendMessage()
        runCurrent()

        push(ChatStreamEvent.Heartbeat(runId = "run-1", streamSeq = 1))
        runCurrent()

        val assistant = viewModel.uiState.value.messages.last()
        assertEquals("", assistant.content)
        assertEquals(MessageDeliveryState.SENDING, assistant.deliveryState)
        assertEquals(ChatSessionState.RESPONDING, viewModel.uiState.value.sessionState)
        assertEquals("仍在处理...", viewModel.uiState.value.streamingStep)

        viewModel.cancelActiveRun()
        advanceUntilIdle()
    }

    @Test
    fun heartbeatTimeoutWithoutFurtherEvents_marksRunInterrupted() = runTest {
        lateinit var push: (ChatStreamEvent) -> Unit
        val provider = ScriptedProvider { _, _, onEvent ->
            push = onEvent
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("执行长任务")
        viewModel.sendMessage()
        runCurrent()

        push(ChatStreamEvent.StreamStarted(runId = "run-1", message = "已接收请求，正在连接 Agent。", streamSeq = 1))
        push(ChatStreamEvent.Heartbeat(runId = "run-1", streamSeq = 2))
        runCurrent()

        assertEquals(StreamLifecycleState.STREAMING, viewModel.uiState.value.streamLifecycleState)

        advanceTimeBy(90_001L)
        runCurrent()

        val assistant = viewModel.uiState.value.messages.last()
        assertEquals(ChatSessionState.ERROR, viewModel.uiState.value.sessionState)
        assertEquals(StreamLifecycleState.INTERRUPTED, viewModel.uiState.value.streamLifecycleState)
        assertEquals(MessageDeliveryState.FAILED, assistant.deliveryState)
        assertEquals("interrupted", assistant.terminalStatus)
        assertEquals(2L, assistant.lastReceivedStreamSeq)
        assertTrue(viewModel.uiState.value.canRetry)
    }

    @Test
    fun unconfirmedCancellation_blocksTheNextPromptAndKeepsStopAvailable() = runTest {
        val provider = ScriptedProvider(
            cancellationStatus = "backend_still_running",
            cancellationBackendStatus = "running",
            polledBackendStatus = "running",
            polledStatusTerminal = false,
        ) { threadId, _, onEvent ->
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunStarted("run-1", "evt-1", 1, threadId),
                ),
            )
            onEvent(ChatStreamEvent.Error("stream failed", retryable = true))
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("打开飞书")
        viewModel.sendMessage()
        runCurrent()

        viewModel.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(ChatSessionState.BACKEND_STILL_RUNNING, viewModel.uiState.value.sessionState)
        assertTrue(viewModel.uiState.value.canCancel)
        viewModel.onInputChanged("回复我 OK")
        viewModel.sendMessage()
        runCurrent()
        assertEquals(1, provider.streamReplyCalls)

        provider.polledBackendStatus = "cancelled"
        provider.polledStatusTerminal = true
        viewModel.cancelActiveRun()
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun appBackground_doesNotCancelActiveRun_whenAppGoesToBackground() = runTest {
        val provider = ScriptedProvider { _, _, onEvent ->
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunStarted("run-1", "evt-1", 1, "thread-1"),
                ),
            )
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("打开飞书")
        viewModel.sendMessage()
        runCurrent()

        viewModel.onAppBackgrounded()
        runCurrent()

        // ✅ 后台不应自动取消正在执行的任务（打开外部 App 是正常业务路径）
        assertTrue(provider.cancelledRuns.isEmpty())
        assertEquals(ChatSessionState.RESPONDING, viewModel.uiState.value.sessionState)

        viewModel.cancelActiveRun()
        advanceTimeBy(10_500)
        runCurrent()
    }

    @Test
    fun deletingTheActiveSession_cancelsItsRunBeforeDeletingTheRemoteThread() = runTest {
        var activeThreadId: String? = null
        val provider = ScriptedProvider { threadId, _, onEvent ->
            activeThreadId = threadId
            onEvent(
                ChatStreamEvent.Trace(
                    TraceEvent.RunStarted("run-1", "evt-1", 1, threadId),
                ),
            )
            awaitCancellation()
        }
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.onInputChanged("打开飞书")
        viewModel.sendMessage()
        runCurrent()

        viewModel.deleteSession(requireNotNull(activeThreadId))
        advanceUntilIdle()

        assertEquals(listOf("run-1"), provider.cancelledRuns.map { it.second })
        assertEquals(listOf(requireNotNull(activeThreadId)), provider.deletedThreads)
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
    fun manyStreamingDeltas_updateContentWithoutPersistingEveryChunk() = runTest {
        val dao = FakeChatDao()
        val store = ChatSessionStore(dao)
        val provider = ScriptedProvider { _, _, onEvent ->
            repeat(100) { index ->
                onEvent(ChatStreamEvent.TextDelta("$index,", invocationId = "model-final"))
            }
            onEvent(ChatStreamEvent.Completed)
        }
        val viewModel = createViewModel(
            chatProvider = provider,
            store = store,
            persistScope = this,
        )
        advanceUntilIdle()

        viewModel.onInputChanged("stream")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals((0 until 100).joinToString(separator = "") { "$it," }, viewModel.uiState.value.messages.last().content)
        assertTrue(
            "streaming chunks must not trigger one Room replace per delta",
            dao.replaceMessagesCalls < 10,
        )
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

    @Test
    fun selectHistory_usesCachedThreadBeforeSlowRemoteRefreshReturns() = runTest {
        val provider = ScriptedProvider(
            remoteThreads = listOf(
                RemoteChatThread(
                    id = "thread-1",
                    title = "旧对话",
                    updatedAtMillis = 2000L,
                    messages = listOf(
                        Message("m1", "旧问题", isUser = true),
                        Message("m2", "旧回答", isUser = false),
                    ),
                ),
            ),
            loadThreadDelayMs = 1_000L,
        )
        val viewModel = createViewModel(chatProvider = provider)
        advanceUntilIdle()
        viewModel.startNewConversation()
        assertEquals(emptyList<Message>(), viewModel.uiState.value.messages)

        viewModel.selectHistory("thread-1")
        runCurrent()

        assertEquals(listOf("旧问题", "旧回答"), viewModel.uiState.value.messages.map { it.content })
        assertFalse(viewModel.uiState.value.isDrawerOpen)
    }

    private fun createViewModel(
        chatProvider: ChatProvider = ScriptedProvider(),
        timeProvider: () -> Long = { System.currentTimeMillis() },
        traceRenderEnabled: Boolean = false,
        store: ChatSessionStore? = null,
        persistScope: CoroutineScope? = null,
    ): ChatViewModel {
        val repo = ChatSessionRepository(
            timeProvider = timeProvider,
            idProvider = { "id-${System.nanoTime()}" },
            store = store,
            persistScope = persistScope,
        )
        return ChatViewModel(
            chatProvider = chatProvider,
            repo = repo,
            traceRenderEnabled = traceRenderEnabled,
        )
    }

    private class ScriptedProvider(
        private val remoteThreads: List<RemoteChatThread> = emptyList(),
        private val cancellationAccepted: Boolean = true,
        private val cancellationStatus: String = "canceled_confirmed",
        private val cancellationBackendStatus: String = "cancelled",
        private val cancellationDeviceStatus: String = "canceled",
        private val cancellationLocalFenced: Boolean = true,
        private val cancellationRetryable: Boolean = false,
        polledBackendStatus: String = "cancelled",
        polledStatusTerminal: Boolean = true,
        private val loadThreadDelayMs: Long = 0L,
        private val confirmEvents: List<ChatStreamEvent> = emptyList(),
        private val rejectEvents: List<ChatStreamEvent> = emptyList(),
        private val takeOverEvents: List<ChatStreamEvent> = emptyList(),
        private val script: suspend (String, ChatPrompt, (ChatStreamEvent) -> Unit) -> Unit = { _, _, onEvent ->
            onEvent(ChatStreamEvent.Completed)
        },
    ) : ChatProvider {
        var createThreadCalls: Int = 0
            private set
        var streamReplyCalls: Int = 0
            private set
        val cancelledRuns = mutableListOf<Triple<String, String, String>>()
        val deletedThreads = mutableListOf<String>()
        val confirmedConfirmationIds = mutableListOf<String>()
        val rejectedConfirmationIds = mutableListOf<String>()
        val takenOverConfirmationIds = mutableListOf<String>()
        var polledBackendStatus: String = polledBackendStatus
        var polledStatusTerminal: Boolean = polledStatusTerminal
        var statusPollCalls: Int = 0
            private set

        override suspend fun createThread(titleHint: String?): RemoteChatThread {
            createThreadCalls += 1
            return RemoteChatThread("thread-${System.nanoTime()}", titleHint ?: "当前对话", 0L, emptyList())
        }

        override suspend fun loadThreads(limit: Int): List<RemoteChatThread> = remoteThreads

        override suspend fun loadThread(threadId: String): RemoteChatThread? {
            if (loadThreadDelayMs > 0L) delay(loadThreadDelayMs)
            return remoteThreads.firstOrNull { it.id == threadId }
        }

        override suspend fun renameThread(threadId: String, title: String) = Unit

        override suspend fun deleteThread(threadId: String) {
            deletedThreads += threadId
        }

        override suspend fun streamReply(
            threadId: String,
            prompt: ChatPrompt,
            onEvent: (ChatStreamEvent) -> Unit,
        ) {
            streamReplyCalls += 1
            script(threadId, prompt, onEvent)
        }

        override suspend fun cancelRun(
            threadId: String,
            runId: String,
            cancelSource: String,
        ): MobileRunCancellation {
            cancelledRuns += Triple(threadId, runId, cancelSource)
            return MobileRunCancellation(
                runId,
                accepted = cancellationAccepted,
                status = cancellationStatus,
                backendStatus = cancellationBackendStatus,
                deviceStatus = cancellationDeviceStatus,
                localFenced = cancellationLocalFenced,
                retryable = cancellationRetryable,
                cancelSource = cancelSource,
                terminalReason = cancelSource,
            )
        }

        override suspend fun getRunStatus(threadId: String, runId: String): MobileRunStatus {
            statusPollCalls += 1
            return MobileRunStatus(
                runId,
                localStatus = if (polledStatusTerminal) "cancelled" else "active",
                backendStatus = polledBackendStatus,
                terminal = polledStatusTerminal,
            )
        }

        override suspend fun confirmTaskProgress(confirmationId: String): List<ChatStreamEvent> {
            confirmedConfirmationIds += confirmationId
            return confirmEvents
        }

        override suspend fun rejectTaskProgress(confirmationId: String): List<ChatStreamEvent> {
            rejectedConfirmationIds += confirmationId
            return rejectEvents
        }

        override suspend fun takeOverTaskProgress(confirmationId: String): List<ChatStreamEvent> {
            takenOverConfirmationIds += confirmationId
            return takeOverEvents
        }
    }
}
