package com.example.blueheartv.viewmodel

import com.example.blueheartv.db.ChatDao
import com.example.blueheartv.db.MessageEntity
import com.example.blueheartv.db.SessionEntity
import com.example.blueheartv.db.SessionWithMessages

class FakeChatDao : ChatDao {
    private val lock = Any()
    private val sessions = mutableListOf<SessionEntity>()
    private val messages = mutableListOf<MessageEntity>()
    var upsertSessionCalls: Int = 0
        private set
    var replaceMessagesCalls: Int = 0
        private set

    override suspend fun getAllSessionsWithMessages(): List<SessionWithMessages> {
        return synchronized(lock) {
            sessions.map { session ->
            SessionWithMessages(
                session = session,
                messages = messages.filter { it.sessionId == session.id },
            )
            }
        }
    }

    override suspend fun getSessionWithMessages(sessionId: String): SessionWithMessages? {
        return synchronized(lock) {
            val session = sessions.firstOrNull { it.id == sessionId } ?: return@synchronized null
            SessionWithMessages(session, messages.filter { it.sessionId == sessionId })
        }
    }

    override suspend fun getAllSessions(): List<SessionEntity> = synchronized(lock) { sessions.toList() }

    override suspend fun upsertSession(session: SessionEntity) {
        synchronized(lock) {
            upsertSessionCalls += 1
            sessions.removeAll { it.id == session.id }
            sessions.add(session)
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        synchronized(lock) {
            sessions.removeAll { it.id == sessionId }
            messages.removeAll { it.sessionId == sessionId }
        }
    }

    override suspend fun deleteAllSessions() {
        synchronized(lock) {
            sessions.clear()
            messages.clear()
        }
    }

    override suspend fun upsertMessages(messages: List<MessageEntity>) {
        synchronized(lock) {
            val ids = messages.map { it.id }.toSet()
            this.messages.removeAll { it.id in ids }
            this.messages.addAll(messages)
        }
    }

    override suspend fun deleteMessages(ids: List<String>) {
        synchronized(lock) {
            messages.removeAll { it.id in ids }
        }
    }

    override suspend fun deleteMessagesForSession(sessionId: String) {
        synchronized(lock) {
            replaceMessagesCalls += 1
            messages.removeAll { it.sessionId == sessionId }
        }
    }

    override suspend fun getMessagesForSession(sessionId: String): List<MessageEntity> {
        return synchronized(lock) {
            messages.filter { it.sessionId == sessionId }.sortedBy { it.orderIndex }
        }
    }

    override suspend fun sessionCount(): Int = synchronized(lock) { sessions.size }

    override suspend fun oldestSessionIds(count: Int): List<String> {
        return synchronized(lock) {
            sessions.sortedBy { it.updatedAtMillis }.take(count).map { it.id }
        }
    }
}
