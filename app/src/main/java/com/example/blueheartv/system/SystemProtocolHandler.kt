package com.example.blueheartv.system

import org.json.JSONArray
import org.json.JSONObject

class SystemProtocolHandler(
    private val api: SystemApi
) {
    suspend fun handleRequest(envelope: JSONObject): JSONObject {
        val message = envelope.optString("message")
        val requestId = if (envelope.has("requestId")) envelope.optInt("requestId") else null
        return runCatching {
            when (message) {
                "ping" -> response("pong", requestId, null)
                "listApps" -> {
                    val data = envelope.requiredObject("data")
                    response(message, requestId, api.listApps(data.requiredString("type")))
                }

                "createEvent" -> {
                    val event = envelope.requiredObject("data").requiredObject("event")
                    response(
                        message,
                        requestId,
                        JSONObject().put("id", api.createEvent(event))
                    )
                }

                "listEvents" -> {
                    val data = envelope.requiredObject("data")
                    response(
                        message,
                        requestId,
                        api.listEvents(
                            start = data.requiredLong("start"),
                            end = data.requiredLong("end")
                        )
                    )
                }

                "updateEvent" -> {
                    val event = envelope.requiredObject("data").requiredObject("event")
                    api.updateEvent(event)
                    response(message, requestId, null)
                }

                "listReminders" -> {
                    val data = envelope.requiredObject("data")
                    response(message, requestId, api.listReminders(data.requiredLong("eventId")))
                }

                "updateReminders" -> {
                    val data = envelope.requiredObject("data")
                    api.updateReminders(
                        eventId = data.requiredLong("eventId"),
                        reminders = data.requiredArray("reminders")
                    )
                    response(message, requestId, null)
                }

                "getConnectionStatus" -> response(message, requestId, api.getConnectionStatus())
                "getRuntimeStatus" -> response(message, requestId, api.getRuntimeStatus())
                "getLocation" -> response(message, requestId, api.getLocation())
                else -> error("未知消息类型: $message")
            }
        }.getOrElse { error ->
            response(
                message = message,
                requestId = requestId,
                data = JSONObject().put("error", error.message ?: "执行失败")
            )
        }
    }

    companion object {
        fun request(message: String, requestId: Int?, data: Any?): JSONObject {
            return envelope("request", message, requestId, data)
        }

        fun response(message: String, requestId: Int?, data: Any?): JSONObject {
            return envelope("response", message, requestId, data)
        }

        private fun envelope(type: String, message: String, requestId: Int?, data: Any?): JSONObject {
            return JSONObject().apply {
                put("type", type)
                put("message", message)
                put(
                    "data",
                    when (data) {
                        null -> JSONObject.NULL
                        is JSONObject, is JSONArray -> data
                        else -> data
                    }
                )
                if (requestId != null) {
                    put("requestId", requestId)
                }
            }
        }
    }
}

private fun JSONObject.requiredObject(key: String): JSONObject {
    require(has(key) && !isNull(key)) { "$key 缺失。" }
    return getJSONObject(key)
}

private fun JSONObject.requiredArray(key: String): JSONArray {
    require(has(key) && !isNull(key)) { "$key 缺失。" }
    return getJSONArray(key)
}

private fun JSONObject.requiredString(key: String): String {
    require(has(key) && !isNull(key)) { "$key 缺失。" }
    return getString(key)
}

private fun JSONObject.requiredLong(key: String): Long {
    require(has(key) && !isNull(key)) { "$key 缺失。" }
    return getLong(key)
}
