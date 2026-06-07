package com.example.blueheartv.control

import org.json.JSONObject

data class AdbSnapshot(
    val screenshot: String?,
    val screenshotMimeType: String?,
    val ui: String?,
    val currentPackage: String?,
    val activity: String?,
    val deviceId: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("screenshot", screenshot)
        put("screenshotMimeType", screenshotMimeType)
        put("ui", ui)
        put("currentPackage", currentPackage)
        put("activity", activity)
        put("deviceId", deviceId)
    }
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}
