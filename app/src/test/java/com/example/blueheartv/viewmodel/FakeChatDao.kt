package com.example.blueheartv.viewmodel

import com.example.blueheartv.db.ChatDao
import com.example.blueheartv.db.MessageEntity
import com.example.blueheartv.db.SessionEntity
import com.example.blueheartv.db.SessionWithMessages

class FakeChatDao : ChatDao {
    private val sessions = mutableListOf<SessionEntity>()
    private val messages = mutableListOf<MessageEntity>()

    override suspend fun getAllSessionsWithMessages(): List<SessionWithMessages> {
        return sessions.map { session ->
            SessionWithMessages(
                session = session,
                messages = messages.filter { it.sessionId == session.id },
            )
        }
    }

    override suspend fun getSessionWithMessages(sessionId: String): SessionWithMessages? {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return null
        return SessionWithMessages(session, messages.filter { it.sessionId == sessionId })
    }

    override suspend fun getAllSessions(): List<SessionEntity> = sessions.toList()

    override suspend fun upsertSession(session: SessionEntity) {
        sessions.removeAll { it.id == session.id }
        sessions.add(session)
    }

    override suspend fun deleteSession(sessionId: String) {
        sessions.removeAll { it.id == sessionId }
        messages.removeAll { it.sessionId == sessionId }
    }

    override suspend fun deleteAllSessions() {
        sessions.clear()
        messages.clear()
    }

    override suspend fun upsertMessages(messages: List<MessageEntity>) {
        val ids = messages.map { it.id }.toSet()
        this.messages.removeAll { it.id in ids }
        this.messages.addAll(messages)
    }

    override suspend fun deleteMessages(ids: List<String>) {
        messages.removeAll { it.id in ids }
    }

    override suspend fun deleteMessagesForSession(sessionId: String) {
        messages.removeAll { it.sessionId == sessionId }
    }

    override suspend fun getMessagesForSession(sessionId: String): List<MessageEntity> {
        return messages.filter { it.sessionId == sessionId }.sortedBy { it.orderIndex }
    }

    override suspend fun sessionCount(): Int = sessions.size

    override suspend fun oldestSessionIds(count: Int): List<String> {
        return sessions.sortedBy { it.updatedAtMillis }.take(count).map { it.id }
    }
}
