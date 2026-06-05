package com.example.blueheartv.db

import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.model.ToolCallStatus
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
        orderIndex = orderIndex,
    )
}

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
                        args = obj.optString("args").takeIf { it.isNotBlank() },
                        result = obj.optString("result").takeIf { it.isNotBlank() },
                        error = obj.optString("error").takeIf { it.isNotBlank() },
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
                tc.args?.let { put("args", it) }
                tc.result?.let { put("result", it) }
                tc.error?.let { put("error", it) }
            })
        }
    }.toString()
}
