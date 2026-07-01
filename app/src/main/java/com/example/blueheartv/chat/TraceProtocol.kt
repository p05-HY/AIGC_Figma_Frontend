package com.example.blueheartv.chat

import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceEvent
import com.example.blueheartv.model.TraceRunStatus
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus
import org.json.JSONObject

private const val MAX_TRACE_EVENT_BYTES = 4 * 1024
private const val MAX_TRACE_TITLE_CHARS = 48
private const val MAX_TRACE_SUMMARY_CHARS = 240
private const val MAX_TRACE_DETAIL_TEXT_CHARS = 500
private val THINK_OPEN_TAG = Regex("<\\s*(?:think|thinking)\\s*>", RegexOption.IGNORE_CASE)
private val THINK_CLOSE_TAG = Regex("<\\s*/\\s*(?:think|thinking)\\s*>", RegexOption.IGNORE_CASE)

/** 对安全门面输出做白名单解析。任何未知字段与未识别事件都会被忽略。 */
fun parseSafeStreamEvent(eventName: String, payload: String): ChatStreamEvent? {
    if (payload.toByteArray(Charsets.UTF_8).size > MAX_TRACE_EVENT_BYTES) return null
    val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    return when (eventName.lowercase()) {
        "trace.v1" -> parseTraceEvent(json)?.let {
            ChatStreamEvent.Trace(it, streamSeq = json.optionalPositiveLong("streamSeq"))
        }
        "assistant.delta" -> parseAssistantDelta(json)
        "task_progress" -> parseTaskProgress(json)
        "task_complexity" -> parseTaskComplexity(json)
        "stream.started" -> parseStreamStarted(json)
        "stream.heartbeat" -> if (json.optString("type") == "stream.heartbeat") {
            ChatStreamEvent.Heartbeat(
                runId = json.optString("runId").ifBlank { null },
                threadId = json.optString("threadId").ifBlank { null },
                backendRunId = json.optString("backendRunId").ifBlank { null },
                streamSeq = json.optionalPositiveLong("streamSeq"),
                timestamp = json.optionalPositiveLong("timestamp"),
            )
        } else {
            null
        }
        "stream.eof" -> if (json.optString("type") == "stream.eof") {
            ChatStreamEvent.StreamEof(
                streamSeq = json.optionalPositiveLong("streamSeq"),
                runId = json.optString("runId").ifBlank { null },
                threadId = json.optString("threadId").ifBlank { null },
                backendRunId = json.optString("backendRunId").ifBlank { null },
                timestamp = json.optionalPositiveLong("timestamp"),
            )
        } else {
            null
        }
        "stream.error" -> if (json.optString("type") == "stream.error") {
            ChatStreamEvent.Error(
                message = json.optString("message").ifBlank { "服务连接中断，请稍后重试。" },
                retryable = json.optBoolean("retryable", true),
                streamSeq = json.optionalPositiveLong("streamSeq"),
                terminalStatus = json.optString("terminalStatus").ifBlank { null },
                terminalReason = json.optString("terminalReason").ifBlank { null },
                cancelSource = json.optString("cancelSource").ifBlank { null },
                runId = json.optString("runId").ifBlank { null },
                threadId = json.optString("threadId").ifBlank { null },
                backendRunId = json.optString("backendRunId").ifBlank { null },
                timestamp = json.optionalPositiveLong("timestamp"),
            )
        } else {
            null
        }

        else -> null
    }
}

