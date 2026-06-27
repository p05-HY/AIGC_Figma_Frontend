package com.example.blueheartv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blueheartv.BuildConfig
import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.chat.ChatPrompt
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.util.DialogUtil
import com.example.blueheartv.model.*
import com.example.blueheartv.telemetry.AppEventLogger
import com.example.blueheartv.voice.InputMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChatState {
    DEFAULT,
    CHAT_SIMPLE,
    CHAT_TOOL_CALLING,
}

enum class ChatSessionState {
    IDLE,
    RESPONDING,
    TIMEOUT_WAITING_CANCEL,
    CANCELLING,
    CANCELLED,
    BACKEND_STILL_RUNNING,
    ERROR,
}

enum class TaskComplexityLevel {
    SIMPLE,
    COMPLEX,
    UNKNOWN,
}

data class TaskComplexityEvent(
    val complexity: TaskComplexityLevel,
    val trackSteps: Boolean,
    val reason: String,
    val message: String? = null,
)

data class TaskCompletionEvent(
    val complexity: TaskComplexityLevel,
    val success: Boolean,
)

data class MessageMutationResult(
    val token: Long,
)

data class HomeUiState(
    val chatState: ChatState = ChatState.DEFAULT,
    val sessionState: ChatSessionState = ChatSessionState.IDLE,
    val messages: List<Message> = emptyList(),
    val isDrawerOpen: Boolean = false,
    val inputText: String = "",
    val imageAttachments: List<ChatAttachment> = emptyList(),
    val lastError: String? = null,
    val canRetry: Boolean = false,
    val retryPrompt: String? = null,
    val histories: List<ChatHistory> = emptyList(),
    val recommendations: List<SmartRecommendation> = defaultRecommendations,
    val inputMode: InputMode = InputMode.TEXT,
    val streamingStep: String? = null,
    val canCancel: Boolean = false,
)

private const val SEND_DEBOUNCE_MS = 500L
private const val STREAMING_TIMEOUT_MS = 60_000L
private const val CANCEL_STATUS_POLL_INTERVAL_MS = 500L
private const val CANCEL_STATUS_MAX_POLLS = 20

private const val TAG = "ChatViewModel"

private data class PendingUndoMutation(
    val token: Long,
    val snapshot: ConversationMutationSnapshot,
)

private data class ActiveStream(
    val threadId: String,
    val assistantMessageId: String,
    var runId: String,
)

private enum class CancellationSource {
    USER,
    TIMEOUT,
    STREAM_DISCONNECTED,
    STREAM_ERROR,
    APP_BACKGROUND,
    SESSION_DELETED,
}

