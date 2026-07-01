package com.example.blueheartv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blueheartv.BuildConfig
import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.chat.ChatPrompt
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.chat.MobileRunCancellation
import com.example.blueheartv.chat.VoiceTranscriptionResult
import com.example.blueheartv.chat.stream.StreamEventDecision
import com.example.blueheartv.chat.stream.StreamDropReason
import com.example.blueheartv.chat.stream.StreamLifecycleDecision
import com.example.blueheartv.chat.stream.StreamLifecycleState
import com.example.blueheartv.chat.stream.StreamManager
import com.example.blueheartv.chat.stream.StreamSession
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

enum class ChatState {
    DEFAULT,
    CHAT_SIMPLE,
    CHAT_TOOL_CALLING,
}

enum class ChatSessionState {
    IDLE,
    RESPONDING,
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
    val streamLifecycleState: StreamLifecycleState = StreamLifecycleState.IDLE,
)

private const val SEND_DEBOUNCE_MS = 500L
private const val CANCEL_STATUS_POLL_INTERVAL_MS = 500L
private const val CANCEL_STATUS_MAX_POLLS = 20
private const val STREAM_ACTIVITY_TIMEOUT_MS = 90_000L

private const val TAG = "ChatViewModel"

private data class PendingUndoMutation(
    val token: Long,
    val snapshot: ConversationMutationSnapshot,
)

private enum class CancellationSource {
    USER,
    STREAM_DISCONNECTED,
    STREAM_ERROR,
    APP_BACKGROUND,
    SESSION_DELETED,
}

private fun CancellationSource.serverValue(): String = when (this) {
    CancellationSource.USER -> "user"
    CancellationSource.STREAM_DISCONNECTED -> "client_disconnected"
    CancellationSource.STREAM_ERROR -> "stream_error"
    CancellationSource.APP_BACKGROUND -> "client_disconnected"
    CancellationSource.SESSION_DELETED -> "session_deleted"
}

