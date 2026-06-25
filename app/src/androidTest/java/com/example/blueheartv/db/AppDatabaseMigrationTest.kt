package com.example.blueheartv.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blueheartv.viewmodel.ChatSessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.name,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationAndFirstRead_removeLegacyRawToolData() = runBlocking {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO sessions (id, title, updatedAtMillis, isPinned) VALUES ('session-1', '测试', 1, 0)")
            execSQL(
                """
                INSERT INTO messages (id, sessionId, content, isUser, deliveryState, errorMessage, toolCallsJson, orderIndex)
                VALUES ('message-1', 'session-1', '你好', 0, 'COMPLETED', NULL,
                '[{"label":"观察屏幕","status":"COMPLETED","args":"token=secret","result":"{\\"package\\":\\"com.secret.app\\"}","error":"password=123","message":"已完成观察","phase":"phone_tool"}]', 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true, AppDatabase.MIGRATION_1_2).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        try {
            val sessions = ChatSessionStore(database.chatDao()).loadSessions(limit = 10)
            val message = sessions.single().messages.single()
            val persisted = database.chatDao().getMessagesForSession("session-1").single()

            assertTrue(persisted.privacySanitized)
            assertFalse(message.toolCalls.toString().contains("token"))
            assertFalse(message.toolCalls.toString().contains("com.secret.app"))
            assertFalse(persisted.toolCallsJson.orEmpty().contains("args"))
            assertFalse(persisted.toolCallsJson.orEmpty().contains("result"))
            assertFalse(persisted.toolCallsJson.orEmpty().contains("error"))
            assertFalse(persisted.toolCallsJson.orEmpty().contains("token"))
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB_NAME = "trace-migration-test"
    }
}
