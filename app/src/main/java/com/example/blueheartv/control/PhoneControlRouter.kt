package com.example.blueheartv.control

sealed class ControlAction {
    data class Tap(val x: Int, val y: Int) : ControlAction()
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : ControlAction()
    data class LaunchApp(val packageName: String) : ControlAction()
    data class PressKey(val keycode: Int) : ControlAction()
    data class TypeText(val text: String) : ControlAction()
    data class RunShell(val command: String) : ControlAction()
    object GoHome : ControlAction()
    object GoBack : ControlAction()
    object DumpScreen : ControlAction()
}

object PhoneControlRouter {
    fun parse(userInput: String): ControlAction? {
        val input = userInput.trim()
        return when {
            input.startsWith("执行shell:") || input.startsWith("shell:") ->
                ControlAction.RunShell(input.substringAfter(":").trim())
            input.contains("返回桌面") || input.contains("回到主页") ->
                ControlAction.GoHome
            input.contains("返回上一页") || input.contains("按返回键") ->
                ControlAction.GoBack
            input.startsWith("打开应用") || input.startsWith("启动应用") -> {
                val pkg = input.substringAfter("应用").trim()
                if (pkg.isNotBlank()) ControlAction.LaunchApp(pkg) else null
            }
            input.startsWith("点击坐标") -> {
                val parts = input.removePrefix("点击坐标").trim().split(" ")
                val x = parts.getOrNull(0)?.toIntOrNull()
                val y = parts.getOrNull(1)?.toIntOrNull()
                if (x != null && y != null) ControlAction.Tap(x, y) else null
            }
            input.contains("截图") || input.contains("看一下屏幕") || input.contains("当前界面") ->
                ControlAction.DumpScreen
            else -> null
        }
    }
}