private fun ChatSessionState.defaultStreamLifecycleState(): StreamLifecycleState = when (this) {
    ChatSessionState.IDLE -> StreamLifecycleState.IDLE
    ChatSessionState.RESPONDING -> StreamLifecycleState.STREAMING
    ChatSessionState.CANCELLING -> StreamLifecycleState.CANCELING
    ChatSessionState.CANCELLED -> StreamLifecycleState.CANCELED
    ChatSessionState.BACKEND_STILL_RUNNING -> StreamLifecycleState.CANCELED_WITH_UNCONFIRMED_BACKEND
    ChatSessionState.ERROR -> StreamLifecycleState.FAILED
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
    private val streamManager = StreamManager()
    private var cancellationJob: Job? = null
    private var streamTimeoutJob: Job? = null
    private var cancelAttemptCount: Int = 0
    private val streamTextAccumulator = StreamTextAccumulator()

    init {
        restoreSessions()
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun setInputMode(mode: InputMode) {
        _uiState.update { it.copy(inputMode = mode) }
    }

    fun toggleInputMode() {
        _uiState.update { state ->
            val newMode = if (state.inputMode == InputMode.TEXT) InputMode.VOICE else InputMode.TEXT
            state.copy(inputMode = newMode)
        }
    }

    fun sendVoiceText(text: String) {
        val recognized = text.trim()
        if (recognized.isBlank()) return
        _uiState.update { state ->
            val current = state.inputText.trimEnd()
            val next = if (current.isBlank()) recognized else "$current $recognized"
            state.copy(
                inputText = next.take(2000),
                inputMode = InputMode.TEXT,
            )
        }
    }

    suspend fun transcribeVoiceAudio(
        audio: ByteArray,
        audioFormat: String = "pcm",
        sampleRate: Int = 16000,
        language: String = "zh-CN",
    ): VoiceTranscriptionResult {
        if (!AgentServerConfigStore.snapshot().isConfigured) {
            error("请先配置服务地址")
        }
        return chatProvider.transcribeVoice(audio, audioFormat, sampleRate, language)
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
                streamLifecycleState = if (cancellation == null) {
                    StreamLifecycleState.IDLE
                } else {
                    StreamLifecycleState.CANCELING
                },
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
        val cancellation = if (streamManager.hasSessionOnThread(sessionId)) {
            stopActiveRun(markAssistantCancelled = false)
        } else {
            null
        }
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
        streamTextAccumulator.clearAll()
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
        val stream = streamManager.start(
            threadId = threadId,
            assistantMessageId = assistantMessageId,
        )
        cancelAttemptCount = 0
        _uiState.update {
            it.copy(
                canCancel = true,
                streamLifecycleState = StreamLifecycleState.STARTING,
            )
        }
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

        fun scheduleActivityTimeout() {
            val capturedVersion = stream.version
            streamTimeoutJob?.cancel()
            streamTimeoutJob = viewModelScope.launch {
                delay(STREAM_ACTIVITY_TIMEOUT_MS)
                val lifecycleDecision = streamManager.onHeartbeatTimeout(
                    session = stream,
                    capturedVersion = capturedVersion,
                )
                if (lifecycleDecision !is StreamLifecycleDecision.Interrupted) return@launch
                val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                val interruptedTrace = trace?.let(::interruptTrace)
                streamTextAccumulator.take(assistantMessageId, runId = stream.accumulatorRunId)
                finishActiveStream(stream, cancelStreamJob = true)
                onStreamError(
                    threadId = threadId,
                    assistantMessageId = assistantMessageId,
                    reason = "连接超时，当前回复未确认完成。请重试。",
                    retryable = true,
                    chatState = chatState,
                    toolCalls = legacyToolCallsFor(interruptedTrace),
                    trace = interruptedTrace,
                    lastReceivedStreamSeq = stream.receivedStreamSeq,
                    terminalStatus = "interrupted",
                    streamLifecycleState = StreamLifecycleState.INTERRUPTED,
                )
                _taskCompletionEvent.tryEmit(
                    TaskCompletionEvent(taskComplexity, success = false),
                )
            }
        }

        scheduleActivityTimeout()

        fun finishFromTerminalTrace(finalTrace: AssistantTrace, chatState: ChatState) {
            finishActiveStream(stream)
            when (finalTrace.runStatus) {
                TraceRunStatus.SUCCEEDED -> {
                    updateAssistantMessage(assistantMessageId, chatState, ChatSessionState.IDLE) { msg ->
                        msg.copy(
                            content = msg.content.ifBlank { "任务已在服务端完成。" },
                            deliveryState = MessageDeliveryState.COMPLETED,
                            trace = finalTrace,
                            toolCalls = legacyToolCallsFor(finalTrace),
                            errorMessage = null,
                            lastReceivedStreamSeq = stream.receivedStreamSeq,
                            terminalStatus = finalTrace.terminalStatusName(),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            streamingStep = null,
                            lastError = null,
                            canRetry = false,
                            canCancel = false,
                            streamLifecycleState = StreamLifecycleState.DONE,
                        )
                    }
                    _taskCompletionEvent.tryEmit(TaskCompletionEvent(taskComplexity, success = true))
                }

                TraceRunStatus.WAITING_FOR_USER -> {
                    updateAssistantMessage(assistantMessageId, ChatState.CHAT_TOOL_CALLING, ChatSessionState.IDLE) { msg ->
                        msg.copy(
                            content = msg.content.ifBlank { "需要你处理后再继续。" },
                            deliveryState = MessageDeliveryState.COMPLETED,
                            trace = finalTrace,
                            toolCalls = legacyToolCallsFor(finalTrace),
                            errorMessage = null,
                            lastReceivedStreamSeq = stream.receivedStreamSeq,
                            terminalStatus = finalTrace.terminalStatusName(),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            streamingStep = null,
                            lastError = null,
                            canRetry = false,
                            canCancel = false,
                            streamLifecycleState = StreamLifecycleState.WAITING_FOR_USER,
                        )
                    }
                    _taskCompletionEvent.tryEmit(TaskCompletionEvent(taskComplexity, success = false))
                }

                TraceRunStatus.FAILED,
                TraceRunStatus.CANCELLED,
                TraceRunStatus.INTERRUPTED,
                TraceRunStatus.RUNNING -> {
                    onStreamError(
                        threadId = threadId,
                        assistantMessageId = assistantMessageId,
                        reason = if (finalTrace.runStatus == TraceRunStatus.CANCELLED) "任务已停止。" else "处理未能完成，请重试。",
                        retryable = finalTrace.runStatus != TraceRunStatus.CANCELLED,
                        chatState = chatState,
                        toolCalls = legacyToolCallsFor(finalTrace),
                        trace = finalTrace,
                        lastReceivedStreamSeq = stream.receivedStreamSeq,
                        terminalStatus = finalTrace.terminalStatusName(),
                        streamLifecycleState = finalTrace.streamLifecycleState(),
                    )
                    _taskCompletionEvent.tryEmit(TaskCompletionEvent(taskComplexity, success = false))
                }
            }
        }

        try {
            chatProvider.streamReplyWithRun(
                threadId = threadId,
                prompt = prompt,
                runId = stream.runId,
                onEvent = streamEvent@ { event ->
                when (val decision = streamManager.acceptEvent(stream, event)) {
                    is StreamEventDecision.Dropped -> {
                        val detail = "thread=${threadId.shortLogId()} assistantMessage=${assistantMessageId.shortLogId()} run=${stream.runId.shortLogId()} backendRun=${stream.backendRunId?.shortLogId() ?: "none"} event=${decision.eventType} reason=${decision.reason} streamSeq=${decision.streamSeq} last=${decision.lastReceivedStreamSeq}"
                        if (decision.reason == StreamDropReason.STREAM_SEQ) {
                            AppEventLogger.info("chat_stream_event_dropped", detail)
                        } else {
                            AppEventLogger.warning("chat_stream_event_dropped", detail)
                        }
                        return@streamEvent
                    }

                    is StreamEventDecision.Accepted -> {
                        if (decision.terminalStatus != null) {
                            AppEventLogger.info(
                                "chat_stream_terminal_received",
                                "thread=${threadId.shortLogId()} assistantMessage=${assistantMessageId.shortLogId()} run=${stream.runId.shortLogId()} backendRun=${stream.backendRunId?.shortLogId() ?: "none"} status=${decision.terminalStatus} streamSeq=${decision.streamSeq}",
                            )
                        }
                    }
                }
                scheduleActivityTimeout()
                when (event) {
                    is ChatStreamEvent.StreamStarted -> {
                        val message = event.message.ifBlank { "已接收请求，正在连接 Agent。" }
                        trace = trace ?: AssistantTrace(
                            runId = event.runId,
                            threadId = event.threadId,
                            summary = message,
                            displayContext = prompt.text,
                        )
                        _uiState.update {
                            it.copy(
                                streamingStep = message,
                                streamLifecycleState = StreamLifecycleState.STREAMING,
                            )
                        }
                        updateAssistantMessage(
                            assistantMessageId,
                            if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE,
                            ChatSessionState.RESPONDING,
                            refreshHistories = false,
                        ) { msg ->
                            msg.copy(
                                deliveryState = MessageDeliveryState.STREAMING,
                                trace = trace,
                                toolCalls = legacyToolCallsFor(),
                                errorMessage = null,
                                lastReceivedStreamSeq = stream.receivedStreamSeq,
                                terminalStatus = null,
                            )
                        }
                    }

                    is ChatStreamEvent.Heartbeat -> {
                        _uiState.update { state ->
                            if (state.sessionState == ChatSessionState.RESPONDING ||
                                state.sessionState == ChatSessionState.CANCELLING
                            ) {
                                state.copy(
                                    streamingStep = state.streamingStep ?: "仍在处理...",
                                    streamLifecycleState = StreamLifecycleState.STREAMING,
                                )
                            } else {
                                state
                            }
                        }
                    }

                    is ChatStreamEvent.Trace -> {
                        trace = reduceTrace(trace, event.event)
                        trace = trace?.withDisplayContext(prompt.text)
                        val finalTrace = trace
                        if (event.event is TraceEvent.RunTerminal &&
                            finalTrace?.hasTerminal == true &&
                            finalTrace.runStatus != TraceRunStatus.FAILED
                        ) {
                            val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                            finishFromTerminalTrace(finalTrace, chatState)
                            return@streamEvent
                        }
                        _uiState.update {
                            it.copy(
                                streamingStep = "正在处理任务",
                                streamLifecycleState = StreamLifecycleState.STREAMING,
                            )
                        }
                        updateAssistantMessage(
                            assistantMessageId,
                            if (trace.hasActionTraceStep()) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE,
                            ChatSessionState.RESPONDING,
                            refreshHistories = false,
                        ) { msg ->
                            msg.copy(
                                deliveryState = MessageDeliveryState.STREAMING,
                                trace = trace,
                                // 只有新版 Trace UI 实际渲染时才隐藏旧进度卡。
                                toolCalls = legacyToolCallsFor(),
                                lastReceivedStreamSeq = stream.receivedStreamSeq,
                                terminalStatus = finalTrace.terminalStatusName(),
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
                        _uiState.update {
                            it.copy(
                                streamingStep = event.message ?: "正在执行 ${event.label}",
                                streamLifecycleState = StreamLifecycleState.STREAMING,
                            )
                        }
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
                            refreshHistories = false,
                        ) { msg ->
                            msg.copy(
                                deliveryState = MessageDeliveryState.STREAMING,
                                toolCalls = legacyToolCallsFor(),
                                lastReceivedStreamSeq = stream.receivedStreamSeq,
                                terminalStatus = null,
                            )
                        }
                    }

                    is ChatStreamEvent.TextDelta -> {
                        val raw = streamTextAccumulator.append(
                            messageId = assistantMessageId,
                            runId = stream.accumulatorRunId,
                            invocationId = event.invocationId,
                            chunk = event.chunk,
                        )
                        val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                        updateAssistantMessage(
                            assistantMessageId,
                            chatState,
                            ChatSessionState.RESPONDING,
                            refreshHistories = false,
                        ) { msg ->
                            msg.copy(
                                content = raw,
                                deliveryState = MessageDeliveryState.STREAMING,
                                toolCalls = legacyToolCallsFor(),
                                trace = trace,
                                lastReceivedStreamSeq = stream.receivedStreamSeq,
                                terminalStatus = null,
                            )
                        }
                    }

                    ChatStreamEvent.Completed -> {
                        val raw = streamTextAccumulator.take(assistantMessageId, runId = stream.accumulatorRunId)
                        finishActiveStream(stream)
                        val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                        trace?.let { repo.persistTrace(assistantMessageId, it) }
                        _uiState.update {
                            it.copy(
                                streamingStep = null,
                                streamLifecycleState = StreamLifecycleState.DONE,
                            )
                        }
                        updateAssistantMessage(assistantMessageId, chatState, ChatSessionState.IDLE) { msg ->
                            msg.copy(
                                content = raw.ifBlank { "我已经收到你的问题，但暂时没有生成内容。请再试一次。" },
                                deliveryState = MessageDeliveryState.COMPLETED,
                                toolCalls = legacyToolCallsFor(),
                                trace = trace,
                                errorMessage = null,
                                lastReceivedStreamSeq = stream.receivedStreamSeq,
                                terminalStatus = "succeeded",
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
                        val lifecycleDecision = streamManager.onStreamEof(stream)
                        if (lifecycleDecision is StreamLifecycleDecision.Ignore) {
                            return@streamEvent
                        }
                        _uiState.update { it.copy(streamingStep = null) }
                        val finalTrace = trace
                        if (finalTrace?.hasTerminal == true) {
                            finishActiveStream(stream)
                        } else if (lifecycleDecision is StreamLifecycleDecision.Cleanup) {
                            finishActiveStream(stream)
                            return@streamEvent
                        }
                        when {
                            finalTrace == null -> {
                                streamTextAccumulator.take(assistantMessageId, runId = stream.accumulatorRunId)
                                finishActiveStream(stream)
                                val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                                onStreamError(
                                    threadId = threadId,
                                    assistantMessageId = assistantMessageId,
                                    reason = "连接中断，当前回复未确认完成。请重试。",
                                    retryable = true,
                                    chatState = chatState,
                                    toolCalls = legacyToolCallsFor(null),
                                    trace = null,
                                    lastReceivedStreamSeq = stream.receivedStreamSeq,
                                    terminalStatus = "interrupted",
                                    streamLifecycleState = StreamLifecycleState.INTERRUPTED,
                                )
                                _taskCompletionEvent.tryEmit(
                                    TaskCompletionEvent(taskComplexity, success = false),
                                )
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
                                    lastReceivedStreamSeq = stream.receivedStreamSeq,
                                    terminalStatus = "interrupted",
                                    streamLifecycleState = StreamLifecycleState.INTERRUPTED,
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
                                    lastReceivedStreamSeq = stream.receivedStreamSeq,
                                    terminalStatus = finalTrace.terminalStatusName(),
                                    streamLifecycleState = finalTrace.streamLifecycleState(),
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
                                        lastReceivedStreamSeq = stream.receivedStreamSeq,
                                        terminalStatus = finalTrace.terminalStatusName(),
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        lastError = null,
                                        canRetry = false,
                                        streamLifecycleState = StreamLifecycleState.WAITING_FOR_USER,
                                    )
                                }
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
                                        lastReceivedStreamSeq = stream.receivedStreamSeq,
                                        terminalStatus = finalTrace.terminalStatusName(),
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        lastError = null,
                                        canRetry = false,
                                        streamLifecycleState = StreamLifecycleState.DONE,
                                    )
                                }
                                _taskCompletionEvent.tryEmit(
                                    TaskCompletionEvent(taskComplexity, success = true),
                                )
                            }
                        }
                    }

                    is ChatStreamEvent.Error -> {
                        val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                        _uiState.update { it.copy(streamingStep = null) }
                        val finalTrace = trace
                        if (finalTrace?.hasTerminal == true) {
                            finishActiveStream(stream)
                            if (finalTrace.runStatus == TraceRunStatus.SUCCEEDED) {
                                updateAssistantMessage(
                                    assistantMessageId,
                                    chatState,
                                    ChatSessionState.IDLE,
                                ) { msg ->
                                    msg.copy(
                                        content = msg.content.ifBlank { "任务已在服务端完成。" },
                                        deliveryState = MessageDeliveryState.COMPLETED,
                                        trace = finalTrace,
                                        toolCalls = legacyToolCallsFor(finalTrace),
                                        errorMessage = null,
                                        lastReceivedStreamSeq = stream.receivedStreamSeq,
                                        terminalStatus = finalTrace.terminalStatusName(),
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        lastError = null,
                                        canRetry = false,
                                        canCancel = false,
                                        streamLifecycleState = StreamLifecycleState.DONE,
                                    )
                                }
                                _taskCompletionEvent.tryEmit(
                                    TaskCompletionEvent(taskComplexity, success = true),
                                )
                            } else {
                                onStreamError(
                                    threadId = threadId,
                                    assistantMessageId = assistantMessageId,
                                    reason = event.message,
                                    retryable = event.retryable,
                                    chatState = chatState,
                                    toolCalls = legacyToolCallsFor(finalTrace),
                                    trace = finalTrace,
                                    lastReceivedStreamSeq = stream.receivedStreamSeq,
                                    terminalStatus = finalTrace.terminalStatusName(),
                                    streamLifecycleState = finalTrace.streamLifecycleState(),
                                )
                                _taskCompletionEvent.tryEmit(
                                    TaskCompletionEvent(taskComplexity, success = false),
                                )
                            }
                            return@streamEvent
                        }
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
                                lastReceivedStreamSeq = stream.receivedStreamSeq,
                                terminalStatus = event.terminalStatus ?: "failed",
                                streamLifecycleState = StreamLifecycleState.FAILED,
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
            throw cancelled
        } catch (error: Exception) {
            val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
            when (streamManager.onProviderException(stream)) {
                StreamLifecycleDecision.Ignore -> return
                is StreamLifecycleDecision.Cleanup -> {
                    trace?.takeIf { it.hasTerminal }?.let { finalTrace ->
                        AppEventLogger.info(
                            "chat_stream_exception_after_terminal_ignored",
                            "thread=${threadId.shortLogId()} assistantMessage=${assistantMessageId.shortLogId()} run=${stream.runId.shortLogId()} backendRun=${stream.backendRunId?.shortLogId() ?: "none"} terminal=${finalTrace.terminalStatusName()}",
                        )
                        finishFromTerminalTrace(finalTrace, chatState)
                        return
                    }
                    finishActiveStream(stream)
                    return
                }
                is StreamLifecycleDecision.Interrupted -> Unit
            }
            val errorSummary = (error.message ?: "unknown_error")
                .replace(Regex("\\s+"), " ")
                .take(160)
            AppEventLogger.warning(
                "chat_stream_exception",
                "thread=${threadId.shortLogId()} assistantMessage=${assistantMessageId.shortLogId()} run=${stream.runId.shortLogId()} backendRun=${stream.backendRunId?.shortLogId() ?: "none"} errorType=${error.javaClass.simpleName} message=$errorSummary",
            )
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
                    lastReceivedStreamSeq = stream.receivedStreamSeq,
                    terminalStatus = "failed",
                    streamLifecycleState = StreamLifecycleState.FAILED,
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
        val stream = streamManager.activeOrPendingSession() ?: return
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
        val stream = streamManager.activeOrPendingSession()
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
        stream: StreamSession,
        source: CancellationSource,
        trace: AssistantTrace?,
        chatState: ChatState,
        taskComplexity: TaskComplexityLevel,
        serverMessage: String? = null,
    ): Job {
        // Stream 会话继续存活，用于接收后端 terminal 事件。
        // streamJob 继续运行，直到后端发来 terminal 或 EOF
        streamManager.beginCancellation(stream)
        streamTextAccumulator.clear(stream.assistantMessageId, runId = stream.accumulatorRunId)
        val waitingState = ChatSessionState.CANCELLING
        val waitingMessage = when (source) {
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
                lastReceivedStreamSeq = stream.receivedStreamSeq,
                terminalStatus = null,
            )
        }
        _uiState.update {
            it.copy(
                sessionState = waitingState,
                streamingStep = waitingMessage,
                lastError = null,
                canRetry = false,
                canCancel = false,
                streamLifecycleState = StreamLifecycleState.CANCELING,
            )
        }
        return viewModelScope.launch {
            val cancellation = runCatching {
                chatProvider.cancelRun(stream.threadId, stream.runId, source.serverValue())
            }.getOrElse { error ->
                AppEventLogger.warning(
                    "chat_cancel_request_failed",
                    "thread=${stream.threadId} run=${stream.runId} ${error.message ?: "unknown_error"}",
                )
                markBackendStillRunning(stream, chatState, trace, "取消请求发送失败，请检查服务连接后重试。")
                return@launch
            }
            if (!cancellation.accepted) {
                markBackendStillRunning(
                    stream,
                    chatState,
                    trace,
                    cancellationRejectionMessage(cancellation),
                )
                return@launch
            }
            if (cancellation.backendMayStillRun) {
                markBackendStillRunning(
                    stream,
                    chatState,
                    trace,
                    cancellationUnconfirmedMessage(cancellation),
                )
                return@launch
            }
            if (cancellation.confirmedStopped) {
                finishCancellation(
                    stream = stream,
                    source = source,
                    trace = trace,
                    chatState = chatState,
                    taskComplexity = taskComplexity,
                    backendStatus = cancellation.confirmedBackendStatus(),
                    serverMessage = serverMessage,
                )
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
                    terminal = isTerminalBackendStatus(backendStatus)
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
        stream: StreamSession,
        source: CancellationSource,
        trace: AssistantTrace?,
        chatState: ChatState,
        taskComplexity: TaskComplexityLevel,
        backendStatus: String,
        serverMessage: String?,
    ) {
        finishActiveStream(stream, cancelCancellationJob = false, cancelStreamJob = true)
        when (backendStatus) {
            "cancelled", "not_started" -> {
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
                        lastReceivedStreamSeq = stream.receivedStreamSeq,
                        terminalStatus = "cancelled",
                    )
                }
                _uiState.update {
                    it.copy(
                        sessionState = ChatSessionState.CANCELLED,
                        streamingStep = null,
                        lastError = null,
                        canRetry = false,
                        canCancel = false,
                        streamLifecycleState = StreamLifecycleState.CANCELED,
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
                        lastReceivedStreamSeq = stream.receivedStreamSeq,
                        terminalStatus = "succeeded",
                    )
                }
                _uiState.update {
                    it.copy(
                        sessionState = ChatSessionState.IDLE,
                        streamingStep = null,
                        canCancel = false,
                        streamLifecycleState = StreamLifecycleState.DONE,
                    )
                }
            }

            else -> {
                val reason = serverMessage ?: if (backendStatus == "timeout" || backendStatus == "server_timeout") {
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
                    lastReceivedStreamSeq = stream.receivedStreamSeq,
                    terminalStatus = backendStatus,
                    streamLifecycleState = StreamLifecycleState.FAILED,
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
        stream: StreamSession,
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
            finishActiveStream(stream, cancelCancellationJob = false, cancelStreamJob = true)
            onStreamError(
                threadId = stream.threadId,
                assistantMessageId = stream.assistantMessageId,
                reason = "无法停止服务端任务（已重试 $cancelAttemptCount 次）。请稍后重新发送。",
                retryable = true,
                chatState = chatState,
                toolCalls = null,
                trace = trace,
                lastReceivedStreamSeq = stream.receivedStreamSeq,
                terminalStatus = "failed",
                streamLifecycleState = StreamLifecycleState.FAILED,
            )
            return
        }
        streamManager.beginCancellation(stream)
        cancellationJob = null
        updateAssistantMessage(stream.assistantMessageId, chatState, ChatSessionState.BACKEND_STILL_RUNNING) { message ->
            message.copy(
                deliveryState = MessageDeliveryState.FAILED,
                trace = trace ?: message.trace,
                toolCalls = null,
                errorMessage = reason,
                lastReceivedStreamSeq = stream.receivedStreamSeq,
                terminalStatus = "backend_still_running",
            )
        }
        _uiState.update {
            it.copy(
                sessionState = ChatSessionState.BACKEND_STILL_RUNNING,
                streamingStep = "服务端任务仍在运行",
                lastError = reason,
                canRetry = false,
                canCancel = true,
                streamLifecycleState = StreamLifecycleState.CANCELED_WITH_UNCONFIRMED_BACKEND,
            )
        }
        AppEventLogger.warning(
            "chat_backend_still_running",
            "thread=${stream.threadId} run=${stream.runId} attempt=$cancelAttemptCount",
        )
    }

    private fun findMessageTrace(messageId: String): AssistantTrace? =
        repo.getActiveSession()?.messages?.firstOrNull { it.id == messageId }?.trace

    private fun AssistantTrace.withDisplayContext(context: String): AssistantTrace =
        if (displayContext.isNullOrBlank() && context.isNotBlank()) {
            copy(displayContext = context)
        } else {
            this
        }

    private fun AssistantTrace?.hasActionTraceStep(): Boolean =
        this?.steps.orEmpty().any { step ->
            if (!step.visibleToUser) return@any false
            val key = "${step.kind} ${step.title} ${step.summary}".lowercase()
            key.contains("phone_action") ||
                key.contains("launch") ||
                key.contains("open_app") ||
                key.contains("tap") ||
                key.contains("swipe") ||
                key.contains("type") ||
                key.contains("observe") ||
                key.contains("weather_query") ||
                key.contains("system") ||
                key.contains("life_service") ||
                key.contains("office") ||
                key.contains("approval")
        }

    private fun AssistantTrace?.terminalStatusName(): String? =
        if (this == null || !hasTerminal) null else runStatus.terminalStatusName()

    private fun TraceRunStatus.terminalStatusName(): String =
        when (this) {
            TraceRunStatus.RUNNING -> "running"
            TraceRunStatus.SUCCEEDED -> "succeeded"
            TraceRunStatus.FAILED -> "failed"
            TraceRunStatus.CANCELLED -> "cancelled"
            TraceRunStatus.WAITING_FOR_USER -> "waiting_for_user"
            TraceRunStatus.INTERRUPTED -> "interrupted"
        }

    private fun AssistantTrace.streamLifecycleState(): StreamLifecycleState =
        when (runStatus) {
            TraceRunStatus.SUCCEEDED -> StreamLifecycleState.DONE
            TraceRunStatus.CANCELLED -> StreamLifecycleState.CANCELED
            TraceRunStatus.WAITING_FOR_USER -> StreamLifecycleState.WAITING_FOR_USER
            TraceRunStatus.INTERRUPTED -> StreamLifecycleState.INTERRUPTED
            TraceRunStatus.FAILED -> StreamLifecycleState.FAILED
            TraceRunStatus.RUNNING -> StreamLifecycleState.STREAMING
        }

    private fun isTerminalBackendStatus(status: String): Boolean =
        status in setOf(
            "succeeded", "failed", "cancelled", "timeout",
            "server_timeout",
            "stream_closed",
            "thread_busy",
            "not_started",           // upstream 从未启动 Run
        )

    private fun MobileRunCancellation.confirmedBackendStatus(): String =
        when (status) {
            "local_fenced_only", "not_bound_but_fenced" -> "not_started"
            else -> backendStatus
        }

    private fun cancellationUnconfirmedMessage(cancellation: MobileRunCancellation): String =
        when (cancellation.status) {
            "backend_still_running" -> "已停止本地接收，但服务端任务仍可能运行。请再次停止。"
            "backend_run_not_bound" -> "已停止本地接收，但无法确认绑定的服务端任务已停止。请再次停止。"
            "cancel_unavailable" -> "已停止本地接收，但暂时无法联系服务端确认任务停止。请稍后重试。"
            "cancel_request_failed" -> "服务端取消请求失败，任务可能仍在运行。请再次停止。"
            "device_cancel_failed" -> "已停止本地接收，但手机端取消确认失败，任务可能仍在运行。"
            "local_fenced_only" -> "已停止本地接收，但服务端任务尚未确认结束。请再次停止。"
            else -> "无法确认服务端任务已停止。请再次停止。"
        }

    private fun cancellationRejectionMessage(cancellation: MobileRunCancellation): String =
        when (cancellation.status) {
            "not_found" -> "服务端未找到该任务，已停止本地等待。"
            else -> "服务端拒绝取消请求：${cancellation.status}。"
        }

    private fun finishActiveStream(
        stream: StreamSession,
        cancelCancellationJob: Boolean = true,
        cancelStreamJob: Boolean = false,
    ) {
        val wasActive = streamManager.activeSession === stream
        val wasPendingCancellation = streamManager.pendingCancellation === stream
        val cleared = streamManager.finish(stream)
        if (wasActive && cancelStreamJob) {
            streamJob?.cancel()
            streamJob = null
        }
        if (wasPendingCancellation) {
            if (cancelCancellationJob) {
                cancellationJob?.cancel()
            }
            cancellationJob = null
        }
        if (cleared) {
            streamTextAccumulator.clear(stream.assistantMessageId, runId = stream.accumulatorRunId)
            streamTimeoutJob?.cancel()
            streamTimeoutJob = null
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
        lastReceivedStreamSeq: Long? = null,
        terminalStatus: String? = null,
        streamLifecycleState: StreamLifecycleState = StreamLifecycleState.FAILED,
    ) {
        updateAssistantMessage(assistantMessageId, chatState, ChatSessionState.ERROR) { msg ->
            msg.copy(
                content = msg.content.ifBlank { "抱歉，这次响应失败了。" },
                deliveryState = MessageDeliveryState.FAILED,
                toolCalls = toolCalls,
                trace = trace ?: msg.trace,
                errorMessage = reason,
                lastReceivedStreamSeq = lastReceivedStreamSeq ?: msg.lastReceivedStreamSeq,
                terminalStatus = terminalStatus ?: msg.terminalStatus ?: "failed",
            )
        }
        _uiState.update {
            it.copy(
                lastError = reason,
                canRetry = retryable,
                streamLifecycleState = streamLifecycleState,
            )
        }
        AppEventLogger.warning(
            "chat_stream_error",
            "thread=$threadId assistantMessage=$assistantMessageId chatState=$chatState retryable=$retryable reason=$reason",
        )
    }

    private fun updateAssistantMessage(
        assistantMessageId: String,
        chatState: ChatState,
        sessionState: ChatSessionState,
        refreshHistories: Boolean = true,
        transform: (Message) -> Message,
    ) {
        val active = repo.updateMessage(assistantMessageId, transform) ?: return

        _uiState.update { state ->
            state.copy(
                chatState = chatState,
                sessionState = sessionState,
                messages = active.messages.toList(),
                histories = if (refreshHistories) repo.buildHistories() else state.histories,
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
                canCancel = streamManager.activeSession != null,
                histories = repo.buildHistories(),
                streamLifecycleState = sessionState.defaultStreamLifecycleState(),
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
                        streamLifecycleState = StreamLifecycleState.IDLE,
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
                        streamLifecycleState = StreamLifecycleState.IDLE,
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

    private fun String.shortLogId(): String =
        if (length <= 12) this else "${take(6)}...${takeLast(4)}"
}
