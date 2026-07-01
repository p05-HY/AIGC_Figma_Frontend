package com.example.blueheartv.chat

import com.example.blueheartv.model.TraceEvent

sealed interface ChatStreamEvent {
    data class TextDelta(
        val chunk: String,
        val invocationId: String? = null,
        val streamSeq: Long? = null,
        val runId: String? = null,
        val threadId: String? = null,
        val backendRunId: String? = null,
        val timestamp: Long? = null,
    ) : ChatStreamEvent

    /** 来自安全 SSE 门面的用户可见执行轨迹，不承载原始工具数据。 */
    data class Trace(
        val event: TraceEvent,
        val streamSeq: Long? = null,
    ) : ChatStreamEvent

    /** 安全 SSE 门面已接收请求并开始代理；不是业务 run.started。 */
    data class StreamStarted(
        val runId: String,
        val message: String,
        val streamSeq: Long? = null,
        val threadId: String? = null,
        val backendRunId: String? = null,
        val timestamp: Long? = null,
    ) : ChatStreamEvent

    data class Heartbeat(
        val runId: String? = null,
        val threadId: String? = null,
        val backendRunId: String? = null,
        val streamSeq: Long? = null,
        val timestamp: Long? = null,
    ) : ChatStreamEvent

    data class TaskComplexity(
        val complexity: String,
        val trackSteps: Boolean,
        val reason: String,
        val message: String? = null,
        val streamSeq: Long? = null,
        val runId: String? = null,
        val threadId: String? = null,
        val backendRunId: String? = null,
        val timestamp: Long? = null,
    ) : ChatStreamEvent

    data class TaskProgress(
        val label: String,
        val status: String,
        val phase: String,
        val message: String? = null,
        val toolName: String? = null,
        val progressKey: String? = null,
        val currentStep: Int? = null,
        val totalSteps: Int? = null,
        val completedSteps: List<TaskProgressStep> = emptyList(),
        val streamSeq: Long? = null,
        val runId: String? = null,
        val threadId: String? = null,
        val backendRunId: String? = null,
        val timestamp: Long? = null,
    ) : ChatStreamEvent

    data class TaskProgressStep(
        val index: Int?,
        val name: String,
        val status: String,
    )

    data object Completed : ChatStreamEvent

    /** 安全门面明确发出的传输结束事件；它不等同于 Agent 执行成功。 */
    data class StreamEof(
        val streamSeq: Long? = null,
        val runId: String? = null,
        val threadId: String? = null,
        val backendRunId: String? = null,
        val timestamp: Long? = null,
    ) : ChatStreamEvent

    data class Error(
        val message: String,
        val retryable: Boolean = true,
        val streamSeq: Long? = null,
        val terminalStatus: String? = null,
        val terminalReason: String? = null,
        val cancelSource: String? = null,
        val runId: String? = null,
        val threadId: String? = null,
        val backendRunId: String? = null,
        val timestamp: Long? = null,
    ) : ChatStreamEvent
}
