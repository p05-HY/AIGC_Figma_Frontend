package com.example.blueheartv.db

import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.model.ToolCallStatus
import com.example.blueheartv.model.ToolProgressStep
import com.example.blueheartv.model.AssistantTrace
import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceRunStatus
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus
import org.json.JSONArray
import org.json.JSONObject

fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        content = content,
        isUser = isUser,
        deliveryState = runCatching { MessageDeliveryState.valueOf(deliveryState) }
            .getOrDefault(MessageDeliveryState.COMPLETED),
        errorMessage = errorMessage,
        toolCalls = toolCallsJson?.let { parseToolCalls(it) },
        trace = traceJson?.let { parseTrace(it) },
        lastReceivedStreamSeq = lastReceivedStreamSeq,
        terminalStatus = terminalStatus,
    )
}

fun Message.toEntity(sessionId: String, orderIndex: Int): MessageEntity {
    return MessageEntity(
        id = id,
        sessionId = sessionId,
        content = content,
        isUser = isUser,
        deliveryState = deliveryState.name,
        errorMessage = errorMessage,
        toolCallsJson = toolCalls?.let { serializeToolCalls(it) },
        traceJson = trace?.let { serializeTrace(it) },
        lastReceivedStreamSeq = lastReceivedStreamSeq,
        terminalStatus = terminalStatus,
        privacySanitized = true,
        orderIndex = orderIndex,
    )
}

/**
 * 首次读取 v1 历史记录时，使用同一份白名单映射重写 JSON。
 * 原始工具入参、结果和错误内容不会返回到领域模型或继续留在数据库中。
 */
fun MessageEntity.sanitizeForStorage(): MessageEntity = copy(
    toolCallsJson = toolCallsJson?.let { raw ->
        parseToolCalls(raw)?.let(::serializeToolCalls)
    },
    privacySanitized = true,
)

private fun parseToolCalls(json: String): List<ToolCall>? {
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val label = obj.optString("label").takeIf { it.isNotBlank() } ?: continue
                val status = obj.optString("status").takeIf { it.isNotBlank() }?.let {
                    runCatching { ToolCallStatus.valueOf(it) }.getOrNull()
                } ?: if (obj.optBoolean("isComplete")) ToolCallStatus.COMPLETED else ToolCallStatus.RUNNING
                add(
                    ToolCall(
                        label = label,
                        status = status,
                        phase = obj.optString("phase").takeIf { it.isNotBlank() },
                        message = obj.optString("message").takeIf { it.isNotBlank() },
                        toolName = obj.optString("toolName").takeIf { it.isNotBlank() },
                        progressKey = obj.optString("progressKey").takeIf { it.isNotBlank() },
                        currentStep = obj.optionalInt("currentStep"),
                        totalSteps = obj.optionalInt("totalSteps"),
                        completedSteps = obj.optJSONArray("completedSteps").parseProgressSteps(),
                    ),
                )
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

private fun serializeToolCalls(toolCalls: List<ToolCall>): String {
    return JSONArray().apply {
        toolCalls.forEach { tc ->
            put(JSONObject().apply {
                put("label", tc.label)
                put("status", tc.status.name)
                tc.phase?.let { put("phase", it) }
                tc.message?.let { put("message", it) }
                tc.toolName?.let { put("toolName", it) }
                tc.progressKey?.let { put("progressKey", it) }
                tc.currentStep?.let { put("currentStep", it) }
                tc.totalSteps?.let { put("totalSteps", it) }
                if (tc.completedSteps.isNotEmpty()) {
                    put(
                        "completedSteps",
                        JSONArray().apply {
                            tc.completedSteps.forEach { step ->
                                put(JSONObject().apply {
                                    step.index?.let { put("index", it) }
                                    put("name", step.name)
                                    put("status", step.status)
                                })
                            }
                        },
                    )
                }
            })
        }
    }.toString()
}

private fun JSONObject.optionalInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

private fun JSONArray?.parseProgressSteps(): List<ToolProgressStep> {
    if (this == null || length() == 0) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
            add(
                ToolProgressStep(
                    index = obj.optionalInt("index"),
                    name = name,
                    status = obj.optString("status").ifBlank { "completed" },
                ),
            )
        }
    }
}

