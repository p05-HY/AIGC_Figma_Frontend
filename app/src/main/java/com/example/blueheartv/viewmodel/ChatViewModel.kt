package com.example.blueheartv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blueheartv.chat.AppContextHolder
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatProviderRegistry
import com.example.blueheartv.chat.ChatSessionStore
import com.example.blueheartv.chat.ChatSessionsSnapshot
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.chat.StoredChatSession
import com.example.blueheartv.model.ChatHistory
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.model.SmartRecommendation
import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.telemetry.AppEventLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val recommendations: List<SmartRecommendation> = sampleRecommendations,
)

private const val SEND_DEBOUNCE_MS = 500L
private const val DEFAULT_SESSION_TITLE = "当前对话"

val sampleRecommendations = listOf(
    SmartRecommendation("您下午3点有产品评审会议", "需要我帮您准备会议材料和议程摘要？"),
    SmartRecommendation("您下午3点有产品评审会议", "需要我帮您准备会议材料和议程摘要？"),
    SmartRecommendation("您下午3点有产品评审会议", "需要我帮您准备会议材料和议程摘要？"),
)

private data class ConversationSession(
    val id: String,
    var title: String,
    var updatedAtMillis: Long,
    val messages: MutableList<Message>,
)

class ChatViewModel(
    private val chatProvider: ChatProvider = ChatProviderRegistry.createProvider(),
    private val snapshotLoader: () -> ChatSessionsSnapshot = {
        ChatSessionStore.load(AppContextHolder.getOrNull())
    },
    private val snapshotSaver: (ChatSessionsSnapshot) -> Unit = { snapshot ->
        ChatSessionStore.save(AppContextHolder.getOrNull(), snapshot)
    },
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var activeSessionId: String? = null
    private var lastSendAtMillis: Long = 0L

    private val sessions = mutableListOf<ConversationSession>()

    init {
        restoreSessions()
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

    fun startNewConversation() {
        streamJob?.cancel()
        val newSession = createSession()
        sessions.add(newSession)
        activeSessionId = newSession.id
        persistSessions()
        publishStateFromActiveSession(
            chatState = ChatState.DEFAULT,
            sessionState = ChatSessionState.IDLE,
            lastError = null,
            canRetry = false,
            retryPrompt = null,
            closeDrawer = true,
            keepInputText = false,
        )
    }

    fun switchToSimpleChat() {
        submitPrompt(
            prompt = "can you speak English?",
            clearInput = false,
            resetConversation = true,
        )
    }

    fun switchToToolCallingChat() {
        submitPrompt(
            prompt = "帮我规划去南山科技园的路线",
            clearInput = false,
            resetConversation = true,
        )
    }

    fun switchToDefault() {
        startNewConversation()
    }

    fun sendMessage() {
        val now = timeProvider()
        if (now - lastSendAtMillis < SEND_DEBOUNCE_MS) {
            AppEventLogger.info("chat_send_debounced", "ignored duplicate send request")
            return
        }
        lastSendAtMillis = now

        submitPrompt(
            prompt = _uiState.value.inputText.trim(),
            clearInput = true,
            resetConversation = false,
        )
    }

    fun retryLastMessage() {
        val prompt = _uiState.value.retryPrompt ?: return
        submitPrompt(
            prompt = prompt,
            clearInput = false,
            resetConversation = false,
        )
    }

    fun selectHistory(historyId: String) {
        val target = sessions.firstOrNull { it.id == historyId } ?: return
        streamJob?.cancel()
        activeSessionId = target.id

        publishStateFromActiveSession(
            chatState = deriveChatState(target.messages),
            sessionState = ChatSessionState.IDLE,
            lastError = null,
            canRetry = false,
            retryPrompt = null,
            closeDrawer = true,
            keepInputText = true,
        )
    }

    private fun submitPrompt(
        prompt: String,
        clearInput: Boolean,
        resetConversation: Boolean,
    ) {
        if (prompt.isBlank()) return
        if (_uiState.value.sessionState == ChatSessionState.RESPONDING) return

        val activeSession = ensureActiveSession(resetConversation)
        val userMessage = Message(
            id = createMessageId(),
            content = prompt,
            isUser = true,
            deliveryState = MessageDeliveryState.COMPLETED,
        )
        val assistantMessageId = createMessageId()
        val assistantPlaceholder = Message(
            id = assistantMessageId,
            content = "",
            isUser = false,
            deliveryState = MessageDeliveryState.SENDING,
        )

        activeSession.messages.add(userMessage)
        activeSession.messages.add(assistantPlaceholder)
        activeSession.updatedAtMillis = timeProvider()
        if (activeSession.title == DEFAULT_SESSION_TITLE) {
            activeSession.title = prompt.take(18)
        }

        AppEventLogger.info("chat_send", "session=${activeSession.id} promptLength=${prompt.length}")

        persistSessions()

        publishStateFromActiveSession(
            chatState = ChatState.CHAT_SIMPLE,
            sessionState = ChatSessionState.RESPONDING,
            lastError = null,
            canRetry = false,
            retryPrompt = prompt,
            closeDrawer = true,
            keepInputText = !clearInput,
        )

        if (clearInput) {
            _uiState.update { it.copy(inputText = "") }
        }

        startStreamingReply(
            prompt = prompt,
            assistantMessageId = assistantMessageId,
        )
    }

    private fun startStreamingReply(
        prompt: String,
        assistantMessageId: String,
    ) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val toolCallStatus = linkedMapOf<String, Boolean>()
            var hasToolCalling = false

            try {
                chatProvider.streamReply(prompt) { event ->
                    when (event) {
                        is ChatStreamEvent.ToolCallStarted -> {
                            hasToolCalling = true
                            toolCallStatus[event.label] = false
                            updateAssistantMessage(
                                assistantMessageId = assistantMessageId,
                                chatState = ChatState.CHAT_TOOL_CALLING,
                                sessionState = ChatSessionState.RESPONDING,
                                persist = false,
                            ) { message ->
                                message.copy(
                                    deliveryState = MessageDeliveryState.STREAMING,
                                    toolCalls = toolCallStatus.toToolCallListOrNull(),
                                )
                            }
                        }

                        is ChatStreamEvent.ToolCallCompleted -> {
                            hasToolCalling = true
                            toolCallStatus[event.label] = true
                            updateAssistantMessage(
                                assistantMessageId = assistantMessageId,
                                chatState = ChatState.CHAT_TOOL_CALLING,
                                sessionState = ChatSessionState.RESPONDING,
                                persist = false,
                            ) { message ->
                                message.copy(
                                    deliveryState = MessageDeliveryState.STREAMING,
                                    toolCalls = toolCallStatus.toToolCallListOrNull(),
                                )
                            }
                        }

                        is ChatStreamEvent.TextDelta -> {
                            updateAssistantMessage(
                                assistantMessageId = assistantMessageId,
                                chatState = if (hasToolCalling) {
                                    ChatState.CHAT_TOOL_CALLING
                                } else {
                                    ChatState.CHAT_SIMPLE
                                },
                                sessionState = ChatSessionState.RESPONDING,
                                persist = false,
                            ) { message ->
                                message.copy(
                                    content = message.content + event.chunk,
                                    deliveryState = MessageDeliveryState.STREAMING,
                                    toolCalls = toolCallStatus.toToolCallListOrNull(),
                                )
                            }
                        }

                        ChatStreamEvent.Completed -> {
                            updateAssistantMessage(
                                assistantMessageId = assistantMessageId,
                                chatState = if (hasToolCalling) {
                                    ChatState.CHAT_TOOL_CALLING
                                } else {
                                    ChatState.CHAT_SIMPLE
                                },
                                sessionState = ChatSessionState.IDLE,
                                persist = true,
                            ) { message ->
                                message.copy(
                                    content = message.content.ifBlank {
                                        "我已经收到你的问题，但暂时没有生成内容。请再试一次。"
                                    },
                                    deliveryState = MessageDeliveryState.COMPLETED,
                                    toolCalls = toolCallStatus.toToolCallListOrNull(),
                                    errorMessage = null,
                                )
                            }
                            _uiState.update { it.copy(lastError = null, canRetry = false) }
                        }

                        is ChatStreamEvent.Error -> {
                            onStreamError(
                                assistantMessageId = assistantMessageId,
                                reason = event.message,
                                retryable = event.retryable,
                                chatState = if (hasToolCalling) {
                                    ChatState.CHAT_TOOL_CALLING
                                } else {
                                    ChatState.CHAT_SIMPLE
                                },
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppEventLogger.error("chat_stream_exception", error.message ?: "unknown_error", error)
                onStreamError(
                    assistantMessageId = assistantMessageId,
                    reason = error.message ?: "请求失败，请稍后重试",
                    retryable = true,
                    chatState = if (hasToolCalling) {
                        ChatState.CHAT_TOOL_CALLING
                    } else {
                        ChatState.CHAT_SIMPLE
                    },
                )
            }
        }
    }

    private fun onStreamError(
        assistantMessageId: String,
        reason: String,
        retryable: Boolean,
        chatState: ChatState,
    ) {
        updateAssistantMessage(
            assistantMessageId = assistantMessageId,
            chatState = chatState,
            sessionState = ChatSessionState.ERROR,
            persist = true,
        ) { message ->
            message.copy(
                content = message.content.ifBlank { "抱歉，这次响应失败了。" },
                deliveryState = MessageDeliveryState.FAILED,
                errorMessage = reason,
            )
        }

        _uiState.update {
            it.copy(
                lastError = reason,
                canRetry = retryable,
            )
        }

        AppEventLogger.warning(
            "chat_stream_error",
            "chatState=$chatState retryable=$retryable reason=$reason",
        )
    }

    private fun updateAssistantMessage(
        assistantMessageId: String,
        chatState: ChatState,
        sessionState: ChatSessionState,
        persist: Boolean,
        transform: (Message) -> Message,
    ) {
        val activeSession = getActiveSession() ?: return
        val assistantIndex = activeSession.messages.indexOfFirst { it.id == assistantMessageId }
        if (assistantIndex < 0) return

        activeSession.messages[assistantIndex] = transform(activeSession.messages[assistantIndex])
        activeSession.updatedAtMillis = timeProvider()

        if (persist) {
            persistSessions()
        }

        _uiState.update { state ->
            state.copy(
                chatState = chatState,
                sessionState = sessionState,
                messages = activeSession.messages.toList(),
                histories = buildHistories(),
            )
        }
    }

    private fun ensureActiveSession(resetConversation: Boolean): ConversationSession {
        if (resetConversation || getActiveSession() == null) {
            val newSession = createSession()
            sessions.add(newSession)
            activeSessionId = newSession.id
            return newSession
        }
        return getActiveSession()!!
    }

    private fun getActiveSession(): ConversationSession? {
        val id = activeSessionId ?: return null
        return sessions.firstOrNull { it.id == id }
    }

    private fun createSession(): ConversationSession {
        return ConversationSession(
            id = createMessageId(),
            title = DEFAULT_SESSION_TITLE,
            updatedAtMillis = timeProvider(),
            messages = mutableListOf(),
        )
    }

    private fun publishStateFromActiveSession(
        chatState: ChatState,
        sessionState: ChatSessionState,
        lastError: String?,
        canRetry: Boolean,
        retryPrompt: String?,
        closeDrawer: Boolean,
        keepInputText: Boolean,
    ) {
        val activeMessages = getActiveSession()?.messages?.toList().orEmpty()
        _uiState.update { state ->
            state.copy(
                chatState = if (activeMessages.isEmpty()) ChatState.DEFAULT else chatState,
                sessionState = sessionState,
                messages = activeMessages,
                isDrawerOpen = if (closeDrawer) false else state.isDrawerOpen,
                inputText = if (keepInputText) state.inputText else "",
                lastError = lastError,
                canRetry = canRetry,
                retryPrompt = retryPrompt,
                histories = buildHistories(),
            )
        }
    }

    private fun buildHistories(): List<ChatHistory> {
        return sessions
            .sortedByDescending { it.updatedAtMillis }
            .map { session ->
                ChatHistory(
                    id = session.id,
                    title = session.title.ifBlank { DEFAULT_SESSION_TITLE },
                    timestamp = formatHistoryTime(session.updatedAtMillis),
                    isCurrent = session.id == activeSessionId,
                )
            }
    }

    private fun persistSessions() {
        val snapshot = ChatSessionsSnapshot(
            activeSessionId = activeSessionId,
            sessions = sessions.map { session ->
                StoredChatSession(
                    id = session.id,
                    title = session.title,
                    updatedAtMillis = session.updatedAtMillis,
                    messages = session.messages.toList(),
                )
            },
        )
        snapshotSaver(snapshot)
    }

    private fun restoreSessions() {
        val snapshot = snapshotLoader()
        if (snapshot.sessions.isEmpty()) {
            _uiState.update { it.copy(histories = emptyList()) }
            return
        }

        sessions.clear()
        sessions.addAll(snapshot.sessions.map { it.toConversationSession() })
        activeSessionId = snapshot.activeSessionId
            ?.takeIf { id -> sessions.any { it.id == id } }
            ?: sessions.maxByOrNull { it.updatedAtMillis }?.id

        val activeSession = getActiveSession()
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(activeSession?.messages.orEmpty()),
                sessionState = ChatSessionState.IDLE,
                messages = activeSession?.messages?.toList().orEmpty(),
                histories = buildHistories(),
                lastError = null,
                canRetry = false,
                retryPrompt = null,
            )
        }
    }

    private fun StoredChatSession.toConversationSession(): ConversationSession {
        return ConversationSession(
            id = id,
            title = title,
            updatedAtMillis = updatedAtMillis,
            messages = messages.toMutableList(),
        )
    }

    private fun deriveChatState(messages: List<Message>): ChatState {
        if (messages.isEmpty()) return ChatState.DEFAULT
        val hasToolCalls = messages.any { !it.toolCalls.isNullOrEmpty() }
        return if (hasToolCalls) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
    }

    private fun formatHistoryTime(updatedAtMillis: Long): String {
        val diffMillis = timeProvider() - updatedAtMillis
        val diffMinutes = diffMillis / 60_000
        return when {
            diffMinutes < 1 -> "刚刚"
            diffMinutes < 60 -> "${diffMinutes}分钟前"
            diffMinutes < 24 * 60 -> "${diffMinutes / 60}小时前"
            else -> {
                val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                formatter.format(Date(updatedAtMillis))
            }
        }
    }

    private fun createMessageId(): String = idProvider()

    private fun LinkedHashMap<String, Boolean>.toToolCallListOrNull(): List<ToolCall>? {
        if (isEmpty()) return null
        return entries.map { (label, isComplete) ->
            ToolCall(label = label, isComplete = isComplete)
        }
    }
}
