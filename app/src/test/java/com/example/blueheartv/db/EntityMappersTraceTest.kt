package com.example.blueheartv.db

import com.example.blueheartv.model.AssistantTrace
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus
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
}
