package com.example.blueheartv.viewmodel

import com.example.blueheartv.model.AssistantTrace
import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceEvent
import com.example.blueheartv.model.TraceRunStatus
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceReducerTest {

    @Test
    fun failedStepDoesNotFailRun() {
        val trace = reduceTrace(
            null,
            stepEvent(seq = 1, status = TraceStepStatus.FAILED),
        )

        assertEquals(TraceRunStatus.RUNNING, trace.runStatus)
        assertEquals(TraceStepStatus.FAILED, trace.steps.single().status)
    }

    @Test
    fun failedRunTerminalChangesRunOnlyAfterTerminalEvent() {
        val running = reduceTrace(null, stepEvent(seq = 1, status = TraceStepStatus.FAILED))
        val failed = reduceTrace(running, terminalEvent(seq = 2, status = TraceRunStatus.FAILED))

        assertEquals(TraceRunStatus.FAILED, failed.runStatus)
        assertTrue(failed.hasTerminal)
    }

    @Test
    fun terminalStatusTransitionsCoverSucceededCanceledInterruptedAndWaiting() {
        val running = reduceTrace(null, stepEvent(seq = 1))

        listOf(
            TraceRunStatus.SUCCEEDED,
            TraceRunStatus.CANCELLED,
            TraceRunStatus.INTERRUPTED,
            TraceRunStatus.WAITING_FOR_USER,
        ).forEachIndexed { index, status ->
            val terminal = reduceTrace(
                running.copy(seenEventIds = emptySet()),
                terminalEvent(seq = (index + 2).toLong(), status = status),
            )

            assertEquals(status, terminal.runStatus)
            assertTrue(terminal.hasTerminal)
        }
    }

    @Test
    fun interruptTrace_doesNotOverrideTerminalStatus() {
        val succeeded = reduceTrace(
            reduceTrace(null, stepEvent(seq = 1)),
            terminalEvent(seq = 2, status = TraceRunStatus.SUCCEEDED),
        )

        val afterEof = interruptTrace(succeeded)

        assertEquals(TraceRunStatus.SUCCEEDED, afterEof.runStatus)
        assertTrue(afterEof.hasTerminal)
    }

    @Test
    fun duplicateEventAndStaleSequenceAreIgnored() {
        val first = reduceTrace(null, stepEvent(seq = 1, eventId = "evt-1"))
        val duplicate = reduceTrace(first, stepEvent(seq = 1, eventId = "evt-1"))
        val stale = reduceTrace(first, stepEvent(seq = 1, eventId = "evt-2"))

        assertEquals(first, duplicate)
        assertEquals(first, stale)
    }

    @Test
    fun eofWithoutTerminalMarksTraceInterrupted() {
        val running = reduceTrace(null, stepEvent(seq = 1))
        val interrupted = interruptTrace(running)

        assertEquals(TraceRunStatus.INTERRUPTED, interrupted.runStatus)
        assertFalse(interrupted.hasTerminal)
    }

    @Test
    fun detailAppend_addsDetailToExistingStepOnly() {
        val running = reduceTrace(null, stepEvent(seq = 1))
        val withDetail = reduceTrace(running, detailEvent(seq = 2))
        val missingStep = reduceTrace(withDetail, detailEvent(seq = 3, stepId = "missing-step"))

        assertEquals(1, withDetail.steps.single().details.size)
        assertEquals("detail-2", withDetail.steps.single().details.single().id)
        assertEquals(withDetail.steps, missingStep.steps)
        assertEquals(3L, missingStep.lastSeq)
        assertTrue("evt-detail-3" in missingStep.seenEventIds)
    }

    @Test
    fun duplicateDetailAndStaleDetailEventsAreIgnored() {
        val running = reduceTrace(null, stepEvent(seq = 1))
        val withDetail = reduceTrace(running, detailEvent(seq = 2, detailId = "detail-1"))
        val duplicateDetail = reduceTrace(withDetail, detailEvent(seq = 3, detailId = "detail-1"))
        val stale = reduceTrace(withDetail, detailEvent(seq = 2, eventId = "evt-new", detailId = "detail-2"))

        assertEquals(1, duplicateDetail.steps.single().details.size)
        assertEquals(withDetail, stale)
    }

    @Test
    fun detailAppendDoesNotChangeRunStatus() {
        val running = reduceTrace(null, stepEvent(seq = 1))
        val withDetail = reduceTrace(
            running,
            detailEvent(seq = 2, kind = TraceDetailKind.ERROR),
        )

        assertEquals(TraceRunStatus.RUNNING, withDetail.runStatus)
        assertFalse(withDetail.hasTerminal)
    }

    @Test
    fun parentAndChildStepsCoexistWithChildDetailsAndFailureDoesNotFailRun() {
        val parent = reduceTrace(
            null,
            stepEvent(
                seq = 1,
                stepId = "tool-parent",
                title = "执行手机操作",
            ),
        )
        val child = reduceTrace(
            parent,
            stepEvent(
                seq = 2,
                stepId = "phone-child-1",
                parentId = "tool-parent",
                title = "点击屏幕",
            ),
        )
        val withDetail = reduceTrace(
            child,
            detailEvent(seq = 3, stepId = "phone-child-1", detailId = "detail-child-1"),
        )
        val failedChild = reduceTrace(
            withDetail,
            stepEvent(
                seq = 4,
                stepId = "phone-child-1",
                parentId = "tool-parent",
                title = "点击屏幕",
                status = TraceStepStatus.FAILED,
            ),
        )

        assertEquals(listOf("tool-parent", "phone-child-1"), failedChild.steps.map { it.id })
        val childStep = failedChild.steps.single { it.id == "phone-child-1" }
        assertEquals("tool-parent", childStep.parentId)
        assertEquals(TraceStepStatus.FAILED, childStep.status)
        assertEquals(1, childStep.details.size)
        assertEquals("detail-child-1", childStep.details.single().id)
        assertEquals(TraceRunStatus.RUNNING, failedChild.runStatus)
        assertFalse(failedChild.hasTerminal)
    }

    private fun stepEvent(
        seq: Long,
        eventId: String = "evt-$seq",
        stepId: String = "step-1",
        parentId: String? = null,
        title: String = "观察屏幕",
        status: TraceStepStatus = TraceStepStatus.RUNNING,
    ): TraceEvent {
        return TraceEvent.StepUpsert(
            runId = "run-1",
            eventId = eventId,
            seq = seq,
            step = TraceStep(
                id = stepId,
                parentId = parentId,
                kind = "tool",
                title = title,
                summary = "正在读取当前手机屏幕状态。",
                status = status,
            ),
        )
    }

    private fun terminalEvent(seq: Long, status: TraceRunStatus): TraceEvent {
        return TraceEvent.RunTerminal(
            runId = "run-1",
            eventId = "evt-terminal-$seq",
            seq = seq,
            status = status,
        )
    }

    private fun detailEvent(
        seq: Long,
        eventId: String = "evt-detail-$seq",
        stepId: String = "step-1",
        detailId: String = "detail-$seq",
        kind: TraceDetailKind = TraceDetailKind.TOOL_RESULT,
    ): TraceEvent {
        return TraceEvent.StepDetailAppend(
            runId = "run-1",
            eventId = eventId,
            seq = seq,
            streamSeq = seq + 10,
            stepId = stepId,
            detail = TraceDetail(
                id = detailId,
                kind = kind,
                title = "工具结果",
                text = "操作已执行。",
            ),
        )
    }
}
