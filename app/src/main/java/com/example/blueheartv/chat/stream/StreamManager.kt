package com.example.blueheartv.chat.stream

import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.model.TraceEvent
import com.example.blueheartv.model.TraceRunStatus
import java.util.UUID

enum class StreamDropReason {
    STALE_SESSION,
    THREAD_MISMATCH,
    RUN_MISMATCH,
    STREAM_SEQ,
}

enum class StreamInterruptReason {
    EOF_WITHOUT_TERMINAL,
    HEARTBEAT_TIMEOUT,
    PROVIDER_EXCEPTION,
}

sealed interface StreamLifecycleDecision {
    data object Ignore : StreamLifecycleDecision
    data class Cleanup(val terminalStatus: String?) : StreamLifecycleDecision
    data class Interrupted(val reason: StreamInterruptReason) : StreamLifecycleDecision
}

sealed interface StreamEventDecision {
    data class Accepted(
        val session: StreamSession,
        val eventType: String,
        val streamSeq: Long?,
        val terminalStatus: String? = null,
    ) : StreamEventDecision

    data class Dropped(
        val session: StreamSession,
        val reason: StreamDropReason,
        val eventType: String,
        val eventRunId: String?,
        val streamSeq: Long?,
        val lastReceivedStreamSeq: Long,
    ) : StreamEventDecision
}

class StreamManager(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val runIdFactory: () -> String = { "run_${UUID.randomUUID().toString().replace("-", "")}" },
) {
    var activeSession: StreamSession? = null
        private set
    var pendingCancellation: StreamSession? = null
        private set

    fun start(
        threadId: String,
        assistantMessageId: String,
        runId: String = runIdFactory(),
    ): StreamSession {
        val now = nowMillis()
        return StreamSession(
            threadId = threadId,
            assistantMessageId = assistantMessageId,
            runId = runId,
            accumulatorRunId = runId,
            startedAtMillis = now,
        ).also { session ->
            activeSession = session
            pendingCancellation = null
        }
    }

    fun owns(session: StreamSession): Boolean =
        activeSession === session || pendingCancellation === session

    fun activeOrPendingSession(): StreamSession? =
        activeSession ?: pendingCancellation

    fun hasSessionOnThread(threadId: String): Boolean =
        activeSession?.threadId == threadId || pendingCancellation?.threadId == threadId

    fun beginCancellation(session: StreamSession) {
        if (owns(session)) {
            pendingCancellation = session
        }
    }

    fun clearPendingCancellation(session: StreamSession? = null) {
        if (session == null || pendingCancellation === session) {
            pendingCancellation = null
        }
    }

    fun finish(session: StreamSession): Boolean {
        var cleared = false
        if (activeSession === session) {
            activeSession = null
            cleared = true
        }
        if (pendingCancellation === session) {
            pendingCancellation = null
            cleared = true
        }
        return cleared
    }

    fun clearAll() {
        activeSession = null
        pendingCancellation = null
    }

    fun onStreamEof(session: StreamSession): StreamLifecycleDecision =
        when {
            !owns(session) -> StreamLifecycleDecision.Ignore
            session.terminalStatus != null -> StreamLifecycleDecision.Cleanup(session.terminalStatus)
            pendingCancellation === session -> StreamLifecycleDecision.Ignore
            else -> StreamLifecycleDecision.Interrupted(StreamInterruptReason.EOF_WITHOUT_TERMINAL)
        }

    fun onProviderException(session: StreamSession): StreamLifecycleDecision =
        when {
            !owns(session) -> StreamLifecycleDecision.Ignore
            session.terminalStatus != null -> StreamLifecycleDecision.Cleanup(session.terminalStatus)
            pendingCancellation === session -> StreamLifecycleDecision.Ignore
            else -> StreamLifecycleDecision.Interrupted(StreamInterruptReason.PROVIDER_EXCEPTION)
        }

    fun onHeartbeatTimeout(
        session: StreamSession,
        capturedVersion: Long,
    ): StreamLifecycleDecision =
        when {
            !owns(session) -> StreamLifecycleDecision.Ignore
            pendingCancellation === session -> StreamLifecycleDecision.Ignore
            session.terminalStatus != null -> StreamLifecycleDecision.Ignore
            session.version != capturedVersion -> StreamLifecycleDecision.Ignore
            else -> StreamLifecycleDecision.Interrupted(StreamInterruptReason.HEARTBEAT_TIMEOUT)
        }

    fun acceptEvent(session: StreamSession, event: ChatStreamEvent): StreamEventDecision {
        val eventRunId = event.runIdOrNull()
        val eventThreadId = event.threadIdOrNull()
        val eventBackendRunId = event.backendRunIdOrNull()
        val eventType = event.debugName()
        val streamSeq = event.streamSeqOrNull()
        if (!owns(session)) {
            return StreamEventDecision.Dropped(
                session = session,
                reason = StreamDropReason.STALE_SESSION,
                eventType = eventType,
                eventRunId = eventRunId,
                streamSeq = streamSeq,
                lastReceivedStreamSeq = session.receivedStreamSeq,
            )
        }
        if (eventThreadId != null && eventThreadId != session.threadId) {
            return StreamEventDecision.Dropped(
                session = session,
                reason = StreamDropReason.THREAD_MISMATCH,
                eventType = eventType,
                eventRunId = eventRunId,
                streamSeq = streamSeq,
                lastReceivedStreamSeq = session.receivedStreamSeq,
            )
        }
        if (!session.bindRunId(eventRunId)) {
            return StreamEventDecision.Dropped(
                session = session,
                reason = StreamDropReason.RUN_MISMATCH,
                eventType = eventType,
                eventRunId = eventRunId,
                streamSeq = streamSeq,
                lastReceivedStreamSeq = session.receivedStreamSeq,
            )
        }

        val terminalStatus = event.terminalStatusNameOrNull()
        val accepted = session.acceptStreamSeq(
            streamSeq = streamSeq,
            allowNonIncreasingTerminal = terminalStatus != null,
            nowMillis = nowMillis(),
        )
        if (!accepted) {
            return StreamEventDecision.Dropped(
                session = session,
                reason = StreamDropReason.STREAM_SEQ,
                eventType = eventType,
                eventRunId = eventRunId,
                streamSeq = streamSeq,
                lastReceivedStreamSeq = session.receivedStreamSeq,
            )
        }

        if (event is ChatStreamEvent.Heartbeat) {
            session.markHeartbeat(nowMillis())
        }
        if (!eventBackendRunId.isNullOrBlank()) {
            session.backendRunId = eventBackendRunId
        }
        if (terminalStatus != null) {
            session.terminalStatus = terminalStatus
        }
        return StreamEventDecision.Accepted(
            session = session,
            eventType = eventType,
            streamSeq = streamSeq,
            terminalStatus = terminalStatus,
        )
    }
}

