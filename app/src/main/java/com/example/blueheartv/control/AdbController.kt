package com.example.blueheartv.control

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AdbController 是合并后 App 内部调用 ADB 执行能力的统一入口。
 * 原 ADB-Test 的 AdbService 通过 WebSocket 接收指令；
 * 这里改为直接函数调用，供 ChatViewModel 的路由层使用。
 */
class AdbController(context: Context) {

    private val executor = ShizukuAdbExecutor(context.packageName)

    /** 执行一条 shell 命令，返回 stdout 或错误信息 */
    suspend fun runShell(command: String): String = withContext(Dispatchers.IO) {
        try {
            val result = executor.execute(command)
            result.stdout.ifBlank { result.stderr }
        } catch (e: Exception) {
            "执行失败: ${e.message}"
        }
    }

    /** 模拟点击屏幕坐标 */
    suspend fun tap(x: Int, y: Int): String =
        runShell("input tap $x $y")

    /** 模拟滑动 */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): String =
        runShell("input swipe $x1 $y1 $x2 $y2 $durationMs")

    /** 输入文字（需要 ADB Keyboard 已安装） */
    suspend fun typeText(text: String): String =
        runShell("am broadcast -a ADB_INPUT_TEXT --es msg '${text.replace("'", "\\'")}'")

    /** 模拟按键（keycode 参考 Android KeyEvent） */
    suspend fun pressKey(keycode: Int): String =
        runShell("input keyevent $keycode")

    /** 启动应用 */
    suspend fun launchApp(packageName: String): String =
        runShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")

    /** 获取当前屏幕 UI 树（XML 文本），供 AI 识别元素位置 */
    suspend fun dumpUiTree(): String = withContext(Dispatchers.IO) {
        try {
            val snapshot = AdbSnapshotCollector(executor).collect()
            snapshot.ui ?: "无法获取 UI 树"
        } catch (e: Exception) {
            "UI 树采集失败: ${e.message}"
        }
    }

    fun destroy() {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { executor.destroy() }
        }
    }
}
