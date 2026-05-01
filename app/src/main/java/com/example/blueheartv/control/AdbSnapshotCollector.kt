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
    private val screenshotFile = File(context.cacheDir, SCREENSHOT_FILE_NAME)

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
            screenshotFile.delete()
            val path = screenshotFile.absolutePath
            val result = executor.execute("screencap -p '$path'")
            if (!result.isSuccess) {
                Log.w(TAG, "screencap failed: ${result.stderr.ifBlank { result.stdout }}")
                return null
            }
            if (!screenshotFile.exists() || screenshotFile.length() <= 0L) {
                Log.w(TAG, "screencap produced empty file")
                return null
            }
            Base64.encodeToString(screenshotFile.readBytes(), Base64.NO_WRAP)
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
