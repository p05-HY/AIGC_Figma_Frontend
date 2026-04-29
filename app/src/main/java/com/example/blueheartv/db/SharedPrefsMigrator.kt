package com.example.blueheartv.db

import android.content.Context
import androidx.core.content.edit
import com.example.blueheartv.chat.ChatSessionStore

object SharedPrefsMigrator {

    private const val PREFS_NAME = "blueheartv_chat_sessions"
    private const val MIGRATION_DONE_KEY = "room_migration_done"

    suspend fun migrateIfNeeded(context: Context, dao: ChatDao) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATION_DONE_KEY, false)) return
        if (!prefs.contains("snapshot_v1")) {
            prefs.edit { putBoolean(MIGRATION_DONE_KEY, true) }
            return
        }

        val snapshot = ChatSessionStore.load(context)
        if (snapshot.sessions.isNotEmpty()) {
            snapshot.sessions.forEach { session ->
                dao.upsertSession(
                    SessionEntity(
                        id = session.id,
                        title = session.title,
                        updatedAtMillis = session.updatedAtMillis,
                        isPinned = session.isPinned,
                    ),
                )
                if (session.messages.isNotEmpty()) {
                    dao.upsertMessages(
                        session.messages.mapIndexed { index, msg ->
                            msg.toEntity(session.id, index)
                        },
                    )
                }
            }
        }

        prefs.edit {
            remove("snapshot_v1")
                .putBoolean(MIGRATION_DONE_KEY, true)
        }
    }
}
