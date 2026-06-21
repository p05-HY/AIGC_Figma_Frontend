package com.example.blueheartv.ui.components

import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ToolDisplayMapperTest {

    @Test
    fun displayToolCall_launchHidesPackageName() {
        val display = displayToolCall(
            ToolCall(
                label = "launch",
                status = ToolCallStatus.RUNNING,
                args = """{"package":"com.tencent.mm","activity":"LauncherUI"}""",
            ),
        )

        assertEquals("打开应用", display.title)
        assertEquals("正在打开目标应用", display.subtitle)
        assertFalse(display.containsDebugText())
    }

    @Test
    fun displayToolCall_tapHidesCoordinates() {
        val display = displayToolCall(
            ToolCall(
                label = "tap",
                status = ToolCallStatus.COMPLETED,
                args = """{"x":453,"y":1892}""",
            ),
        )

        assertEquals("点击屏幕", display.title)
        assertEquals("已完成", display.statusText)
        assertFalse(display.containsDebugText())
    }

    @Test
    fun displayToolCall_unknownTechnicalNameUsesGenericAction() {
        val display = displayToolCall(
            ToolCall(
                label = "mobile_agent_execute_intent",
                status = ToolCallStatus.FAILED,
                error = "Intent action android.intent.action.VIEW failed for package com.demo",
            ),
        )

        assertEquals("执行操作", display.title)
        assertEquals("需要重试", display.statusText)
        assertFalse(display.containsDebugText())
    }

    private fun ToolDisplayInfo.containsDebugText(): Boolean {
        val visible = listOfNotNull(title, subtitle, statusText).joinToString(" ")
        val debugMarkers = listOf(
            "{",
            "}",
            "\"x\"",
            "\"y\"",
            "package",
            "com.",
            "Intent",
            "Activity",
            "shell",
            "ADB",
            "currentPackage",
        )
        return debugMarkers.any { marker -> visible.contains(marker, ignoreCase = true) }
    }
}
