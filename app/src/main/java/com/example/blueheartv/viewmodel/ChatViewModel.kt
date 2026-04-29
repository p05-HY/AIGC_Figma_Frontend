package com.example.blueheartv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blueheartv.chat.ChatPrompt
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.model.*
import com.example.blueheartv.telemetry.AppEventLogger
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
    val imageAttachments: List<ChatAttachment> = emptyList(),
    val lastError: String? = null,
    val canRetry: Boolean = false,
    val retryPrompt: String? = null,
    val histories: List<ChatHistory> = emptyList(),
    val recommendations: List<SmartRecommendation> = defaultRecommendations,
)

private const val SEND_DEBOUNCE_MS = 500L

private const val TAG = "ChatViewModel"


class ChatViewModel(
    private val chatProvider: ChatProvider,
    private val repo: ChatSessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var historyJob: Job? = null
    private var lastSendAtMillis: Long = 0L
    private val rawStreamContent = mutableMapOf<String, String>()
    private val completeThinkTagRegex = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
    private val unclosedThinkTagRegex = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE)
    private val danglingThinkCloseTagRegex = Regex("</think>", RegexOption.IGNORE_CASE)

    init {
        restoreSessions()
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
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
        streamJob?.cancel()
        repo.clearActiveSession()
        publishActiveSession(
            chatState = ChatState.DEFAULT,
            sessionState = ChatSessionState.IDLE,
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
        streamJob?.cancel()
        val ids = repo.sessions.map { it.id }
        repo.clearAll()
        _uiState.update { HomeUiState() }
        viewModelScope.launch {
            ids.forEach { id -> runCatching { chatProvider.deleteThread(id) } }
        }
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
        streamJob?.cancel()
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val remote = chatProvider.loadThread(historyId)
            val target = if (remote != null) {
                repo.upsertRemoteThread(remote, makeActive = true)
            } else {
                repo.switchActive(historyId)
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
        repo.deleteSession(sessionId)
        val active = repo.getActiveSession()
        _uiState.update { state ->
            state.copy(
                chatState = deriveChatState(active?.messages.orEmpty()),
                messages = active?.messages?.toList().orEmpty(),
                histories = repo.buildHistories(),
            )
        }
        viewModelScope.launch { runCatching { chatProvider.deleteThread(sessionId) } }
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
        val attachments = _uiState.value.imageAttachments
        if (prompt.isBlank() && attachments.isEmpty()) return
        if (_uiState.value.sessionState == ChatSessionState.RESPONDING) return

        val outgoingPrompt = ChatPrompt(text = prompt, attachments = attachments)
        if (clearInput) {
            _uiState.update { it.copy(inputText = "", imageAttachments = emptyList()) }
        }

        streamJob?.cancel()
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
    }

    private suspend fun startStreamingReply(
        threadId: String,
        assistantMessageId: String,
        prompt: ChatPrompt,
    ) {
        val toolCallStatus = linkedMapOf<String, Boolean>()
        var hasToolCalling = false

        try {
            chatProvider.streamReply(threadId, prompt) { event ->
                when (event) {
                    is ChatStreamEvent.ToolCallStarted -> {
                        hasToolCalling = true
                        toolCallStatus[event.label] = false
                        updateAssistantMessage(
                            assistantMessageId,
                            ChatState.CHAT_TOOL_CALLING,
                            ChatSessionState.RESPONDING,
                        ) { msg ->
                            msg.copy(
                                deliveryState = MessageDeliveryState.STREAMING,
                                toolCalls = toolCallStatus.toToolCallListOrNull(),
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
                        ) { msg ->
                            msg.copy(
                                deliveryState = MessageDeliveryState.STREAMING,
                                toolCalls = toolCallStatus.toToolCallListOrNull(),
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
                        ) { msg ->
                            msg.copy(
                                content = stripThinkTags(raw),
                                deliveryState = MessageDeliveryState.STREAMING,
                                toolCalls = toolCallStatus.toToolCallListOrNull(),
                            )
                        }
                    }

                    ChatStreamEvent.Completed -> {
                        val finalContent = stripThinkTags(rawStreamContent.remove(assistantMessageId) ?: "")
                        val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
                        updateAssistantMessage(assistantMessageId, chatState, ChatSessionState.IDLE) { msg ->
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
                        onStreamError(threadId, assistantMessageId, event.message, event.retryable, chatState)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppEventLogger.error(
                "chat_stream_exception",
                "thread=$threadId assistantMessage=$assistantMessageId ${error.message ?: "unknown_error"}",
                error,
            )
            val chatState = if (hasToolCalling) ChatState.CHAT_TOOL_CALLING else ChatState.CHAT_SIMPLE
            onStreamError(threadId, assistantMessageId, error.message ?: "请求失败，请稍后重试", true, chatState)
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

    private fun onStreamError(
        threadId: String,
        assistantMessageId: String,
        reason: String,
        retryable: Boolean,
        chatState: ChatState,
    ) {
        updateAssistantMessage(assistantMessageId, chatState, ChatSessionState.ERROR) { msg ->
            msg.copy(
                content = msg.content.ifBlank { "抱歉，这次响应失败了。" },
                deliveryState = MessageDeliveryState.FAILED,
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
                histories = repo.buildHistories(),
            )
        }
    }

    private fun restoreSessions(makeLatestActive: Boolean = true) {
        historyJob?.cancel()
        val previousActive = repo.activeSessionId
        historyJob = viewModelScope.launch {
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
