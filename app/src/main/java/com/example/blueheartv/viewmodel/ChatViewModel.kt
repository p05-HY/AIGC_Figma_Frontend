package com.example.blueheartv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.control.AdbController
import com.example.blueheartv.control.ControlAction
import com.example.blueheartv.control.PhoneControlRouter
import com.example.blueheartv.model.*
import com.example.blueheartv.telemetry.AppEventLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ChatState {
    DEFAULT,
    CHAT_SIMPLE,
    CHAT_TOOL_CALLING,
}

enum class ChatSessionState {
    IDLE,
    RESPONDING,
    ERROR,
}

data class HomeUiState(
    val chatState: ChatState = ChatState.DEFAULT,
    val sessionState: ChatSessionState = ChatSessionState.IDLE,
    val messages: List<Message> = emptyList(),
    val isDrawerOpen: Boolean = false,
    val inputText: String = "",
    val lastError: String? = null,
    val canRetry: Boolean = false,
    val retryPrompt: String? = null,
    val histories: List<ChatHistory> = emptyList(),
    val recommendations: List<SmartRecommendation> = defaultRecommendations,
)

private const val SEND_DEBOUNCE_MS = 500L

class ChatViewModel(
    private val chatProvider: ChatProvider,
    private val repo: ChatSessionRepository,
    private val adbController: AdbController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var lastSendAtMillis: Long = 0L
    private val rawStreamContent = mutableMapOf<String, String>()
    private val completeThinkTagRegex = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
    private val unclosedThinkTagRegex = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE)
    private val danglingThinkCloseTagRegex = Regex("</think>", RegexOption.IGNORE_CASE)

    init {
        restoreSessions()
    }

    override fun onCleared() {
        repo.persistImmediate()
        super.onCleared()
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun toggleDrawer() {
        _uiState.update { it.copy(isDrawerOpen = !it.isDrawerOpen) }
    }

    fun closeDrawer() {
        _uiState.update { it.copy(isDrawerOpen = false) }
    }

    val isEmptyConversation: Boolean
        get() = repo.getActiveSession()?.messages.isNullOrEmpty()

    fun startNewConversation() {
        if (isEmptyConversation) return
        streamJob?.cancel()
        repo.startNewSession() ?: return
        publishActiveSession(
            chatState = ChatState.DEFAULT,
            sessionState = ChatSessionState.IDLE,
            lastError = null, canRetry = false, retryPrompt = null,
            closeDrawer = true, keepInputText = false,
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
        streamJob?.cancel()
        repo.clearAll()
        _uiState.update { HomeUiState() }
    }

    fun sendRecommendation(rec: SmartRecommendation) {
        submitPrompt(rec.title + "：" + rec.subtitle, clearInput = false, resetConversation = false)
    }

    fun sendMessage() {
        val now = repo.now()
        if (now - lastSendAtMillis < SEND_DEBOUNCE_MS) {
            AppEventLogger.info("chat_send_debounced", "ignored duplicate send request")
            return
        }
        lastSendAtMillis = now
        submitPrompt(_uiState.value.inputText.trim(), clearInput = true, resetConversation = false)
    }

    fun retryLastMessage() {
        val prompt = _uiState.value.retryPrompt ?: return
        submitPrompt(prompt, clearInput = false, resetConversation = false)
    }

    fun selectHistory(historyId: String) {
        val target = repo.switchActive(historyId) ?: return
        streamJob?.cancel()
        publishActiveSession(
            chatState = deriveChatState(target.messages),
            sessionState = ChatSessionState.IDLE,
            lastError = null, canRetry = false, retryPrompt = null,
            closeDrawer = true, keepInputText = true,
        )
    }

    fun renameSession(sessionId: String, newTitle: String) {
        if (repo.renameSession(sessionId, newTitle)) {
            _uiState.update { it.copy(histories = repo.buildHistories()) }
        }
    }

    fun togglePin(sessionId: String) {
        if (repo.togglePin(sessionId)) {
            _uiState.update { it.copy(histories = repo.buildHistories()) }
        }
    }

    fun deleteSession(sessionId: String) {
        repo.deleteSession(sessionId)
        val active = repo.getActiveSession()
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(active?.messages.orEmpty()),
                messages = active?.messages?.toList().orEmpty(),
                histories = repo.buildHistories(),
            )
        }
    }

    fun getShareText(sessionId: String): String = repo.getShareText(sessionId)

    fun deleteMessage(messageId: String) {
        val active = repo.deleteMessage(messageId) ?: return
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(active.messages),
                messages = active.messages.toList(),
            )
        }
    }

    fun quoteMessage(content: String) {
        val quoted = "「$content」\n"
        _uiState.update { it.copy(inputText = quoted + it.inputText) }
    }

    fun editAndResend(messageId: String, newContent: String) {
        val active = repo.removeMessageAndFollowing(messageId) ?: return
        _uiState.update { state -> state.copy(messages = active.messages.toList()) }
        submitPrompt(newContent, clearInput = false, resetConversation = false)
    }

    fun deleteAiMessage(messageId: String) {
        val active = repo.deleteAiMessage(messageId) ?: return
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(active.messages),
                messages = active.messages.toList(),
            )
        }
    }

    private fun submitPrompt(prompt: String, clearInput: Boolean, resetConversation: Boolean) {
        if (prompt.isBlank()) return
        if (_uiState.value.sessionState == ChatSessionState.RESPONDING) return

        val activeSession = repo.ensureActiveSession(resetConversation)
        val userMessage = Message(
            id = repo.createId(),
            content = prompt,
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
            "session=${activeSession.id} userMessage=${userMessage.id} assistantMessage=$assistantMessageId promptLength=${prompt.length}",
        )

        publishActiveSession(
            chatState = ChatState.CHAT_SIMPLE,
            sessionState = ChatSessionState.RESPONDING,
            lastError = null, canRetry = false, retryPrompt = prompt,
            closeDrawer = true, keepInputText = !clearInput,
        )
        if (clearInput) {
            _uiState.update { it.copy(inputText = "") }
        }

        val controlAction = PhoneControlRouter.parse(prompt)
        if (controlAction != null) {
            handleControlAction(controlAction, assistantMessageId)
        } else {
            startStreamingReply(activeSession, assistantMessageId)
        }
    }

    private fun handleControlAction(action: ControlAction, assistantMessageId: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val result = executeControlAction(action)
            updateAssistantMessage(assistantMessageId, ChatState.CHAT_SIMPLE, ChatSessionState.IDLE, true) { msg ->
                msg.copy(
                    content = result,
                    deliveryState = com.example.blueheartv.model.MessageDeliveryState.COMPLETED,
                    errorMessage = null,
                )
            }
            _uiState.update { it.copy(lastError = null, canRetry = false) }
        }
    }

    private suspend fun executeControlAction(action: ControlAction): String =
        withContext(Dispatchers.IO) {
            runCatching {
                when (action) {
                    is ControlAction.GoHome -> adbController.pressKey(3)
                    is ControlAction.GoBack -> adbController.pressKey(4)
                    is ControlAction.Tap -> adbController.tap(action.x, action.y)
                    is ControlAction.Swipe -> adbController.swipe(action.x1, action.y1, action.x2, action.y2)
                    is ControlAction.LaunchApp -> adbController.launchApp(action.packageName)
                    is ControlAction.TypeText -> adbController.typeText(action.text)
                    is ControlAction.PressKey -> adbController.pressKey(action.keycode)
                    is ControlAction.RunShell -> adbController.runShell(action.command)
                    is ControlAction.DumpScreen -> adbController.dumpUiTree()
                }
            }.getOrElse { e -> "操作失败：${e.message}" }
        }

    private fun startStreamingReply(activeSession: ConversationSession, assistantMessageId: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val toolCallStatus = linkedMapOf<String, Boolean>()
            var hasToolCalling = false
            val sessionId = activeSession.id

            val historyMessages = activeSession.messages
                .filter { it.id != assistantMessageId }
                .filter { it.deliveryState == MessageDeliveryState.COMPLETED }

            try {
                chatProvider.streamReply(historyMessages) { event ->
                    when (event) {
                        is ChatStreamEvent.ToolCallStarted -> {
                            hasToolCalling = true
                            toolCallStatus[event.label] = false
                            updateAssistantMessage(
                                assistantMessageId,
                                ChatState.CHAT_TOOL_CALLING,
                                ChatSessionState.RESPONDING,
                                false
                            ) { msg ->
                                msg.copy(
                                    deliveryState = MessageDeliveryState.STREAMING,
                                    toolCalls = toolCallStatus.toToolCallListOrNull()
                                )
                            }
                        }

                        is ChatStreamEvent.ToolCallCompleted -> {
                            hasToolCalling = true
                            toolCallStatus[event.label] = true
                            updateAssistantMessage(
                                assistantMessageId,
                                ChatState.CHAT_TOOL_CALLING,
                                ChatSessionState.RESPONDING,
                                false
                            ) { msg ->
                                msg.copy(
                                    deliveryState = MessageDeliveryState.STREAMING,
                                    toolCalls = toolCallStatus.toToolCallListOrNull()
                                )
                            }
                        }

                        is ChatStreamEvent.TextDelta -> {
                            val raw = (rawStreamContent[assistantMessageId] ?: "") + event.chunk
                            rawStreamContent[assistantMessageId] = raw
                            val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                            updateAssistantMessage(
                                assistantMessageId,
                                chatState,
                                ChatSessionState.RESPONDING,
                                false
                            ) { msg ->
                                msg.copy(
                                    content = stripThinkTags(raw),
                                    deliveryState = MessageDeliveryState.STREAMING,
                                    toolCalls = toolCallStatus.toToolCallListOrNull()
                                )
                            }
                        }

                        ChatStreamEvent.Completed -> {
                            val finalContent = stripThinkTags(rawStreamContent.remove(assistantMessageId) ?: "")
                            AppEventLogger.info(
                                "chat_stream_completed",
                                "session=$sessionId assistantMessage=$assistantMessageId hasTool=$hasToolCalling contentLength=${finalContent.length}"
                            )
                            val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                            updateAssistantMessage(assistantMessageId, chatState, ChatSessionState.IDLE, true) { msg ->
                                msg.copy(
                                    content = finalContent.ifBlank { "我已经收到你的问题，但暂时没有生成内容。请再试一次。" },
                                    deliveryState = MessageDeliveryState.COMPLETED,
                                    toolCalls = toolCallStatus.toToolCallListOrNull(),
                                    errorMessage = null,
                                )
                            }
                            _uiState.update { it.copy(lastError = null, canRetry = false) }
                        }

                        is ChatStreamEvent.Error -> {
                            rawStreamContent.remove(assistantMessageId)
                            val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                            onStreamError(sessionId, assistantMessageId, event.message, event.retryable, chatState)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppEventLogger.error(
                    "chat_stream_exception",
                    "session=$sessionId assistantMessage=$assistantMessageId ${error.message ?: "unknown_error"}",
                    error
                )
                val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                onStreamError(sessionId, assistantMessageId, error.message ?: "请求失败，请稍后重试", true, chatState)
            }
        }
    }

    private fun onStreamError(
        sessionId: String,
        assistantMessageId: String,
        reason: String,
        retryable: Boolean,
        chatState: ChatState
    ) {
        updateAssistantMessage(assistantMessageId, chatState, ChatSessionState.ERROR, true) { msg ->
            msg.copy(
                content = msg.content.ifBlank { "抱歉，这次响应失败了。" },
                deliveryState = MessageDeliveryState.FAILED,
                errorMessage = reason
            )
        }
        _uiState.update { it.copy(lastError = reason, canRetry = retryable) }
        AppEventLogger.warning(
            "chat_stream_error",
            "session=$sessionId assistantMessage=$assistantMessageId chatState=$chatState retryable=$retryable reason=$reason"
        )
    }

    private fun updateAssistantMessage(
        assistantMessageId: String,
        chatState: ChatState,
        sessionState: ChatSessionState,
        persist: Boolean,
        transform: (Message) -> Message
    ) {
        val active = repo.updateMessage(assistantMessageId, transform) ?: return

        if (persist) repo.requestPersist()

        _uiState.update { state ->
            state.copy(
                chatState = chatState,
                sessionState = sessionState,
                messages = active.messages.toList(),
                histories = repo.buildHistories()
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
        keepInputText: Boolean
    ) {
        val msgs = repo.getActiveSession()?.messages?.toList().orEmpty()
        _uiState.update { state ->
            state.copy(
                chatState = if (msgs.isEmpty()) ChatState.DEFAULT else chatState,
                sessionState = sessionState,
                messages = msgs,
                isDrawerOpen = if (closeDrawer) false else state.isDrawerOpen,
                inputText = if (keepInputText) state.inputText else "",
                lastError = lastError, canRetry = canRetry, retryPrompt = retryPrompt,
                histories = repo.buildHistories(),
            )
        }
    }

    private fun restoreSessions() {
        val result = repo.restore()
        val active = result.activeSession
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(active?.messages.orEmpty()),
                sessionState = ChatSessionState.IDLE,
                messages = active?.messages?.toList().orEmpty(),
                histories = result.histories,
                lastError = null, canRetry = false, retryPrompt = null,
            )
        }
    }

    private fun deriveChatState(messages: List<Message>): ChatState {
        if (messages.isEmpty()) return ChatState.DEFAULT
        return if (messages.any { !it.toolCalls.isNullOrEmpty() }) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
    }

    private fun stripThinkTags(text: String): String {
        var result = text.replace(completeThinkTagRegex, "")
        result = result.replace(unclosedThinkTagRegex, "")
        result = result.replace(danglingThinkCloseTagRegex, "")
        return result.trimStart()
    }

    private fun LinkedHashMap<String, Boolean>.toToolCallListOrNull(): List<ToolCall>? {
        if (isEmpty()) return null
        return entries.map { (label, isComplete) -> ToolCall(label = label, isComplete = isComplete) }
    }
}
