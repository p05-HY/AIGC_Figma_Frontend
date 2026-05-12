package com.example.blueheartv.viewmodel

import com.example.blueheartv.db.ChatDao
import com.example.blueheartv.db.SessionEntity
import com.example.blueheartv.db.toDomain
import com.example.blueheartv.db.toEntity

class ChatSessionStore(
    private val dao: ChatDao,
) {
    suspend fun loadSessions(limit: Int): List<ConversationSession> {
        val rows = dao.getAllSessionsWithMessages()
            .sortedByDescending { it.session.updatedAtMillis }
            .take(limit)

        return rows.map { row ->
            val messages = row.messages.sortedBy { it.orderIndex }.map { it.toDomain() }
            ConversationSession(
                id = row.session.id,
                title = row.session.title,
                updatedAtMillis = row.session.updatedAtMillis,
                isPinned = row.session.isPinned,
                messages = messages.toMutableList(),
            )
        }
    }

    suspend fun upsertSession(session: ConversationSession) {
        dao.upsertSession(
            SessionEntity(
                id = session.id,
                title = session.title,
                updatedAtMillis = session.updatedAtMillis,
                isPinned = session.isPinned,
            )
        )
    }

    suspend fun replaceMessages(session: ConversationSession) {
        dao.deleteMessagesForSession(session.id)
        val entities = session.messages.mapIndexed { index, message ->
            message.toEntity(session.id, index)
        }
        if (entities.isNotEmpty()) {
            dao.upsertMessages(entities)
        }
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    suspend fun deleteSessions(sessionIds: Collection<String>) {
        sessionIds.forEach { dao.deleteSession(it) }
    }

    suspend fun deleteAll() {
        dao.deleteAllSessions()
    }
}
