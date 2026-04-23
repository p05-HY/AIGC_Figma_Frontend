package com.example.blueheartv.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAtMillis: Long,
    val isPinned: Boolean = false,
)
