package com.example.blueheartv.chat

import com.example.blueheartv.model.TraceEvent
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceRunStatus
import com.example.blueheartv.model.TraceStepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeTraceStreamTest {

    @Test
    fun traceStep_usesOnlyWhitelistedSafeFields() {
        val event = parseSafeStreamEvent(
            eventName = "trace.v1",
            payload = """
                {
                  "type":"trace.v1",
                  "version":1,
                  "runId":"run-1",
                  "threadId":"thread-1",
                  "eventId":"evt-1",
                  "seq":1,
                  "streamSeq":7,
                  "event":"step.upsert",
                  "rawResult":"token=secret",
                  "step":{
                    "stepId":"tool-1",
                    "kind":"tool",
                    "title":"观察屏幕",
                    "summary":"正在读取当前手机屏幕状态。",
                    "status":"running",
                    "visibleToUser":true,
                    "args":{"password":"raw"}
                  }
                }
            """.trimIndent(),
        ) as ChatStreamEvent.Trace

        val traceEvent = event.event as TraceEvent.StepUpsert
        assertEquals(7L, event.streamSeq)
        assertEquals("run-1", traceEvent.runId)
        assertEquals(TraceStepStatus.RUNNING, traceEvent.step.status)
        assertFalse(traceEvent.step.summary.contains("secret"))
    }

    @Test
    fun hiddenOrOversizedTraceIsRejected() {
        val hidden = parseSafeStreamEvent(
            "trace.v1",
            """{"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-1","seq":1,"event":"step.upsert","step":{"stepId":"tool-1","kind":"tool","title":"x","summary":"x","status":"running","visibleToUser":false}}""",
        )
        val tooLarge = parseSafeStreamEvent(
            "trace.v1",
            "{" + "x".repeat(4_100) + "}",
        )

        assertNull(hidden)
        assertNull(tooLarge)
    }

    @Test
    fun streamEof_isAnExplicitEventNotASuccessTerminal() {
        val event = parseSafeStreamEvent("stream.eof", """{"type":"stream.eof","streamSeq":5}""")

        assertTrue(event is ChatStreamEvent.StreamEof)
        assertEquals(5L, (event as ChatStreamEvent.StreamEof).streamSeq)
    }

    @Test
    fun streamStarted_isParsedAsTransportLifecycleOnly() {
        val event = parseSafeStreamEvent(
            "stream.started",
            """{"type":"stream.started","runId":"run-1","threadId":"thread-1","streamSeq":1,"message":"已接收请求，正在连接 Agent。"}""",
        ) as ChatStreamEvent.StreamStarted

        assertEquals("run-1", event.runId)
        assertEquals(1L, event.streamSeq)
        assertEquals("已接收请求，正在连接 Agent。", event.message)
    }

    @Test
    fun streamSeq_isRetainedForAllSafeFacadeEvents() {
        val assistant = parseSafeStreamEvent(
            "assistant.delta",
            """{"type":"assistant.delta","chunk":"答案","streamSeq":2}""",
        ) as ChatStreamEvent.TextDelta
        val progress = parseSafeStreamEvent(
            "task_progress",
            """{"type":"task_progress","label":"观察屏幕","status":"running","phase":"observe","streamSeq":3}""",
        ) as ChatStreamEvent.TaskProgress
        val error = parseSafeStreamEvent(
            "stream.error",
            """{"type":"stream.error","message":"断开","retryable":true,"terminalStatus":"failed","terminalReason":"upstream_ended_without_terminal","cancelSource":"server_timeout","streamSeq":4}""",
        ) as ChatStreamEvent.Error
        val heartbeat = parseSafeStreamEvent(
            "stream.heartbeat",
            """{"type":"stream.heartbeat","runId":"run-1","streamSeq":5}""",
        ) as ChatStreamEvent.Heartbeat

        assertEquals(2L, assistant.streamSeq)
        assertEquals(3L, progress.streamSeq)
        assertEquals(4L, error.streamSeq)
        assertEquals("failed", error.terminalStatus)
        assertEquals("upstream_ended_without_terminal", error.terminalReason)
        assertEquals("server_timeout", error.cancelSource)
        assertEquals(5L, heartbeat.streamSeq)
        assertEquals("run-1", heartbeat.runId)
    }

    @Test
    fun runTerminal_keepsReasonAndCancelSourceWhenProvidedBySafeFacade() {
        val event = parseSafeStreamEvent(
            "trace.v1",
            """{"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-1","seq":1,"event":"run.terminal","status":"failed","reason":"server_timeout","cancelSource":"frontend_timeout"}""",
        ) as ChatStreamEvent.Trace

        val terminal = event.event as TraceEvent.RunTerminal
        assertEquals(TraceRunStatus.FAILED, terminal.status)
        assertEquals("server_timeout", terminal.reason)
        assertEquals("frontend_timeout", terminal.cancelSource)
    }

    @Test
    fun stepDetailAppend_parsesSafeDetailAndStreamSeq() {
        val event = parseSafeStreamEvent(
            "trace.v1",
            """
                {
                  "type":"trace.v1",
                  "version":1,
                  "runId":"run-1",
                  "eventId":"evt-detail-1",
                  "seq":2,
                  "streamSeq":8,
                  "event":"step.detail.append",
                  "stepId":"step-1",
                  "rawResult":{"token":"secret"},
                  "detail":{
                    "detailId":"detail-1",
                    "kind":"tool_result",
                    "title":"工具结果",
                    "text":"已完成屏幕观察，当前页面显示为微信首页。",
                    "visibleToUser":true,
                    "args":{"password":"raw"}
                  }
                }
            """.trimIndent(),
        ) as ChatStreamEvent.Trace
        val traceEvent = event.event as TraceEvent.StepDetailAppend

        assertEquals(8L, event.streamSeq)
        assertEquals("step-1", traceEvent.stepId)
        assertEquals(8L, traceEvent.streamSeq)
        assertEquals("detail-1", traceEvent.detail.id)
        assertEquals(TraceDetailKind.TOOL_RESULT, traceEvent.detail.kind)
        assertEquals("工具结果", traceEvent.detail.title)
        assertFalse(traceEvent.detail.text.contains("secret"))
    }

    @Test
    fun invalidStepDetailAppend_isRejected() {
        val hidden = parseSafeStreamEvent(
            "trace.v1",
            """{"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-1","seq":1,"event":"step.detail.append","stepId":"step-1","detail":{"detailId":"detail-1","kind":"warning","title":"需要确认","text":"该操作需要你确认。","visibleToUser":false}}""",
        )
        val unknownKind = parseSafeStreamEvent(
            "trace.v1",
            """{"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-2","seq":2,"event":"step.detail.append","stepId":"step-1","detail":{"detailId":"detail-2","kind":"raw_result","title":"Raw","text":"raw","visibleToUser":true}}""",
        )
        val missingId = parseSafeStreamEvent(
            "trace.v1",
            """{"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-3","seq":3,"event":"step.detail.append","stepId":"step-1","detail":{"kind":"warning","title":"需要确认","text":"该操作需要你确认。","visibleToUser":true}}""",
        )

        assertNull(hidden)
        assertNull(unknownKind)
        assertNull(missingId)
    }
}