private fun ChatStreamEvent.runIdOrNull(): String? = when (this) {
    is ChatStreamEvent.TextDelta -> runId
    is ChatStreamEvent.StreamStarted -> runId
    is ChatStreamEvent.Heartbeat -> runId
    is ChatStreamEvent.TaskComplexity -> runId
    is ChatStreamEvent.TaskProgress -> runId
    is ChatStreamEvent.StreamEof -> runId
    is ChatStreamEvent.Error -> runId
    is ChatStreamEvent.Trace -> event.runId
    else -> null
}

private fun ChatStreamEvent.threadIdOrNull(): String? = when (this) {
    is ChatStreamEvent.TextDelta -> threadId
    is ChatStreamEvent.StreamStarted -> threadId
    is ChatStreamEvent.Heartbeat -> threadId
    is ChatStreamEvent.TaskComplexity -> threadId
    is ChatStreamEvent.TaskProgress -> threadId
    is ChatStreamEvent.StreamEof -> threadId
    is ChatStreamEvent.Error -> threadId
    is ChatStreamEvent.Trace -> when (event) {
        is TraceEvent.RunStarted -> event.threadId
        else -> null
    }
    else -> null
}

private fun ChatStreamEvent.backendRunIdOrNull(): String? = when (this) {
    is ChatStreamEvent.TextDelta -> backendRunId
    is ChatStreamEvent.StreamStarted -> backendRunId
    is ChatStreamEvent.Heartbeat -> backendRunId
    is ChatStreamEvent.TaskComplexity -> backendRunId
    is ChatStreamEvent.TaskProgress -> backendRunId
    is ChatStreamEvent.StreamEof -> backendRunId
    is ChatStreamEvent.Error -> backendRunId
    else -> null
}

private fun ChatStreamEvent.streamSeqOrNull(): Long? = when (this) {
    is ChatStreamEvent.TextDelta -> streamSeq
    is ChatStreamEvent.Trace -> streamSeq
    is ChatStreamEvent.StreamStarted -> streamSeq
    is ChatStreamEvent.Heartbeat -> streamSeq
    is ChatStreamEvent.TaskComplexity -> streamSeq
    is ChatStreamEvent.TaskProgress -> streamSeq
    is ChatStreamEvent.StreamEof -> streamSeq
    is ChatStreamEvent.Error -> streamSeq
    else -> null
}

private fun ChatStreamEvent.terminalStatusNameOrNull(): String? =
    if (this is ChatStreamEvent.Trace && event is TraceEvent.RunTerminal) {
        event.status.terminalStatusName()
    } else {
        null
    }

private fun ChatStreamEvent.debugName(): String = when (this) {
    is ChatStreamEvent.TextDelta -> "assistant.delta"
    is ChatStreamEvent.Trace -> "trace.${event.debugName()}"
    is ChatStreamEvent.StreamStarted -> "stream.started"
    is ChatStreamEvent.Heartbeat -> "stream.heartbeat"
    is ChatStreamEvent.TaskComplexity -> "task_complexity"
    is ChatStreamEvent.TaskProgress -> "task_progress"
    ChatStreamEvent.Completed -> "completed"
    is ChatStreamEvent.StreamEof -> "stream.eof"
    is ChatStreamEvent.Error -> "stream.error"
}

private fun TraceEvent.debugName(): String = when (this) {
    is TraceEvent.RunStarted -> "run.started"
    is TraceEvent.StepUpsert -> "step.upsert"
    is TraceEvent.StepDetailAppend -> "step.detail.append"
    is TraceEvent.RunTerminal -> "run.terminal"
}

private fun TraceRunStatus.terminalStatusName(): String =
    when (this) {
        TraceRunStatus.RUNNING -> "running"
        TraceRunStatus.SUCCEEDED -> "succeeded"
        TraceRunStatus.FAILED -> "failed"
        TraceRunStatus.CANCELLED -> "cancelled"
        TraceRunStatus.WAITING_FOR_USER -> "waiting_for_user"
        TraceRunStatus.INTERRUPTED -> "interrupted"
    }
