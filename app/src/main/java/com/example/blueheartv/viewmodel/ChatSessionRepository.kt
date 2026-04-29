package com.example.blueheartv.viewmodel

import com.example.blueheartv.chat.RemoteChatThread
import com.example.blueheartv.model.ChatHistory
import com.example.blueheartv.model.Message
import java.text.SimpleDateFormat
import java.util.*

internal const val DEFAULT_SESSION_TITLE = "当前对话"
private const val MAX_SESSIONS = 20

internal data class ConversationSession(
    val id: String,
    var title: String,
    var updatedAtMillis: Long,
    var isPinned: Boolean = false,
    val messages: MutableList<Message>,
)

class ChatSessionRepository(
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {
    internal val sessions = mutableListOf<ConversationSession>()
    internal var activeSessionId: String? = null
        private set

    internal fun restore(remoteThreads: List<RemoteChatThread>): SessionRestoreResult {
        sessions.clear()
        sessions.addAll(
            remoteThreads
                .take(MAX_SESSIONS)
                .map { it.toConversationSession() },
        )
        activeSessionId = sessions.maxByOrNull { it.updatedAtMillis }?.id
        return SessionRestoreResult(
            activeSession = getActiveSession(),
            histories = buildHistories(),
        )
    }

    internal fun upsertRemoteThread(thread: RemoteChatThread, makeActive: Boolean = false): ConversationSession {
        val existing = sessions.indexOfFirst { it.id == thread.id }
        val session = thread.toConversationSession()
        if (existing >= 0) {
            val pinned = sessions[existing].isPinned
            sessions[existing] = session.copy(isPinned = pinned)
        } else {
            sessions.add(session)
        }
        if (makeActive) activeSessionId = thread.id
        trimSessions()
        return getActiveSession() ?: session
    }

    internal fun getActiveSession(): ConversationSession? {
        val id = activeSessionId ?: return null
        return sessions.firstOrNull { it.id == id }
    }

    internal fun clearActiveSession() {
        activeSessionId = null
    }

    internal fun createLocalSession(remoteThread: RemoteChatThread): ConversationSession {
        val session = remoteThread.toConversationSession()
        sessions.removeAll { it.id == session.id }
        sessions.add(session)
        activeSessionId = session.id
        trimSessions()
        return session
    }

    internal fun switchActive(sessionId: String): ConversationSession? {
        val target = sessions.firstOrNull { it.id == sessionId } ?: return null
        activeSessionId = target.id
        return target
    }

    fun renameSession(sessionId: String, newTitle: String): Boolean {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return false
        session.title = truncateTitle(newTitle)
        session.updatedAtMillis = timeProvider()
        return true
    }

    fun togglePin(sessionId: String): Boolean {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return false
        session.isPinned = !session.isPinned
        return true
    }

    fun deleteSession(sessionId: String) {
        sessions.removeAll { it.id == sessionId }
        if (activeSessionId == sessionId) {
            activeSessionId = sessions.maxByOrNull { it.updatedAtMillis }?.id
        }
    }

    fun clearAll() {
        sessions.clear()
        activeSessionId = null
    }

    fun getShareText(sessionId: String): String {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return ""
        return buildString {
            appendLine("【${session.title}】")
            appendLine()
            session.messages.forEach { msg ->
                val role = if (msg.isUser) "我" else "AI"
                appendLine("$role: ${msg.content}")
            }
        }
    }

    internal fun deleteMessage(messageId: String): ConversationSession? {
        val active = getActiveSession() ?: return null
        val idx = active.messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return null

        val toRemove = mutableListOf(messageId)
        val msg = active.messages[idx]
        if (msg.isUser && idx + 1 < active.messages.size && !active.messages[idx + 1].isUser) {
            toRemove.add(active.messages[idx + 1].id)
        }
        active.messages.removeAll { it.id in toRemove }
        active.updatedAtMillis = timeProvider()
        return active
    }

    internal fun deleteAiMessage(messageId: String): ConversationSession? {
        val active = getActiveSession() ?: return null
        active.messages.removeAll { it.id == messageId }
        active.updatedAtMillis = timeProvider()
        return active
    }

    internal fun removeMessageAndFollowing(messageId: String): ConversationSession? {
        val active = getActiveSession() ?: return null
        val idx = active.messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return null

        val toRemove = mutableListOf(messageId)
        if (idx + 1 < active.messages.size && !active.messages[idx + 1].isUser) {
            toRemove.add(active.messages[idx + 1].id)
        }
        active.messages.removeAll { it.id in toRemove }
        active.updatedAtMillis = timeProvider()
        return active
    }

    fun createId(): String = idProvider()

    fun now(): Long = timeProvider()

    internal fun appendPromptMessages(
        userMessage: Message,
        assistantPlaceholder: Message,
        titleHint: String?,
    ): ConversationSession? {
        val active = getActiveSession() ?: return null
        active.messages.add(userMessage)
        active.messages.add(assistantPlaceholder)
        active.updatedAtMillis = timeProvider()
        if (titleHint != null && active.title == DEFAULT_SESSION_TITLE) {
            active.title = truncateTitle(titleHint)
        }
        return active
    }

    internal fun updateMessage(
        messageId: String,
        transform: (Message) -> Message,
    ): ConversationSession? {
        val active = getActiveSession() ?: return null
        val idx = active.messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return null
        active.messages[idx] = transform(active.messages[idx])
        active.updatedAtMillis = timeProvider()
        return active
    }

    fun buildHistories(): List<ChatHistory> {
        return sessions
            .sortedWith(
                compareByDescending<ConversationSession> { it.isPinned }
                    .thenByDescending { it.updatedAtMillis },
            )
            .map { session ->
                ChatHistory(
                    id = session.id,
                    title = session.title.ifBlank { DEFAULT_SESSION_TITLE },
                    timestamp = formatHistoryTime(session.updatedAtMillis),
                    isCurrent = session.id == activeSessionId,
                    isPinned = session.isPinned,
                )
            }
    }

    private fun RemoteChatThread.toConversationSession(): ConversationSession {
        return ConversationSession(
            id = id,
            title = title.ifBlank { DEFAULT_SESSION_TITLE },
            updatedAtMillis = updatedAtMillis,
            messages = messages.toMutableList(),
        )
    }

    private fun trimSessions() {
        if (sessions.size <= MAX_SESSIONS) return
        val excess = sessions
            .sortedBy { it.updatedAtMillis }
            .take(sessions.size - MAX_SESSIONS)
            .map { it.id }
            .toSet()
        sessions.removeAll { it.id in excess }
        if (activeSessionId in excess) {
            activeSessionId = sessions.maxByOrNull { it.updatedAtMillis }?.id
        }
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
}

internal fun truncateTitle(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.length > 18) trimmed.take(18) + "..." else trimmed
}

internal data class SessionRestoreResult(
    val activeSession: ConversationSession?,
    val histories: List<ChatHistory>,
)