private fun parseTraceEvent(json: JSONObject): TraceEvent? {
    if (json.optString("type") != "trace.v1" || json.optInt("version") != 1) return null
    val runId = json.requiredString("runId") ?: return null
    val eventId = json.requiredString("eventId") ?: return null
    val seq = json.optLong("seq", 0L).takeIf { it > 0L } ?: return null
    return when (json.requiredString("event")) {
        "run.started" -> TraceEvent.RunStarted(
            runId = runId,
            eventId = eventId,
            seq = seq,
            threadId = json.optString("threadId").ifBlank { null },
            summary = json.optString("summary").ifBlank { null }?.bounded(MAX_TRACE_SUMMARY_CHARS),
        )

        "step.upsert" -> {
            val step = json.optJSONObject("step") ?: return null
            if (!step.optBoolean("visibleToUser", false)) return null
            val status = stepStatus(step.requiredString("status") ?: return null) ?: return null
            val stepId = step.requiredString("stepId") ?: return null
            val kind = step.requiredString("kind") ?: return null
            val title = step.requiredString("title") ?: return null
            val summary = step.requiredString("summary") ?: return null
            TraceEvent.StepUpsert(
                runId = runId,
                eventId = eventId,
                seq = seq,
                step = TraceStep(
                    id = stepId,
                    parentId = step.optString("parentId").ifBlank { null },
                    kind = kind,
                    title = title.bounded(MAX_TRACE_TITLE_CHARS),
                    summary = summary.bounded(MAX_TRACE_SUMMARY_CHARS),
                    status = status,
                    visibleToUser = true,
                ),
            )
        }

        "step.detail.append" -> {
            val stepId = json.requiredString("stepId") ?: return null
            val detail = json.optJSONObject("detail") ?: return null
            if (!detail.optBoolean("visibleToUser", true)) return null
            val detailId = detail.requiredString("detailId") ?: return null
            val kind = detailKind(detail.requiredString("kind") ?: return null) ?: return null
            val title = detail.requiredString("title") ?: return null
            val text = detail.requiredString("text") ?: return null
            if (text.containsThinkMarkup()) return null
            TraceEvent.StepDetailAppend(
                runId = runId,
                eventId = eventId,
                seq = seq,
                streamSeq = json.optionalPositiveLong("streamSeq"),
                stepId = stepId,
                detail = TraceDetail(
                    id = detailId,
                    kind = kind,
                    title = title.bounded(MAX_TRACE_TITLE_CHARS),
                    text = text.bounded(MAX_TRACE_DETAIL_TEXT_CHARS),
                    visibleToUser = true,
                ),
            )
        }

        "run.terminal" -> runStatus(json.requiredString("status") ?: return null)?.let { status ->
            TraceEvent.RunTerminal(
                runId = runId,
                eventId = eventId,
                seq = seq,
                status = status,
                reason = json.optString("reason").ifBlank { null }?.bounded(128),
                cancelSource = json.optString("cancelSource").ifBlank { null }?.bounded(64),
            )
        }

        else -> null
    }
}

private fun parseAssistantDelta(json: JSONObject): ChatStreamEvent.TextDelta? {
    if (json.optString("type") != "assistant.delta") return null
    val chunk = sanitizeAssistantDelta(json.requiredString("chunk") ?: return null) ?: return null
    return ChatStreamEvent.TextDelta(
        chunk = chunk,
        invocationId = json.optString("invocationId").ifBlank { null },
        streamSeq = json.optionalPositiveLong("streamSeq"),
        runId = json.optString("runId").ifBlank { null },
        threadId = json.optString("threadId").ifBlank { null },
        backendRunId = json.optString("backendRunId").ifBlank { null },
        timestamp = json.optionalPositiveLong("timestamp"),
    )
}

private fun parseTaskProgress(json: JSONObject): ChatStreamEvent.TaskProgress? {
    if (json.optString("type") != "task_progress") return null
    val label = json.requiredString("label") ?: return null
    val status = json.requiredString("status") ?: return null
    val phase = json.requiredString("phase") ?: return null
    return ChatStreamEvent.TaskProgress(
        label = label.bounded(MAX_TRACE_TITLE_CHARS),
        status = status.bounded(32),
        phase = phase.bounded(64),
        message = json.optString("message").ifBlank { null }?.bounded(MAX_TRACE_SUMMARY_CHARS),
        progressKey = json.optString("progressKey").ifBlank { null }?.bounded(128),
        currentStep = json.optionalNonNegativeInt("currentStep"),
        totalSteps = json.optionalNonNegativeInt("totalSteps"),
        streamSeq = json.optionalPositiveLong("streamSeq"),
        runId = json.optString("runId").ifBlank { null },
        threadId = json.optString("threadId").ifBlank { null },
        backendRunId = json.optString("backendRunId").ifBlank { null },
        timestamp = json.optionalPositiveLong("timestamp"),
    )
}

