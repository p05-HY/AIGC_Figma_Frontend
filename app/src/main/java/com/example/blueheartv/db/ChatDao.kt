package com.example.blueheartv.db

import androidx.room.*

@Dao
interface ChatDao {

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY isPinned DESC, updatedAtMillis DESC")
    suspend fun getAllSessionsWithMessages(): List<SessionWithMessages>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionWithMessages(sessionId: String): SessionWithMessages?

    @Query("SELECT * FROM sessions ORDER BY isPinned DESC, updatedAtMillis DESC")
    suspend fun getAllSessions(): List<SessionEntity>

    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Upsert
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessages(ids: List<String>)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getMessagesForSession(sessionId: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun sessionCount(): Int

    @Query("SELECT id FROM sessions ORDER BY updatedAtMillis ASC LIMIT :count")
    suspend fun oldestSessionIds(count: Int): List<String>
}
