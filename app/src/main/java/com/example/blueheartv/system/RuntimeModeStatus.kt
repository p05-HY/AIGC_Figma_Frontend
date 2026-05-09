package com.example.blueheartv.system

import org.json.JSONObject

data class RuntimeModeStatus(
    val networkConnected: Boolean,
    val mode: String,
    val localServerRunning: Boolean,
    val localModelPath: String?
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("networkConnected", networkConnected)
            put("mode", mode)
            put("localServerRunning", localServerRunning)
            put("localModelPath", localModelPath ?: JSONObject.NULL)
        }
    }
}