private fun parseTaskComplexity(json: JSONObject): ChatStreamEvent.TaskComplexity? {
    if (json.optString("type") != "task_complexity") return null
    val complexity = json.requiredString("complexity") ?: return null
    val reason = json.requiredString("reason") ?: return null
    if (!json.has("trackSteps")) return null
    return ChatStreamEvent.TaskComplexity(
        complexity = complexity.bounded(32),
        trackSteps = json.optBoolean("trackSteps"),
        reason = reason.bounded(64),
        message = json.optString("message").ifBlank { null }?.bounded(MAX_TRACE_SUMMARY_CHARS),
        streamSeq = json.optionalPositiveLong("streamSeq"),
        runId = json.optString("runId").ifBlank { null },
        threadId = json.optString("threadId").ifBlank { null },
        backendRunId = json.optString("backendRunId").ifBlank { null },
        timestamp = json.optionalPositiveLong("timestamp"),
    )
}

private fun parseStreamStarted(json: JSONObject): ChatStreamEvent.StreamStarted? {
    if (json.optString("type") != "stream.started") return null
    val runId = json.requiredString("runId") ?: return null
    return ChatStreamEvent.StreamStarted(
        runId = runId,
        message = json.optString("message").ifBlank { "已接收请求，正在连接 Agent。" },
        streamSeq = json.optionalPositiveLong("streamSeq"),
        threadId = json.optString("threadId").ifBlank { null },
        backendRunId = json.optString("backendRunId").ifBlank { null },
        timestamp = json.optionalPositiveLong("timestamp"),
    )
}

private fun JSONObject.requiredString(key: String): String? =
    optString(key).trim().ifBlank { null }

private fun JSONObject.optionalNonNegativeInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key).takeIf { it >= 0 } else null

private fun JSONObject.optionalPositiveLong(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0L } else null

private fun sanitizeAssistantDelta(chunk: String): String? {
    if (THINK_OPEN_TAG.containsMatchIn(chunk)) return null
    if (chunk.contains("<think", ignoreCase = true)) return null
    val cleaned = THINK_CLOSE_TAG.replace(chunk, "")
    if (cleaned.containsThinkMarkup()) return null
    return cleaned.takeIf { it.isNotEmpty() }
}

private fun String.containsThinkMarkup(): Boolean =
    THINK_OPEN_TAG.containsMatchIn(this) ||
        THINK_CLOSE_TAG.containsMatchIn(this) ||
        contains("<think", ignoreCase = true) ||
        contains("</think", ignoreCase = true)

private fun String.bounded(limit: Int): String =
    if (length <= limit) this else take(limit - 1) + "…"

private fun stepStatus(value: String): TraceStepStatus? = when (value) {
    "queued" -> TraceStepStatus.QUEUED
    "running" -> TraceStepStatus.RUNNING
    "succeeded" -> TraceStepStatus.SUCCEEDED
    "failed" -> TraceStepStatus.FAILED
    "cancelled" -> TraceStepStatus.CANCELLED
    "waiting_for_user" -> TraceStepStatus.WAITING_FOR_USER
    else -> null
}

private fun detailKind(value: String): TraceDetailKind? = when (value) {
    "reasoning_summary" -> TraceDetailKind.REASONING_SUMMARY
    "plan" -> TraceDetailKind.PLAN
    "decision" -> TraceDetailKind.DECISION
    "tool_call" -> TraceDetailKind.TOOL_CALL
    "tool_args_summary" -> TraceDetailKind.TOOL_ARGS_SUMMARY
    "tool_result" -> TraceDetailKind.TOOL_RESULT
    "observation" -> TraceDetailKind.OBSERVATION
    "retry" -> TraceDetailKind.RETRY
    "warning" -> TraceDetailKind.WARNING
    "error" -> TraceDetailKind.ERROR
    else -> null
}

private fun runStatus(value: String): TraceRunStatus? = when (value) {
    "succeeded" -> TraceRunStatus.SUCCEEDED
    "failed" -> TraceRunStatus.FAILED
    "cancelled" -> TraceRunStatus.CANCELLED
    "waiting_for_user" -> TraceRunStatus.WAITING_FOR_USER
    else -> null
}
