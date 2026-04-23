package com.example.blueheartv.db

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithMessages(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val messages: List<MessageEntity>,
)
