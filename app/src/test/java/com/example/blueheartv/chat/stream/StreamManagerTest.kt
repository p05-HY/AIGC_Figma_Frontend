package com.example.blueheartv.chat.stream

import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.model.TraceEvent
import com.example.blueheartv.model.TraceRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamManagerTest {

    @Test
    fun start_createsActiveSessionWithStableIdsAndTimestamps() {
        var now = 1_000L
        val manager = StreamManager(nowMillis = { now })

        val session = manager.start(
            threadId = "thread-1",
            assistantMessageId = "assistant-1",
            runId = "run-local-1",
        )

        assertSame(session, manager.activeSession)
        assertEquals("thread-1", session.threadId)
        assertEquals("assistant-1", session.assistantMessageId)
        assertEquals("run-local-1", session.runId)
        assertEquals("run-local-1", session.accumulatorRunId)
        assertEquals(1_000L, session.startedAtMillis)
        assertEquals(1_000L, session.lastEventAtMillis)
    }

    @Test
    fun acceptEvent_dropsMismatchedRunIdButKeepsMatchingRun() {
        val manager = StreamManager(nowMillis = { 1_000L })
        val session = manager.start("thread-1", "assistant-1", "run-1")

        val started = manager.acceptEvent(
            session,
            ChatStreamEvent.StreamStarted(
                runId = "run-1",
                threadId = "thread-1",
                message = "started",
                streamSeq = 1,
            ),
        )
        val stale = manager.acceptEvent(
            session,
            ChatStreamEvent.Trace(
                TraceEvent.RunTerminal(
                    runId = "run-stale",
                    eventId = "evt-stale",
                    seq = 1,
                    status = TraceRunStatus.SUCCEEDED,
                ),
                streamSeq = 2,
            ),
        )

        assertTrue(started is StreamEventDecision.Accepted)
        assertTrue(stale is StreamEventDecision.Dropped)
        assertEquals(StreamDropReason.RUN_MISMATCH, (stale as StreamEventDecision.Dropped).reason)
        assertEquals("run-1", session.runId)
    }

    @Test
    fun acceptEvent_dropsMismatchedThreadIdEvenWhenRunIdMatches() {
        val manager = StreamManager(nowMillis = { 1_000L })
        val session = manager.start("thread-1", "assistant-1", "run-1")

        val stale = manager.acceptEvent(
            session,
            ChatStreamEvent.TextDelta(
                chunk = "old",
                runId = "run-1",
                threadId = "thread-stale",
                streamSeq = 1,
            ),
        )

        assertTrue(stale is StreamEventDecision.Dropped)
        assertEquals(StreamDropReason.THREAD_MISMATCH, (stale as StreamEventDecision.Dropped).reason)
        assertEquals(0L, session.receivedStreamSeq)
    }

    @Test
    fun acceptEvent_recordsBackendRunIdFromSafeEvents() {
        val manager = StreamManager(nowMillis = { 1_000L })
        val session = manager.start("thread-1", "assistant-1", "run-1")

        val accepted = manager.acceptEvent(
            session,
            ChatStreamEvent.TextDelta(
                chunk = "ok",
                runId = "run-1",
                threadId = "thread-1",
                backendRunId = "backend-1",
                streamSeq = 1,
            ),
        )

        assertTrue(accepted is StreamEventDecision.Accepted)
        assertEquals("backend-1", session.backendRunId)
    }

    @Test
    fun acceptEvent_dropsDuplicateDeltaButAcceptsTerminalWithDuplicateStreamSeq() {
        val manager = StreamManager(nowMillis = { 1_000L })
        val session = manager.start("thread-1", "assistant-1", "run-1")

        val firstDelta = manager.acceptEvent(session, ChatStreamEvent.TextDelta("ok", streamSeq = 2))
        val duplicateDelta = manager.acceptEvent(session, ChatStreamEvent.TextDelta("ok", streamSeq = 2))
        val terminal = manager.acceptEvent(
            session,
            ChatStreamEvent.Trace(
                TraceEvent.RunTerminal(
                    runId = "run-1",
                    eventId = "evt-terminal",
                    seq = 1,
                    status = TraceRunStatus.SUCCEEDED,
                ),
                streamSeq = 2,
            ),
        )

        assertTrue(firstDelta is StreamEventDecision.Accepted)
        assertTrue(duplicateDelta is StreamEventDecision.Dropped)
        assertEquals(StreamDropReason.STREAM_SEQ, (duplicateDelta as StreamEventDecision.Dropped).reason)
        assertTrue(terminal is StreamEventDecision.Accepted)
        assertEquals("succeeded", session.terminalStatus)
        assertEquals(2L, session.receivedStreamSeq)
    }

    @Test
    fun heartbeatUpdatesLivenessWithoutChangingTerminalStatus() {
        var now = 1_000L
        val manager = StreamManager(nowMillis = { now })
        val session = manager.start("thread-1", "assistant-1", "run-1")

        now = 2_000L
        val heartbeat = manager.acceptEvent(session, ChatStreamEvent.Heartbeat(runId = "run-1", streamSeq = 1))

        assertTrue(heartbeat is StreamEventDecision.Accepted)
        assertEquals(2_000L, session.lastHeartbeatAtMillis)
        assertEquals(2_000L, session.lastEventAtMillis)
        assertEquals(null, session.terminalStatus)
    }

    @Test
    fun streamEofWithoutTerminalRequestsInterruptedLifecycle() {
        val manager = StreamManager(nowMillis = { 1_000L })
        val session = manager.start("thread-1", "assistant-1", "run-1")

        val decision = manager.onStreamEof(session)

        assertEquals(StreamLifecycleDecision.Interrupted(StreamInterruptReason.EOF_WITHOUT_TERMINAL), decision)
    }

    @Test
    fun streamEofAfterTerminalOnlyRequestsCleanup() {
        val manager = StreamManager(nowMillis = { 1_000L })
        val session = manager.start("thread-1", "assistant-1", "run-1")
        manager.acceptEvent(
            session,
            ChatStreamEvent.Trace(
                TraceEvent.RunTerminal(
                    runId = "run-1",
                    eventId = "evt-terminal",
                    seq = 1,
                    status = TraceRunStatus.SUCCEEDED,
                ),
                streamSeq = 1,
            ),
        )

        val decision = manager.onStreamEof(session)

        assertEquals(StreamLifecycleDecision.Cleanup("succeeded"), decision)
    }

    @Test
    fun providerExceptionAfterTerminalOnlyRequestsCleanup() {
        val manager = StreamManager(nowMillis = { 1_000L })
        val session = manager.start("thread-1", "assistant-1", "run-1")
        manager.acceptEvent(
            session,
            ChatStreamEvent.Trace(
                TraceEvent.RunTerminal(
                    runId = "run-1",
                    eventId = "evt-terminal",
                    seq = 1,
                    status = TraceRunStatus.SUCCEEDED,
                ),
                streamSeq = 1,
            ),
        )

        val decision = manager.onProviderException(session)

        assertEquals(StreamLifecycleDecision.Cleanup("succeeded"), decision)
    }

    @Test
    fun heartbeatTimeoutAfterTerminalIsIgnoredButWithoutActivityInterrupts() {
        var now = 1_000L
        val manager = StreamManager(nowMillis = { now })
        val session = manager.start("thread-1", "assistant-1", "run-1")
        val version = session.version

        assertEquals(
            StreamLifecycleDecision.Interrupted(StreamInterruptReason.HEARTBEAT_TIMEOUT),
            manager.onHeartbeatTimeout(session, capturedVersion = version),
        )

        manager.acceptEvent(
            session,
            ChatStreamEvent.Trace(
                TraceEvent.RunTerminal(
                    runId = "run-1",
                    eventId = "evt-terminal",
                    seq = 1,
                    status = TraceRunStatus.SUCCEEDED,
                ),
                streamSeq = 1,
            ),
        )
        now = 2_000L

        assertEquals(
            StreamLifecycleDecision.Ignore,
            manager.onHeartbeatTimeout(session, capturedVersion = session.version),
        )
    }

    @Test
    fun cancellationAndFinishMoveSessionThroughManagerState() {
        val manager = StreamManager(nowMillis = { 1_000L })
        val session = manager.start("thread-1", "assistant-1", "run-1")

        manager.beginCancellation(session)

        assertSame(session, manager.activeSession)
        assertSame(session, manager.pendingCancellation)
        assertTrue(manager.owns(session))

        val cleared = manager.finish(session)

        assertTrue(cleared)
        assertEquals(null, manager.activeSession)
        assertEquals(null, manager.pendingCancellation)
        assertFalse(manager.owns(session))
    }
}
