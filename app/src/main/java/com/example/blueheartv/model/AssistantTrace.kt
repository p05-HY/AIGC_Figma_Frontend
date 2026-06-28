package com.example.blueheartv.model

/** 仅保存服务端安全门面下发的、可面向用户展示的执行轨迹。 */
data class AssistantTrace(
    val runId: String,
    val threadId: String? = null,
    val summary: String? = null,
    val steps: List<TraceStep> = emptyList(),
    val runStatus: TraceRunStatus = TraceRunStatus.RUNNING,
    val lastSeq: Long = 0L,
    val seenEventIds: Set<String> = emptySet(),
    val hasTerminal: Boolean = false,
)

enum class TraceRunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    WAITING_FOR_USER,
    INTERRUPTED,
}

enum class TraceStepStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    WAITING_FOR_USER,
}

enum class TraceDetailKind {
    REASONING_SUMMARY,
    PLAN,
    DECISION,
    TOOL_CALL,
    TOOL_ARGS_SUMMARY,
    TOOL_RESULT,
    OBSERVATION,
    RETRY,
    WARNING,
    ERROR,
}

data class TraceDetail(
    val id: String,
    val kind: TraceDetailKind,
    val title: String,
    val text: String,
    val visibleToUser: Boolean = true,
)

data class TraceStep(
    val id: String,
    val parentId: String? = null,
    val kind: String,
    val title: String,
    val summary: String,
    val status: TraceStepStatus,
    val visibleToUser: Boolean = true,
    val details: List<TraceDetail> = emptyList(),
)

sealed interface TraceEvent {
    val runId: String
    val eventId: String
    val seq: Long

    data class RunStarted(
        override val runId: String,
        override val eventId: String,
        override val seq: Long,
        val threadId: String? = null,
        val summary: String? = null,
    ) : TraceEvent

    data class StepUpsert(
        override val runId: String,
        override val eventId: String,
        override val seq: Long,
        val step: TraceStep,
    ) : TraceEvent

    data class StepDetailAppend(
        override val runId: String,
        override val eventId: String,
        override val seq: Long,
        val streamSeq: Long?,
        val stepId: String,
        val detail: TraceDetail,
    ) : TraceEvent

    data class RunTerminal(
        override val runId: String,
        override val eventId: String,
        override val seq: Long,
        val status: TraceRunStatus,
        val reason: String? = null,
        val cancelSource: String? = null,
    ) : TraceEvent
}
