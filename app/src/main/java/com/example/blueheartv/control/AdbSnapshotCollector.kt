package com.example.blueheartv.control

class AdbSnapshotCollector(
    private val executor: ShizukuAdbExecutor
) {

    suspend fun collect(): AdbSnapshot {
        val screenshot = runCatching {
            executor.execute("screencap -p | base64").stdout.replace("\n", "").ifBlank { null }
        }.getOrNull()

        val ui = AdbAccessibilityService.dumpUiTree()
        val topActivity = resolveTopActivity()

        return AdbSnapshot(
            screenshot = screenshot,
            ui = ui,
            currentPackage = AdbAccessibilityService.currentPackageName() ?: topActivity.first,
            activity = AdbAccessibilityService.currentActivityName() ?: topActivity.second
        )
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
