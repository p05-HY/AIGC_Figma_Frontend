package com.example.blueheartv.control

import org.json.JSONObject

data class AdbSnapshot(
    val screenshot: String?,
    val ui: String?,
    val currentPackage: String?,
    val activity: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("screenshot", screenshot)
        put("ui", ui)
        put("currentPackage", currentPackage)
        put("activity", activity)
    }
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}
