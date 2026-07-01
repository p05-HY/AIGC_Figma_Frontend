package com.example.blueheartv.db

import com.example.blueheartv.model.AssistantTrace
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityMappersTraceTest {

    @Test
    fun traceDetails_roundTripThroughTraceJson() {
        val trace = AssistantTrace(
            runId = "run-1",
            steps = listOf(
                TraceStep(
                    id = "step-1",
                    parentId = "tool-parent",
                    kind = "tool",
                    title = "观察屏幕",
                    summary = "正在读取当前手机屏幕状态。",
                    status = TraceStepStatus.SUCCEEDED,
                    details = listOf(
                        TraceDetail(
                            id = "detail-1",
                            kind = TraceDetailKind.OBSERVATION,
                            title = "观察结果",
                            text = "已获取当前屏幕概要。",
                        ),
                    ),
                ),
            ),
        )
        val message = Message(id = "m1", content = "ok", isUser = false, trace = trace)

        val restored = message.toEntity(sessionId = "s1", orderIndex = 0).toDomain()

        assertEquals("tool-parent", restored.trace!!.steps.single().parentId)
        assertEquals(trace.steps.single().details, restored.trace.steps.single().details)
    }

    @Test
    fun oldTraceJsonWithoutDetails_stillReadsWithEmptyDetails() {
        val entity = MessageEntity(
            id = "m1",
            sessionId = "s1",
            content = "ok",
            isUser = false,
            deliveryState = "COMPLETED",
            traceJson = """
                {
                  "runId":"run-1",
                  "steps":[
                    {
                      "id":"step-1",
                      "kind":"tool",
                      "title":"观察屏幕",
                      "summary":"正在读取当前手机屏幕状态。",
                      "status":"SUCCEEDED",
                      "visibleToUser":true
                    }
                  ],
                  "runStatus":"RUNNING",
                  "lastSeq":1,
                  "seenEventIds":["evt-1"],
                  "hasTerminal":false
                }
            """.trimIndent(),
            orderIndex = 0,
        )

        val trace = entity.toDomain().trace!!

        assertTrue(trace.steps.single().details.isEmpty())
    }

    @Test
    fun streamWatermarkAndTerminalStatus_roundTripThroughMessageEntity() {
        val message = Message(
            id = "m1",
            content = "部分回复",
            isUser = false,
            deliveryState = MessageDeliveryState.FAILED,
            lastReceivedStreamSeq = 42L,
            terminalStatus = "interrupted",
        )

        val entity = message.toEntity(sessionId = "s1", orderIndex = 0)
        val restored = entity.toDomain()

        assertEquals(42L, entity.lastReceivedStreamSeq)
        assertEquals("interrupted", entity.terminalStatus)
        assertEquals(42L, restored.lastReceivedStreamSeq)
        assertEquals("interrupted", restored.terminalStatus)
    }

    @Test
    fun migration3To4_addsStreamWatermarkAndTerminalColumns() {
        val statements = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args?.firstOrNull() is String) {
                statements += args.first() as String
            }
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Void.TYPE -> Unit
                String::class.java -> ""
                else -> null
            }
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_3_4.migrate(db)

        assertTrue(
            statements.any {
                it == "ALTER TABLE messages ADD COLUMN lastReceivedStreamSeq INTEGER NOT NULL DEFAULT 0"
            },
        )
        assertTrue(
            statements.any {
                it == "ALTER TABLE messages ADD COLUMN terminalStatus TEXT"
            },
        )
    }
}
