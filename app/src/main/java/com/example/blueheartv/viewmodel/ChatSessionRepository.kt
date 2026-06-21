package com.example.blueheartv.viewmodel

import com.example.blueheartv.chat.RemoteChatThread
import com.example.blueheartv.model.ChatHistory
import com.example.blueheartv.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

internal const val DEFAULT_SESSION_TITLE = "当前对话"
private const val MAX_SESSIONS = 20

class ChatSessionRepository(
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val store: ChatSessionStore? = null,
    private val persistScope: CoroutineScope? = null,
) {
    internal val sessions = mutableListOf<ConversationSession>()
    internal var activeSessionId: String? = null
        private set

    internal fun restore(remoteThreads: List<RemoteChatThread>): SessionRestoreResult {
        val pinnedById = sessions.associate { it.id to it.isPinned }
        sessions.clear()
        sessions.addAll(
            remoteThreads
                .take(MAX_SESSIONS)
                .map {
                    val session = it.toConversationSession()
                    session.isPinned = pinnedById[session.id] ?: session.isPinned
                    session
                },
        )
        activeSessionId = sessions.maxByOrNull { it.updatedAtMillis }?.id
        return SessionRestoreResult(
            activeSession = getActiveSession(),
            histories = buildHistories(),
        )
    }

    internal suspend fun restoreFromStore(): SessionRestoreResult? {
        val store = store ?: return null
        val loaded = store.loadSessions(MAX_SESSIONS)
        if (loaded.isEmpty()) return null
        sessions.clear()
        sessions.addAll(loaded)
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
        persistSession(session)
        persistMessages(session)
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
        persistSession(session)
        return true
    }

    fun togglePin(sessionId: String): Boolean {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return false
        session.isPinned = !session.isPinned
        persistSession(session)
        return true
    }

    fun deleteSession(sessionId: String) {
        sessions.removeAll { it.id == sessionId }
        if (activeSessionId == sessionId) {
            activeSessionId = sessions.maxByOrNull { it.updatedAtMillis }?.id
        }
        persistDeleteSession(sessionId)
    }

    fun clearAll() {
        sessions.clear()
        activeSessionId = null
        persistDeleteAll()
    }

    internal fun snapshotActiveSession(): ConversationMutationSnapshot? {
        val activeId = activeSessionId ?: return null
        return ConversationMutationSnapshot(
            activeSessionId = activeId,
            sessions = sessions.map { it.deepCopy() },
        )
    }

    internal fun restoreSnapshot(snapshot: ConversationMutationSnapshot): ConversationSession? {
        val snapshotSessionIds = snapshot.sessions.map { it.id }.toSet()
        val removedSessionIds = sessions
            .map { it.id }
            .filterNot { it in snapshotSessionIds }
            .toSet()
        sessions.clear()
        sessions.addAll(snapshot.sessions.map { it.deepCopy() })
        activeSessionId = snapshot.activeSessionId
        persistDeleteSessions(removedSessionIds)
        sessions.forEach { session ->
            persistSession(session)
            persistMessages(session)
        }
        return getActiveSession()
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
        persistSession(active)
        persistMessages(active)
        return active
    }

    internal fun deleteAiMessage(messageId: String): ConversationSession? {
        val active = getActiveSession() ?: return null
        active.messages.removeAll { it.id == messageId }
        active.updatedAtMillis = timeProvider()
        persistSession(active)
        persistMessages(active)
        return active
    }

    internal fun removeMessageAndFollowing(messageId: String): ConversationSession? {
        val active = getActiveSession() ?: return null
        val idx = active.messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return null

        active.messages.subList(idx, active.messages.size).clear()
        active.updatedAtMillis = timeProvider()
        persistSession(active)
        persistMessages(active)
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
        persistSession(active)
        persistMessages(active)
        return active
    }

    internal fun updateMessage(
        messageId: String,
        transform: (Message) -> Message,
    ): ConversationSession? {
        val active = getActiveSession() ?: return null
        val idx = active.messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return null
        val updated = transform(active.messages[idx])
        active.messages[idx] = updated
        active.updatedAtMillis = timeProvider()
        if (updated.deliveryState != com.example.blueheartv.model.MessageDeliveryState.STREAMING &&
            updated.deliveryState != com.example.blueheartv.model.MessageDeliveryState.SENDING
        ) {
            persistSession(active)
            persistMessages(active)
        }
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
        persistDeleteSessions(excess)
    }

    private fun persistSession(session: ConversationSession) {
        persist { it.upsertSession(session) }
    }

    private fun persistMessages(session: ConversationSession) {
        persist { it.replaceMessages(session) }
    }

    private fun persistDeleteSession(sessionId: String) {
        persist { it.deleteSession(sessionId) }
    }

    private fun persistDeleteSessions(sessionIds: Set<String>) {
        if (sessionIds.isEmpty()) return
        persist { it.deleteSessions(sessionIds) }
    }

    private fun persistDeleteAll() {
        persist { it.deleteAll() }
    }

    private fun persist(action: suspend (ChatSessionStore) -> Unit) {
        val store = store ?: return
        val scope = persistScope ?: return
        scope.launch(Dispatchers.IO) {
            action(store)
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

internal data class ConversationMutationSnapshot(
    val activeSessionId: String,
    val sessions: List<ConversationSession>,
)

private fun ConversationSession.deepCopy(): ConversationSession {
    return copy(messages = messages.toMutableList())
}
