package com.example.blueheartv.control

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File

private const val TAG = "AdbSnapshotCollector"
private const val SCREENSHOT_FILE_NAME = "adb_snapshot.png"

class AdbSnapshotCollector(
    context: Context,
    private val executor: ShizukuAdbExecutor,
) {

    suspend fun collect(): AdbSnapshot {
        val screenshot = captureScreenshotBase64()

        val ui = AdbAccessibilityService.dumpUiTree()
        val topActivity = resolveTopActivity()

        return AdbSnapshot(
            screenshot = screenshot,
            ui = ui,
            currentPackage = AdbAccessibilityService.currentPackageName() ?: topActivity.first,
            activity = AdbAccessibilityService.currentActivityName() ?: topActivity.second
        )
    }

    private suspend fun captureScreenshotBase64(): String? {
        return runCatching {
            // shell 用户可写的临时目录
            val tempFilePath = "/data/local/tmp/screenshot_${System.currentTimeMillis()}.png"
            val result = executor.execute("screencap -p '$tempFilePath'")

            if (!result.isSuccess) {
                Log.w(TAG, "screencap failed: ${result.stderr.ifBlank { result.stdout }}")
                return null
            }

            // 读取文件内容
            val file = File(tempFilePath)
            if (!file.exists() || file.length() <= 0L) {
                Log.w(TAG, "screencap produced empty file")
                return null
            }

            val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)

            // 清理临时文件
            file.delete()

            base64
        }.onFailure { error ->
            Log.w(TAG, "capture screenshot failed: ${error.message}", error)
        }.getOrNull()
    }

    private suspend fun resolveTopActivity(): Pair<String?, String?> {
        val output = runCatching {
            executor.execute("dumpsys window | grep mCurrentFocus").stdout
        }.getOrNull().orEmpty()
        if (output.isBlank()) return null to null
        val focusComponent = output.substringAfterLast(" ").substringBefore("}")
        val activity = focusComponent.substringAfter("/", "")
        val pkg = focusComponent.substringBefore("/", "")
        return pkg.ifBlank { null } to activity.ifBlank { null }
    }
}
