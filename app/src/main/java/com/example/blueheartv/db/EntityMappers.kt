package com.example.blueheartv.db

import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.model.ToolCall
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
                add(ToolCall(label = label, isComplete = obj.optBoolean("isComplete")))
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

private fun serializeToolCalls(toolCalls: List<ToolCall>): String {
    return JSONArray().apply {
        toolCalls.forEach { tc ->
            put(JSONObject().apply {
                put("label", tc.label)
                put("isComplete", tc.isComplete)
            })
        }
    }.toString()
}