class ChatViewModel(
    private val chatProvider: ChatProvider,
    private val repo: ChatSessionRepository,
    private val traceRenderEnabled: Boolean = BuildConfig.TRACE_V1_RENDER_ENABLED,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _messageSentEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messageSentEvent: SharedFlow<String> = _messageSentEvent.asSharedFlow()

    private val _taskComplexityEvent = MutableSharedFlow<TaskComplexityEvent>(extraBufferCapacity = 1)
    val taskComplexityEvent: SharedFlow<TaskComplexityEvent> = _taskComplexityEvent.asSharedFlow()

    private val _taskCompletionEvent = MutableSharedFlow<TaskCompletionEvent>(extraBufferCapacity = 1)
    val taskCompletionEvent: SharedFlow<TaskCompletionEvent> = _taskCompletionEvent.asSharedFlow()

    private val _navigateToSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToSettings: SharedFlow<Unit> = _navigateToSettings.asSharedFlow()

    private var streamJob: Job? = null
    private var historyJob: Job? = null
    private var lastSendAtMillis: Long = 0L
    private var nextUndoToken: Long = 0L
    private var pendingUndoMutation: PendingUndoMutation? = null
    private var activeStream: ActiveStream? = null
    private var pendingCancellation: ActiveStream? = null
    private var cancellationJob: Job? = null
    private var cancelAttemptCount: Int = 0
    private val rawStreamContent = mutableMapOf<String, String>()
    private val streamInvocationIds = mutableMapOf<String, String>()

    init {
        restoreSessions()
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun toggleInputMode() {
        _uiState.update { state ->
            val newMode = if (state.inputMode == InputMode.TEXT) InputMode.VOICE else InputMode.TEXT
            state.copy(inputMode = newMode)
        }
    }

    fun sendVoiceText(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(inputText = text) }
        sendMessage()
    }

    fun addImageAttachment(attachment: ChatAttachment) {
        _uiState.update { state ->
            state.copy(imageAttachments = (state.imageAttachments + attachment).takeLast(4))
        }
    }

    fun removeImageAttachment(id: String) {
        _uiState.update { state ->
            state.copy(imageAttachments = state.imageAttachments.filterNot { it.id == id })
        }
    }

    fun toggleDrawer() {
        _uiState.update { it.copy(isDrawerOpen = !it.isDrawerOpen) }
        if (_uiState.value.isDrawerOpen) restoreSessions(makeLatestActive = false)
    }

    fun closeDrawer() {
        _uiState.update { it.copy(isDrawerOpen = false) }
    }

    val isEmptyConversation: Boolean
        get() = repo.getActiveSession()?.messages.isNullOrEmpty()

    fun startNewConversation() {
        historyJob?.cancel()
        val cancellation = stopActiveRun(markAssistantCancelled = false)
        repo.clearActiveSession()
        publishActiveSession(
            chatState = ChatState.DEFAULT,
            sessionState = if (cancellation == null) ChatSessionState.IDLE else ChatSessionState.CANCELLING,
            lastError = null,
            canRetry = false,
            retryPrompt = null,
            closeDrawer = true,
            keepInputText = false,
            keepAttachments = false,
        )
    }

    fun switchToSimpleChat() {
        submitPrompt("can you speak English?", clearInput = false, resetConversation = true)
    }

    fun switchToToolCallingChat() {
        submitPrompt("帮我规划去南山科技园的路线", clearInput = false, resetConversation = true)
    }

    fun sendQuickAction(prompt: String) {
        submitPrompt(prompt, clearInput = false, resetConversation = true)
    }

    fun switchToDefault() {
        startNewConversation()
    }

    fun clearAllHistory() {
        val cancellation = stopActiveRun(markAssistantCancelled = false)
        val ids = repo.sessions.map { it.id }
        repo.clearAll()
        _uiState.update {
            HomeUiState(
                sessionState = if (cancellation == null) ChatSessionState.IDLE else ChatSessionState.CANCELLING,
                streamingStep = if (cancellation == null) null else "正在停止关联任务。",
            )
        }
        viewModelScope.launch {
            cancellation?.join()
            ids.forEach { id -> runCatching { chatProvider.deleteThread(id) } }
        }
    }

    fun sendRecommendation(rec: SmartRecommendation) {
        submitPrompt(rec.title + "：" + rec.subtitle, clearInput = false, resetConversation = false)
    }

    fun sendMessage() {
        val state = _uiState.value
        val userInput = state.inputText.trim()
        if (userInput.isBlank() && state.imageAttachments.isEmpty()) return

        val now = repo.now()
        if (now - lastSendAtMillis < SEND_DEBOUNCE_MS) {
            AppEventLogger.info("chat_send_debounced", "ignored duplicate send request")
            return
        }
        lastSendAtMillis = now
        val submitted = submitPrompt(userInput, clearInput = true, resetConversation = false)
        if (submitted && userInput.isNotBlank()) {
            _messageSentEvent.tryEmit(userInput)
        }
    }

    fun retryLastMessage() {
        val prompt = _uiState.value.retryPrompt ?: return
        submitPrompt(prompt, clearInput = false, resetConversation = false)
    }

    fun selectHistory(historyId: String) {
        val cancellation = stopActiveRun(markAssistantCancelled = false)
        historyJob?.cancel()
        val cached = repo.switchActive(historyId)
        if (cached != null && cancellation == null) {
            publishActiveSession(
                chatState = deriveChatState(cached.messages),
                sessionState = ChatSessionState.IDLE,
                lastError = null,
                canRetry = false,
                retryPrompt = null,
                closeDrawer = true,
                keepInputText = true,
                keepAttachments = true,
            )
        }
        historyJob = viewModelScope.launch {
            cancellation?.join()
            val remote = runCatching { chatProvider.loadThread(historyId) }.getOrNull()
            val target = if (remote != null) {
                repo.upsertRemoteThread(remote, makeActive = true)
            } else {
                cached ?: repo.switchActive(historyId)
            } ?: return@launch
            publishActiveSession(
                chatState = deriveChatState(target.messages),
                sessionState = ChatSessionState.IDLE,
                lastError = null,
                canRetry = false,
                retryPrompt = null,
                closeDrawer = true,
                keepInputText = true,
                keepAttachments = true,
            )
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        if (repo.renameSession(sessionId, newTitle)) {
            _uiState.update { it.copy(histories = repo.buildHistories()) }
            viewModelScope.launch { runCatching { chatProvider.renameThread(sessionId, newTitle) } }
        }
    }

    fun togglePin(sessionId: String) {
        if (repo.togglePin(sessionId)) {
            _uiState.update { it.copy(histories = repo.buildHistories()) }
        }
    }

    fun deleteSession(sessionId: String) {
        val cancellation = if (
            activeStream?.threadId == sessionId || pendingCancellation?.threadId == sessionId
        ) stopActiveRun(markAssistantCancelled = false) else null
        repo.deleteSession(sessionId)
        val active = repo.getActiveSession()
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(active?.messages.orEmpty()),
                messages = active?.messages?.toList().orEmpty(),
                histories = repo.buildHistories(),
                sessionState = if (cancellation == null) state.sessionState else ChatSessionState.CANCELLING,
                streamingStep = if (cancellation == null) state.streamingStep else "正在停止关联任务。",
            )
        }
        viewModelScope.launch {
            cancellation?.join()
            runCatching { chatProvider.deleteThread(sessionId) }
        }
    }

    fun getShareText(sessionId: String): String = repo.getShareText(sessionId)

    fun deleteMessage(messageId: String): MessageMutationResult? {
        val snapshot = repo.snapshotActiveSession() ?: return null
        val active = repo.deleteMessage(messageId) ?: return null
        val result = storeUndoSnapshot(snapshot)
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(active.messages),
                messages = active.messages.toList(),
            )
        }
        return result
    }

    fun quoteMessage(content: String) {
        val quoted = "「$content」\n"
        _uiState.update { it.copy(inputText = quoted + it.inputText) }
    }

    fun editAndResend(messageId: String, newContent: String): MessageMutationResult? {
        val prompt = newContent.trim()
        val attachments = _uiState.value.imageAttachments
        if (!canSubmitPrompt(prompt, attachments)) return null

        val snapshot = repo.snapshotActiveSession() ?: return null
        val active = repo.removeMessageAndFollowing(messageId) ?: return null
        val result = storeUndoSnapshot(snapshot)
        _uiState.update { state -> state.copy(messages = active.messages.toList()) }
        if (!startPrompt(prompt, attachments, clearInput = false, resetConversation = false)) {
            pendingUndoMutation = null
            restoreUndoSnapshot(snapshot)
            return null
        }
        return result
    }

    fun deleteAiMessage(messageId: String): MessageMutationResult? {
        val snapshot = repo.snapshotActiveSession() ?: return null
        val active = repo.deleteAiMessage(messageId) ?: return null
        val result = storeUndoSnapshot(snapshot)
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(active.messages),
                messages = active.messages.toList(),
            )
        }
        return result
    }

    fun undoLastMessageMutation(token: Long): Boolean {
        val pending = pendingUndoMutation ?: return false
        if (pending.token != token) return false
        stopActiveRun(markAssistantCancelled = false)
        rawStreamContent.clear()
        streamInvocationIds.clear()
        pendingUndoMutation = null
        return restoreUndoSnapshot(pending.snapshot)
    }

    fun undoLastMessageMutation(): Boolean {
        val token = pendingUndoMutation?.token ?: return false
        return undoLastMessageMutation(token)
    }

    private fun storeUndoSnapshot(snapshot: ConversationMutationSnapshot): MessageMutationResult {
        val token = ++nextUndoToken
        pendingUndoMutation = PendingUndoMutation(token = token, snapshot = snapshot)
        return MessageMutationResult(token)
    }

    private fun restoreUndoSnapshot(snapshot: ConversationMutationSnapshot): Boolean {
        val active = repo.restoreSnapshot(snapshot) ?: return false
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(active.messages),
                sessionState = ChatSessionState.IDLE,
                messages = active.messages.toList(),
                histories = repo.buildHistories(),
                lastError = null,
                canRetry = false,
                retryPrompt = null,
            )
        }
        return true
    }

    private fun submitPrompt(prompt: String, clearInput: Boolean, resetConversation: Boolean): Boolean {
        val attachments = _uiState.value.imageAttachments
        if (!canSubmitPrompt(prompt, attachments)) return false
        return startPrompt(prompt, attachments, clearInput, resetConversation)
    }

    private fun canSubmitPrompt(prompt: String, attachments: List<ChatAttachment>): Boolean {
        if (!AgentServerConfigStore.snapshot().isConfigured) {
            DialogUtil.showAlert(
                title = "未配置服务",
                message = "请先在设置中配置服务地址",
                confirmText = "去设置",
                cancelText = "取消",
                onConfirm = { _navigateToSettings.tryEmit(Unit) },
            )
            return false
        }
        if (prompt.isBlank() && attachments.isEmpty()) return false
        if (_uiState.value.sessionState in setOf(
                ChatSessionState.RESPONDING,
                ChatSessionState.TIMEOUT_WAITING_CANCEL,
                ChatSessionState.CANCELLING,
                ChatSessionState.BACKEND_STILL_RUNNING,
            )
        ) {
            AppEventLogger.warning(
                "chat_send_blocked_by_active_run",
                "state=${_uiState.value.sessionState}",
            )
            return false
        }
        return true
    }

    private fun startPrompt(
        prompt: String,
        attachments: List<ChatAttachment>,
        clearInput: Boolean,
        resetConversation: Boolean,
    ): Boolean {
        val outgoingPrompt = ChatPrompt(text = prompt, attachments = attachments)
        if (clearInput) {
            _uiState.update { it.copy(inputText = "", imageAttachments = emptyList()) }
        }

        stopActiveRun(markAssistantCancelled = false)
        streamJob = viewModelScope.launch {
            val activeSession = try {
                if (resetConversation || repo.getActiveSession() == null) {
                    repo.createLocalSession(chatProvider.createThread(prompt.takeIf { it.isNotBlank() }))
                } else {
                    repo.getActiveSession()!!
                }
            } catch (error: Exception) {
                Log.e(TAG, error.message, error)
                onPreflightError(error.message ?: "无法创建服务端会话", prompt)
                return@launch
            }

            val userMessage = Message(
                id = repo.createId(),
                content = prompt.ifBlank { "[图片]" },
                isUser = true,
                deliveryState = MessageDeliveryState.COMPLETED,
            )
            val assistantMessageId = repo.createId()
            val assistantPlaceholder = Message(
                id = assistantMessageId,
                content = "",
                isUser = false,
                deliveryState = MessageDeliveryState.SENDING,
            )

            repo.appendPromptMessages(userMessage, assistantPlaceholder, titleHint = prompt)

            AppEventLogger.info(
                "chat_send",
                "thread=${activeSession.id} userMessage=${userMessage.id} assistantMessage=$assistantMessageId promptLength=${prompt.length} attachments=${attachments.size}",
            )

            publishActiveSession(
                chatState = ChatState.CHAT_SIMPLE,
                sessionState = ChatSessionState.RESPONDING,
                lastError = null,
                canRetry = false,
                retryPrompt = prompt,
                closeDrawer = true,
                keepInputText = !clearInput,
                keepAttachments = !clearInput,
            )

            startStreamingReply(activeSession.id, assistantMessageId, outgoingPrompt)
        }
        return true
    }

    private suspend fun startStreamingReply(
        threadId: String,
        assistantMessageId: String,
        prompt: ChatPrompt,
    ) {
        val stream = ActiveStream(
            threadId = threadId,
            assistantMessageId = assistantMessageId,
            runId = "run_${UUID.randomUUID().toString().replace("-", "")}",
        )
        activeStream = stream
        cancelAttemptCount = 0
        _uiState.update { it.copy(canCancel = true) }
        val toolCallStatus = linkedMapOf<String, ToolCall>()
        var hasToolCalling = false
        var trace: AssistantTrace? = null
        var taskComplexity = TaskComplexityLevel.UNKNOWN

        fun legacyToolCallsFor(currentTrace: AssistantTrace? = trace): List<ToolCall>? =
            if (traceRenderEnabled && currentTrace != null) {
                null
            } else {
                toolCallStatus.toToolCallListOrNull()
            }

        // 流式超时检测：60 秒无事件则认为卡住
        var lastEventAt = repo.now()
        var timeoutRunning = true
        val timeoutJob = viewModelScope.launch {
            while (timeoutRunning) {
                delay(5_000)
                if (!timeoutRunning) return@launch
                if (repo.now() - lastEventAt > STREAMING_TIMEOUT_MS) {
                    timeoutRunning = false
                    requestRunCancellation(
                        stream = stream,
                        source = CancellationSource.TIMEOUT,
                        trace = trace,
                        chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE,
                        taskComplexity = taskComplexity,
                    )
                    return@launch
                }
            }
        }

        try {
            chatProvider.streamReplyWithRun(
                threadId = threadId,
                prompt = prompt,
                runId = stream.runId,
                onEvent = streamEvent@ { event ->
                if (activeStream !== stream && pendingCancellation !== stream) return@streamEvent
                lastEventAt = repo.now()
                when (event) {
                    is ChatStreamEvent.StreamStarted -> {
                        stream.runId = event.runId
                        val message = event.message.ifBlank { "已接收请求，正在连接 Agent。" }
                        _uiState.update { it.copy(streamingStep = message) }
                        updateAssistantMessage(
                            assistantMessageId,
                            if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE,
                            ChatSessionState.RESPONDING,
                        ) { msg ->
                            msg.copy(
                                deliveryState = MessageDeliveryState.STREAMING,
                                errorMessage = null,
                            )
                        }
                    }

                    is ChatStreamEvent.Trace -> {
                        stream.runId = event.event.runId
                        trace = reduceTrace(trace, event.event)
                        _uiState.update { it.copy(streamingStep = "正在执行手机操作") }
                        updateAssistantMessage(
                            assistantMessageId,
                            ChatState.CHAT_TOOL_CALLING,
                            ChatSessionState.RESPONDING,
                        ) { msg ->
                            msg.copy(
                                deliveryState = MessageDeliveryState.STREAMING,
                                trace = trace,
                                // 只有新版 Trace UI 实际渲染时才隐藏旧进度卡。
                                toolCalls = legacyToolCallsFor(),
                            )
                        }
                    }

                    is ChatStreamEvent.TaskComplexity -> {
                        taskComplexity = when (event.complexity.lowercase()) {
                            "complex" -> TaskComplexityLevel.COMPLEX
                            "simple" -> TaskComplexityLevel.SIMPLE
                            else -> TaskComplexityLevel.UNKNOWN
                        }
                        _taskComplexityEvent.tryEmit(
                            TaskComplexityEvent(
                                complexity = taskComplexity,
                                trackSteps = event.trackSteps,
                                reason = event.reason,
                                message = event.message,
                            )
                        )
                    }

                    is ChatStreamEvent.TaskProgress -> {
                        hasToolCalling = true
                        _uiState.update { it.copy(streamingStep = event.message ?: "正在执行 ${event.label}") }
                        val key = toolCallStatus.findToolCallKey(
                            label = event.label,
                            toolName = event.toolName,
                            progressKey = event.progressKey,
                        )
                        val existing = toolCallStatus[key]
                        toolCallStatus[key] = ToolCall(
                            label = event.label,
                            status = event.status.toToolCallStatus(),
                            phase = event.phase,
                            message = event.message,
                            toolName = event.toolName,
                            progressKey = event.progressKey,
                            currentStep = event.currentStep,
                            totalSteps = event.totalSteps,
                            completedSteps = event.completedSteps.map {
                                ToolProgressStep(
                                    index = it.index,
                                    name = it.name,
                                    status = it.status,
                                )
                            },
                        )
                        updateAssistantMessage(
                            assistantMessageId,
                            ChatState.CHAT_TOOL_CALLING,
                            ChatSessionState.RESPONDING,
                        ) { msg ->
                            msg.copy(
                                deliveryState = MessageDeliveryState.STREAMING,
                                toolCalls = legacyToolCallsFor(),
                            )
                        }
                    }

                    is ChatStreamEvent.TextDelta -> {
                        val invocationId = event.invocationId ?: assistantMessageId
                        if (streamInvocationIds[assistantMessageId] != invocationId) {
                            streamInvocationIds[assistantMessageId] = invocationId
                        }
                        val raw = (rawStreamContent[assistantMessageId] ?: "") + event.chunk
                        rawStreamContent[assistantMessageId] = raw
                        val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                        updateAssistantMessage(
                            assistantMessageId,
                            chatState,
                            ChatSessionState.RESPONDING,
                        ) { msg ->
                            msg.copy(
                                content = raw,
                                deliveryState = MessageDeliveryState.STREAMING,
                                toolCalls = legacyToolCallsFor(),
                                trace = trace,
                            )
                        }
                    }

                    ChatStreamEvent.Completed -> {
                        timeoutJob.cancel()
                        finishActiveStream(stream)
                        val raw = rawStreamContent.remove(assistantMessageId) ?: ""
                        streamInvocationIds.remove(assistantMessageId)
                        val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                        trace?.let { repo.persistTrace(assistantMessageId, it) }
                        _uiState.update { it.copy(streamingStep = null) }
                        updateAssistantMessage(assistantMessageId, chatState, ChatSessionState.IDLE) { msg ->
                            msg.copy(
                                content = raw.ifBlank { "我已经收到你的问题，但暂时没有生成内容。请再试一次。" },
                                deliveryState = MessageDeliveryState.COMPLETED,
                                toolCalls = legacyToolCallsFor(),
                                trace = trace,
                                errorMessage = null,
                            )
                        }
                        _uiState.update { it.copy(lastError = null, canRetry = false) }
                        _taskCompletionEvent.tryEmit(
                            TaskCompletionEvent(
                                complexity = taskComplexity,
                                success = true,
                            )
                        )
                    }

                    is ChatStreamEvent.StreamEof -> {
                        timeoutJob.cancel()
                        _uiState.update { it.copy(streamingStep = null) }
                        val finalTrace = trace
                        if (finalTrace?.hasTerminal == true) {
                            finishActiveStream(stream)
                        }
                        when {
                            finalTrace == null -> {
                                finishActiveStream(stream)
                                val raw = rawStreamContent.remove(assistantMessageId) ?: ""
                                streamInvocationIds.remove(assistantMessageId)
                                val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                                if (raw.isBlank() && hasToolCalling) {
                                    onStreamError(
                                        threadId = threadId,
                                        assistantMessageId = assistantMessageId,
                                        reason = "服务连接中断，请稍后重试。",
                                        retryable = true,
                                        chatState = chatState,
                                        toolCalls = legacyToolCallsFor(null),
                                        trace = null,
                                    )
                                    _taskCompletionEvent.tryEmit(
                                        TaskCompletionEvent(taskComplexity, success = false),
                                    )
                                } else {
                                    updateAssistantMessage(
                                        assistantMessageId,
                                        chatState,
                                        ChatSessionState.IDLE,
                                    ) { msg ->
                                        msg.copy(
                                            content = raw.ifBlank { "我已经收到你的问题，但暂时没有生成内容。请再试一次。" },
                                            deliveryState = MessageDeliveryState.COMPLETED,
                                            toolCalls = legacyToolCallsFor(null),
                                            trace = null,
                                            errorMessage = null,
                                        )
                                    }
                                    _uiState.update { it.copy(lastError = null, canRetry = false) }
                                    _taskCompletionEvent.tryEmit(
                                        TaskCompletionEvent(taskComplexity, success = true),
                                    )
                                }
                            }

                            !finalTrace.hasTerminal -> {
                                finishActiveStream(stream)
                                val interruptedTrace = interruptTrace(finalTrace)
                                onStreamError(
                                    threadId = threadId,
                                    assistantMessageId = assistantMessageId,
                                    reason = "连接中断，未收到任务结束状态。请重试。",
                                    retryable = true,
                                    chatState = ChatState.CHAT_TOOL_CALLING,
                                    toolCalls = legacyToolCallsFor(interruptedTrace),
                                    trace = interruptedTrace,
                                )
                                _taskCompletionEvent.tryEmit(
                                    TaskCompletionEvent(taskComplexity, success = false),
                                )
                                return@streamEvent
                            }

                            finalTrace.runStatus in setOf(
                                TraceRunStatus.FAILED,
                                TraceRunStatus.CANCELLED,
                            ) -> {
                                onStreamError(
                                    threadId = threadId,
                                    assistantMessageId = assistantMessageId,
                                    reason = "处理未能完成，请重试。",
                                    retryable = true,
                                    chatState = ChatState.CHAT_TOOL_CALLING,
                                    toolCalls = legacyToolCallsFor(finalTrace),
                                    trace = finalTrace,
                                )
                                _taskCompletionEvent.tryEmit(
                                    TaskCompletionEvent(taskComplexity, success = false),
                                )
                            }

                            finalTrace.runStatus == TraceRunStatus.WAITING_FOR_USER -> {
                                updateAssistantMessage(
                                    assistantMessageId,
                                    ChatState.CHAT_TOOL_CALLING,
                                    ChatSessionState.IDLE,
                                ) { msg ->
                                    msg.copy(
                                        content = msg.content.ifBlank { "需要你处理后再继续。" },
                                        deliveryState = MessageDeliveryState.COMPLETED,
                                        trace = finalTrace,
                                        toolCalls = legacyToolCallsFor(finalTrace),
                                        errorMessage = null,
                                    )
                                }
                                _uiState.update { it.copy(lastError = null, canRetry = false) }
                                _taskCompletionEvent.tryEmit(
                                    TaskCompletionEvent(taskComplexity, success = false),
                                )
                            }

                            else -> {
                                updateAssistantMessage(
                                    assistantMessageId,
                                    if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE,
                                    ChatSessionState.IDLE,
                                ) { msg ->
                                    msg.copy(
                                        content = msg.content.ifBlank { "我已经收到你的问题，但暂时没有生成内容。请再试一次。" },
                                        deliveryState = MessageDeliveryState.COMPLETED,
                                        trace = finalTrace,
                                        toolCalls = legacyToolCallsFor(finalTrace),
                                        errorMessage = null,
                                    )
                                }
                                _uiState.update { it.copy(lastError = null, canRetry = false) }
                                _taskCompletionEvent.tryEmit(
                                    TaskCompletionEvent(taskComplexity, success = true),
                                )
                            }
                        }
                    }

                    is ChatStreamEvent.Error -> {
                        timeoutJob.cancel()
                        val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                        _uiState.update { it.copy(streamingStep = null) }
                        // 如果没有任何 trace 事件到达，说明后端 Run 根本没启动。
                        // 此时取消后端 Run 毫无意义，直接进入 ERROR 状态让用户重试。
                        if (trace == null && !hasToolCalling) {
                            finishActiveStream(stream)
                            onStreamError(
                                threadId = threadId,
                                assistantMessageId = assistantMessageId,
                                reason = event.message,
                                retryable = event.retryable,
                                chatState = chatState,
                                toolCalls = null,
                                trace = null,
                            )
                            _taskCompletionEvent.tryEmit(
                                TaskCompletionEvent(taskComplexity, success = false),
                            )
                            return@streamEvent
                        }
                        requestRunCancellation(
                            stream = stream,
                            source = CancellationSource.STREAM_ERROR,
                            trace = trace,
                            chatState = chatState,
                            taskComplexity = taskComplexity,
                            serverMessage = event.message,
                        )
                        return@streamEvent
                    }
                }
            },
            )
        } catch (cancelled: CancellationException) {
            timeoutJob.cancel()
            throw cancelled
        } catch (error: Exception) {
            timeoutJob.cancel()
            AppEventLogger.error(
                "chat_stream_exception",
                "thread=$threadId assistantMessage=$assistantMessageId ${error.message ?: "unknown_error"}",
                error,
            )
            val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
            // 如果没有任何事件到达，Run 从未启动，跳过 cancelRun。
            if (trace == null && !hasToolCalling) {
                finishActiveStream(stream)
                onStreamError(
                    threadId = threadId,
                    assistantMessageId = assistantMessageId,
                    reason = error.message ?: "请求失败，请稍后重试",
                    retryable = true,
                    chatState = chatState,
                    toolCalls = null,
                    trace = null,
                )
                _taskCompletionEvent.tryEmit(
                    TaskCompletionEvent(taskComplexity, success = false),
                )
                return
            }
            requestRunCancellation(
                stream = stream,
                source = CancellationSource.STREAM_ERROR,
                trace = trace,
                chatState = chatState,
                taskComplexity = taskComplexity,
                serverMessage = error.message ?: "请求失败，请稍后重试",
            )
        }
    }

    private fun onPreflightError(reason: String, prompt: String) {
        _uiState.update {
            it.copy(
                sessionState = ChatSessionState.ERROR,
                lastError = reason,
                canRetry = true,
                retryPrompt = prompt,
            )
        }
    }

    /** 请求停止当前任务；只有服务端确认 terminal 后才显示“已停止”。 */
    fun cancelActiveRun() {
        val stream = activeStream ?: pendingCancellation ?: return
        if (cancellationJob?.isActive == true) return
        requestRunCancellation(
            stream = stream,
            source = CancellationSource.USER,
            trace = findMessageTrace(stream.assistantMessageId),
            chatState = ChatState.CHAT_TOOL_CALLING,
            taskComplexity = TaskComplexityLevel.UNKNOWN,
        )
    }

    /** 页面进入后台时不再自动取消任务。打开外部 App（如飞书/微信）是正常业务
     * 路径，不应误判为用户离开。取消仅由用户显式点击"停止"按钮触发。 */
    fun onAppBackgrounded() {
        // 不再自动取消 —— 打开外部 App 会触发 ON_STOP 是正常业务路径。
        // 保留此方法供未来可选集成使用。
    }

    private fun stopActiveRun(markAssistantCancelled: Boolean): Job? {
        val stream = activeStream ?: pendingCancellation
        if (stream == null) {
            _uiState.update { it.copy(canCancel = false) }
            return null
        }
        if (cancellationJob?.isActive == true) return cancellationJob
        return requestRunCancellation(
            stream = stream,
            source = if (markAssistantCancelled) CancellationSource.USER else CancellationSource.SESSION_DELETED,
            trace = findMessageTrace(stream.assistantMessageId),
            chatState = ChatState.CHAT_TOOL_CALLING,
            taskComplexity = TaskComplexityLevel.UNKNOWN,
        )
    }

    private fun requestRunCancellation(
        stream: ActiveStream,
        source: CancellationSource,
        trace: AssistantTrace?,
        chatState: ChatState,
        taskComplexity: TaskComplexityLevel,
        serverMessage: String? = null,
    ): Job {
        // ✅ 不再抢先置空 activeStream 和取消 streamJob
        // activeStream 继续存活，用于接收后端 terminal 事件
        // streamJob 继续运行，直到后端发来 terminal 或 EOF
        pendingCancellation = stream
        rawStreamContent.remove(stream.assistantMessageId)
        streamInvocationIds.remove(stream.assistantMessageId)
        val waitingState = if (source == CancellationSource.TIMEOUT) {
            ChatSessionState.TIMEOUT_WAITING_CANCEL
        } else {
            ChatSessionState.CANCELLING
        }
        val waitingMessage = when (source) {
            CancellationSource.TIMEOUT -> "响应超时，正在确认停止服务端任务。"
            CancellationSource.APP_BACKGROUND -> "应用已转入后台，正在停止任务。"
            CancellationSource.STREAM_DISCONNECTED -> "连接中断，正在确认停止服务端任务。"
            CancellationSource.STREAM_ERROR -> "服务异常，正在确认停止服务端任务。"
            CancellationSource.SESSION_DELETED -> "正在停止关联任务。"
            CancellationSource.USER -> "正在停止当前任务。"
        }
        updateAssistantMessage(stream.assistantMessageId, chatState, waitingState) { message ->
            message.copy(
                deliveryState = MessageDeliveryState.STREAMING,
                trace = trace ?: message.trace,
                toolCalls = null,
                errorMessage = waitingMessage,
            )
        }
        _uiState.update {
            it.copy(
                sessionState = waitingState,
                streamingStep = waitingMessage,
                lastError = null,
                canRetry = false,
                canCancel = false,
            )
        }
        return viewModelScope.launch {
            val cancellation = runCatching {
                chatProvider.cancelRun(stream.threadId, stream.runId)
            }.getOrElse { error ->
                AppEventLogger.warning(
                    "chat_cancel_request_failed",
                    "thread=${stream.threadId} run=${stream.runId} ${error.message ?: "unknown_error"}",
                )
                markBackendStillRunning(stream, chatState, trace, "取消请求未被服务端确认。")
                return@launch
            }
            if (!cancellation.accepted) {
                markBackendStillRunning(stream, chatState, trace, "服务端未确认取消请求。")
                return@launch
            }
            var backendStatus = cancellation.backendStatus
            var terminal = isTerminalBackendStatus(backendStatus)
            var pollCount = 0
            while (!terminal && pollCount < CANCEL_STATUS_MAX_POLLS) {
                pollCount += 1
                delay(CANCEL_STATUS_POLL_INTERVAL_MS)
                val status = runCatching {
                    chatProvider.getRunStatus(stream.threadId, stream.runId)
                }.getOrNull()
                if (status != null) {
                    backendStatus = status.backendStatus
                    terminal = status.terminal || isTerminalBackendStatus(backendStatus)
                }
            }
            if (terminal) {
                finishCancellation(
                    stream = stream,
                    source = source,
                    trace = trace,
                    chatState = chatState,
                    taskComplexity = taskComplexity,
                    backendStatus = backendStatus,
                    serverMessage = serverMessage,
                )
            } else {
                markBackendStillRunning(
                    stream,
                    chatState,
                    trace,
                    "服务端任务仍在运行，已阻止发送新任务。请再次停止。",
                )
            }
        }.also { cancellationJob = it }
    }

    private fun finishCancellation(
        stream: ActiveStream,
        source: CancellationSource,
        trace: AssistantTrace?,
        chatState: ChatState,
        taskComplexity: TaskComplexityLevel,
        backendStatus: String,
        serverMessage: String?,
    ) {
        finishActiveStream(stream, cancelCancellationJob = false, cancelStreamJob = true)
        when (backendStatus) {
            "cancelled" -> {
                val cancelledTrace = (trace ?: findMessageTrace(stream.assistantMessageId)
                    ?: AssistantTrace(runId = stream.runId)).copy(
                    runStatus = TraceRunStatus.CANCELLED,
                    hasTerminal = true,
                )
                updateAssistantMessage(stream.assistantMessageId, chatState, ChatSessionState.CANCELLED) { message ->
                    message.copy(
                        content = message.content.ifBlank { "已停止当前任务。" },
                        deliveryState = MessageDeliveryState.COMPLETED,
                        trace = cancelledTrace,
                        toolCalls = null,
                        errorMessage = null,
                    )
                }
                _uiState.update {
                    it.copy(
                        sessionState = ChatSessionState.CANCELLED,
                        streamingStep = null,
                        lastError = null,
                        canRetry = false,
                        canCancel = false,
                    )
                }
            }

            "succeeded" -> {
                updateAssistantMessage(stream.assistantMessageId, chatState, ChatSessionState.IDLE) { message ->
                    message.copy(
                        content = message.content.ifBlank { "任务已在服务端完成。" },
                        deliveryState = MessageDeliveryState.COMPLETED,
                        trace = trace ?: message.trace,
                        toolCalls = null,
                        errorMessage = null,
                    )
                }
                _uiState.update { it.copy(sessionState = ChatSessionState.IDLE, streamingStep = null, canCancel = false) }
            }

            else -> {
                val reason = serverMessage ?: if (backendStatus == "timeout") {
                    "任务在服务端超时并已结束。"
                } else {
                    "任务在服务端失败并已结束。"
                }
                onStreamError(
                    threadId = stream.threadId,
                    assistantMessageId = stream.assistantMessageId,
                    reason = reason,
                    retryable = true,
                    chatState = chatState,
                    toolCalls = null,
                    trace = trace ?: findMessageTrace(stream.assistantMessageId),
                )
            }
        }
        _taskCompletionEvent.tryEmit(TaskCompletionEvent(taskComplexity, success = false))
        AppEventLogger.info(
            "chat_cancel_confirmed",
            "thread=${stream.threadId} run=${stream.runId} source=$source backendStatus=$backendStatus",
        )
    }

    private fun markBackendStillRunning(
        stream: ActiveStream,
        chatState: ChatState,
        trace: AssistantTrace?,
        reason: String,
    ) {
        cancelAttemptCount++
        // 连续 3 次取消失败后放弃，转为 ERROR 让用户可以重新发送
        if (cancelAttemptCount >= 3) {
            AppEventLogger.warning(
                "chat_cancel_gave_up",
                "thread=${stream.threadId} run=${stream.runId} attempts=$cancelAttemptCount",
            )
            cancellationJob = null
            pendingCancellation = null
            finishActiveStream(stream, cancelCancellationJob = false, cancelStreamJob = true)
            onStreamError(
                threadId = stream.threadId,
                assistantMessageId = stream.assistantMessageId,
                reason = "无法停止服务端任务（已重试 $cancelAttemptCount 次）。请稍后重新发送。",
                retryable = true,
                chatState = chatState,
                toolCalls = null,
                trace = trace,
            )
            return
        }
        pendingCancellation = stream
        updateAssistantMessage(stream.assistantMessageId, chatState, ChatSessionState.BACKEND_STILL_RUNNING) { message ->
            message.copy(
                deliveryState = MessageDeliveryState.FAILED,
                trace = trace ?: message.trace,
                toolCalls = null,
                errorMessage = reason,
            )
        }
        _uiState.update {
            it.copy(
                sessionState = ChatSessionState.BACKEND_STILL_RUNNING,
                streamingStep = "服务端任务仍在运行",
                lastError = reason,
                canRetry = false,
                canCancel = true,
            )
        }
        AppEventLogger.warning(
            "chat_backend_still_running",
            "thread=${stream.threadId} run=${stream.runId} attempt=$cancelAttemptCount",
        )
    }

    private fun findMessageTrace(messageId: String): AssistantTrace? =
        repo.getActiveSession()?.messages?.firstOrNull { it.id == messageId }?.trace

    private fun isTerminalBackendStatus(status: String): Boolean =
        status in setOf(
            "succeeded", "failed", "cancelled", "timeout",
            // 服务端 safe_stream / cancel_mobile_run 返回的非运行中状态
            "not_started",           // upstream 从未启动 Run
            "cancel_unavailable",    // 无法联系上游 LangGraph
            "cancel_request_failed", // 取消请求被上游拒绝
        )

    private fun finishActiveStream(
        stream: ActiveStream,
        cancelCancellationJob: Boolean = true,
        cancelStreamJob: Boolean = false,
    ) {
        var cleared = false
        if (activeStream === stream) {
            activeStream = null
            if (cancelStreamJob) {
                streamJob?.cancel()
                streamJob = null
            }
            cleared = true
        }
        if (pendingCancellation === stream) {
            pendingCancellation = null
            if (cancelCancellationJob) {
                cancellationJob?.cancel()
            }
            cancellationJob = null
            cleared = true
        }
        if (cleared) {
            _uiState.update { it.copy(canCancel = false) }
        }
    }

    private fun onStreamError(
        threadId: String,
        assistantMessageId: String,
        reason: String,
        retryable: Boolean,
        chatState: ChatState,
        toolCalls: List<ToolCall>?,
        trace: AssistantTrace? = null,
    ) {
        updateAssistantMessage(assistantMessageId, chatState, ChatSessionState.ERROR) { msg ->
            msg.copy(
                content = msg.content.ifBlank { "抱歉，这次响应失败了。" },
                deliveryState = MessageDeliveryState.FAILED,
                toolCalls = toolCalls,
                trace = trace ?: msg.trace,
                errorMessage = reason,
            )
        }
        _uiState.update { it.copy(lastError = reason, canRetry = retryable) }
        AppEventLogger.warning(
            "chat_stream_error",
            "thread=$threadId assistantMessage=$assistantMessageId chatState=$chatState retryable=$retryable reason=$reason",
        )
    }

    private fun updateAssistantMessage(
        assistantMessageId: String,
        chatState: ChatState,
        sessionState: ChatSessionState,
        transform: (Message) -> Message,
    ) {
        val active = repo.updateMessage(assistantMessageId, transform) ?: return

        _uiState.update { state ->
            state.copy(
                chatState = chatState,
                sessionState = sessionState,
                messages = active.messages.toList(),
                histories = repo.buildHistories(),
            )
        }
    }

    private fun publishActiveSession(
        chatState: ChatState,
        sessionState: ChatSessionState,
        lastError: String?,
        canRetry: Boolean,
        retryPrompt: String?,
        closeDrawer: Boolean,
        keepInputText: Boolean,
        keepAttachments: Boolean,
    ) {
        val msgs = repo.getActiveSession()?.messages?.toList().orEmpty()
        _uiState.update { state ->
            state.copy(
                chatState = if (msgs.isEmpty()) ChatState.DEFAULT else chatState,
                sessionState = sessionState,
                messages = msgs,
                isDrawerOpen = if (closeDrawer) false else state.isDrawerOpen,
                inputText = if (keepInputText) state.inputText else "",
                imageAttachments = if (keepAttachments) state.imageAttachments else emptyList(),
                lastError = lastError,
                canRetry = canRetry,
                retryPrompt = retryPrompt,
                canCancel = activeStream != null,
                histories = repo.buildHistories(),
            )
        }
    }

    private fun restoreSessions(makeLatestActive: Boolean = true) {
        historyJob?.cancel()
        val previousActive = repo.activeSessionId
        historyJob = viewModelScope.launch {
            val localResult = runCatching { repo.restoreFromStore() }.getOrNull()
            if (localResult != null) {
                if (!makeLatestActive) {
                    if (previousActive != null) {
                        repo.switchActive(previousActive) ?: repo.clearActiveSession()
                    } else {
                        repo.clearActiveSession()
                    }
                }
                val active = repo.getActiveSession()
                _uiState.update { state ->
                    state.copy(
                        chatState = deriveChatState(active?.messages.orEmpty()),
                        sessionState = ChatSessionState.IDLE,
                        messages = active?.messages?.toList().orEmpty(),
                        histories = repo.buildHistories(),
                        lastError = null,
                        canRetry = false,
                        retryPrompt = null,
                    )
                }
            }

            val result = runCatching {
                val threads = chatProvider.loadThreads()
                repo.restore(threads)
            }
            result.onSuccess {
                if (!makeLatestActive) {
                    if (previousActive != null) {
                        repo.switchActive(previousActive) ?: repo.clearActiveSession()
                    } else {
                        repo.clearActiveSession()
                    }
                }
                val active = repo.getActiveSession()
                _uiState.update { state ->
                    state.copy(
                        chatState = deriveChatState(active?.messages.orEmpty()),
                        sessionState = ChatSessionState.IDLE,
                        messages = active?.messages?.toList().orEmpty(),
                        histories = repo.buildHistories(),
                        lastError = null,
                        canRetry = false,
                        retryPrompt = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        lastError = error.message ?: "无法加载服务端历史",
                        canRetry = true,
                        histories = repo.buildHistories(),
                    )
                }
            }
        }
    }

    private fun deriveChatState(messages: List<Message>): ChatState {
        if (messages.isEmpty()) return ChatState.DEFAULT
        return if (messages.any { it.trace != null || !it.toolCalls.isNullOrEmpty() }) {
            ChatState.CHAT_TOOL_CALLING
        } else {
            ChatState.CHAT_SIMPLE
        }
    }

    private fun LinkedHashMap<String, ToolCall>.findToolCallKey(
        label: String,
        toolName: String? = null,
        progressKey: String? = null,
    ): String {
        val candidates = listOfNotNull(progressKey, toolName, label)
        return entries.firstOrNull { (_, toolCall) ->
            toolCall.label in candidates ||
                (toolCall.toolName != null && toolCall.toolName in candidates) ||
                (toolCall.progressKey != null && toolCall.progressKey in candidates)
        }?.key ?: keys.firstOrNull { it in candidates } ?: candidates.first()
    }

    private fun LinkedHashMap<String, ToolCall>.toToolCallListOrNull(): List<ToolCall>? {
        if (isEmpty()) return null
        return values.toList()
    }

    private fun String.toToolCallStatus(): ToolCallStatus {
        return when (lowercase()) {
            "completed", "done", "end" -> ToolCallStatus.COMPLETED
            "failed", "error" -> ToolCallStatus.FAILED
            else -> ToolCallStatus.RUNNING
        }
    }
}
