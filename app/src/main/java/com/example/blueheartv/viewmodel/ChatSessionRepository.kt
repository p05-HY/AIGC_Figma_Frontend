package com.example.blueheartv.viewmodel

import com.example.blueheartv.db.ChatDao
import com.example.blueheartv.db.SessionEntity
import com.example.blueheartv.db.toDomain
import com.example.blueheartv.db.toEntity
import com.example.blueheartv.model.ChatHistory
import com.example.blueheartv.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
    private val dao: ChatDao,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val idProvider: () -> String = { java.util.UUID.randomUUID().toString() },
    persistDebounceMs: Long = 250L,
    scope: CoroutineScope,
) {
    internal val sessions = mutableListOf<ConversationSession>()
    internal var activeSessionId: String? = null
        private set

    private val persistRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch {
            persistRequests
                .debounce(persistDebounceMs)
                .collect { flushToRoom() }
        }
    }

    internal fun restore(): SessionRestoreResult {
        val rows = runBlocking { dao.getAllSessionsWithMessages() }
        if (rows.isEmpty()) {
            return SessionRestoreResult(activeSession = null, histories = emptyList())
        }

        sessions.clear()
        sessions.addAll(rows.map { row ->
            ConversationSession(
                id = row.session.id,
                title = row.session.title,
                updatedAtMillis = row.session.updatedAtMillis,
                isPinned = row.session.isPinned,
                messages = row.messages
                    .sortedBy { it.orderIndex }
                    .map { it.toDomain() }
                    .toMutableList(),
            )
        })
        activeSessionId = sessions.maxByOrNull { it.updatedAtMillis }?.id

        return SessionRestoreResult(
            activeSession = getActiveSession(),
            histories = buildHistories(),
        )
    }

    internal fun getActiveSession(): ConversationSession? {
        val id = activeSessionId ?: return null
        return sessions.firstOrNull { it.id == id }
    }

    internal fun ensureActiveSession(reset: Boolean): ConversationSession {
        if (reset || getActiveSession() == null) {
            val newSession = createSession()
            sessions.add(newSession)
            activeSessionId = newSession.id
            return newSession
        }
        return getActiveSession()!!
    }

    internal fun switchActive(sessionId: String): ConversationSession? {
        val target = sessions.firstOrNull { it.id == sessionId } ?: return null
        activeSessionId = target.id
        return target
    }

    internal fun startNewSession(): ConversationSession? {
        if (getActiveSession()?.messages.isNullOrEmpty()) return null
        val newSession = createSession()
        sessions.add(newSession)
        activeSessionId = newSession.id
        requestPersist()
        return newSession
    }

    fun renameSession(sessionId: String, newTitle: String): Boolean {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return false
        session.title = truncateTitle(newTitle)
        requestPersist()
        return true
    }

    fun togglePin(sessionId: String): Boolean {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return false
        session.isPinned = !session.isPinned
        requestPersist()
        return true
    }

    fun deleteSession(sessionId: String) {
        sessions.removeAll { it.id == sessionId }
        if (activeSessionId == sessionId) {
            activeSessionId = sessions.maxByOrNull { it.updatedAtMillis }?.id
        }
        requestPersist()
    }

    fun clearAll() {
        sessions.clear()
        activeSessionId = null
        requestPersist()
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
        requestPersist()
        return active
    }

    internal fun deleteAiMessage(messageId: String): ConversationSession? {
        val active = getActiveSession() ?: return null
        active.messages.removeAll { it.id == messageId }
        requestPersist()
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
        requestPersist()
        return active
    }

    fun requestPersist() {
        persistRequests.tryEmit(Unit)
    }

    fun persistImmediate() {
        runBlocking { flushToRoom() }
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
        requestPersist()
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

    private suspend fun flushToRoom() {
        val currentIds = sessions.map { it.id }.toSet()
        val storedIds = dao.getAllSessions().map { it.id }.toSet()
        val deletedIds = storedIds - currentIds
        deletedIds.forEach { dao.deleteSession(it) }

        sessions.forEach { session ->
            dao.upsertSession(
                SessionEntity(
                    id = session.id,
                    title = session.title,
                    updatedAtMillis = session.updatedAtMillis,
                    isPinned = session.isPinned,
                ),
            )
            dao.deleteMessagesForSession(session.id)
            if (session.messages.isNotEmpty()) {
                dao.upsertMessages(
                    session.messages.mapIndexed { index, msg -> msg.toEntity(session.id, index) },
                )
            }
        }

        val count = dao.sessionCount()
        if (count > MAX_SESSIONS) {
            val excess = dao.oldestSessionIds(count - MAX_SESSIONS)
            excess.forEach { dao.deleteSession(it) }
            sessions.removeAll { it.id in excess }
        }
    }

    private fun createSession(): ConversationSession {
        return ConversationSession(
            id = createId(),
            title = DEFAULT_SESSION_TITLE,
            updatedAtMillis = timeProvider(),
            messages = mutableListOf(),
        )
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