// ── Trace (AssistantTrace) JSON 序列化 ──

private fun parseTrace(json: String): AssistantTrace? {
    return runCatching {
        val obj = JSONObject(json)
        AssistantTrace(
            runId = obj.optString("runId"),
            threadId = obj.optString("threadId").takeIf { it.isNotBlank() },
            summary = obj.optString("summary").takeIf { it.isNotBlank() },
            displayContext = obj.optString("displayContext").takeIf { it.isNotBlank() },
            steps = obj.optJSONArray("steps")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val stepObj = arr.optJSONObject(i) ?: return@mapNotNull null
                    TraceStep(
                        id = stepObj.optString("id"),
                        parentId = stepObj.optString("parentId").takeIf { it.isNotBlank() },
                        kind = stepObj.optString("kind"),
                        title = stepObj.optString("title"),
                        summary = stepObj.optString("summary"),
                        status = runCatching { TraceStepStatus.valueOf(stepObj.optString("status")) }
                            .getOrDefault(TraceStepStatus.RUNNING),
                        visibleToUser = stepObj.optBoolean("visibleToUser", true),
                        details = stepObj.optJSONArray("details").parseTraceDetails(),
                    )
                }
            }.orEmpty(),
            runStatus = runCatching { TraceRunStatus.valueOf(obj.optString("runStatus")) }
                .getOrDefault(TraceRunStatus.RUNNING),
            terminalReason = obj.optString("terminalReason").takeIf { it.isNotBlank() },
            cancelSource = obj.optString("cancelSource").takeIf { it.isNotBlank() },
            lastSeq = obj.optLong("lastSeq", 0L),
            seenEventIds = obj.optJSONArray("seenEventIds")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }.toSet()
            }.orEmpty(),
            hasTerminal = obj.optBoolean("hasTerminal", false),
        )
    }.getOrNull()
}

private fun serializeTrace(trace: AssistantTrace): String {
    return JSONObject().apply {
        put("runId", trace.runId)
        trace.threadId?.let { put("threadId", it) }
        trace.summary?.let { put("summary", it) }
        trace.displayContext?.let { put("displayContext", it) }
        put("steps", JSONArray().apply {
            trace.steps.forEach { step ->
                put(JSONObject().apply {
                    put("id", step.id)
                    step.parentId?.let { put("parentId", it) }
                    put("kind", step.kind)
                    put("title", step.title)
                    put("summary", step.summary)
                    put("status", step.status.name)
                    put("visibleToUser", step.visibleToUser)
                    if (step.details.isNotEmpty()) {
                        put(
                            "details",
                            JSONArray().apply {
                                step.details.forEach { detail ->
                                    put(JSONObject().apply {
                                        put("id", detail.id)
                                        put("kind", detail.kind.name)
                                        put("title", detail.title)
                                        put("text", detail.text)
                                        put("visibleToUser", detail.visibleToUser)
                                    })
                                }
                            },
                        )
                    }
                })
            }
        })
        put("runStatus", trace.runStatus.name)
        trace.terminalReason?.let { put("terminalReason", it) }
        trace.cancelSource?.let { put("cancelSource", it) }
        put("lastSeq", trace.lastSeq)
        put("seenEventIds", JSONArray(trace.seenEventIds))
        put("hasTerminal", trace.hasTerminal)
    }.toString()
}

private fun JSONArray?.parseTraceDetails(): List<TraceDetail> {
    if (this == null || length() == 0) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            if (!obj.optBoolean("visibleToUser", true)) continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val kind = parseTraceDetailKind(obj.optString("kind")) ?: continue
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: continue
            val text = obj.optString("text").takeIf { it.isNotBlank() } ?: continue
            add(
                TraceDetail(
                    id = id,
                    kind = kind,
                    title = title,
                    text = text,
                    visibleToUser = true,
                ),
            )
        }
    }
}

private fun parseTraceDetailKind(value: String): TraceDetailKind? =
    runCatching { TraceDetailKind.valueOf(value) }.getOrNull() ?: when (value) {
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
