package com.example.blueheartv.ui.components

import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.model.ToolCallStatus
import java.util.Locale

enum class ToolDisplayType {
    OBSERVE,
    LAUNCH,
    TAP,
    TYPE,
    SWIPE,
    LONG_PRESS,
    DOUBLE_TAP,
    KEY_EVENT,
    INTERACT,
    SYSTEM,
    GENERIC,
}

data class ToolDisplayInfo(
    val title: String,
    val subtitle: String,
    val statusText: String,
    val type: ToolDisplayType = ToolDisplayType.GENERIC,
)

private data class ToolDisplaySpec(
    val title: String,
    val runningSubtitle: String,
    val completedSubtitle: String,
    val type: ToolDisplayType,
)

private val toolDisplaySpecs = mapOf(
    "observe" to ToolDisplaySpec("观察屏幕", "正在读取当前界面", "已读取当前界面", ToolDisplayType.OBSERVE),
    "launch" to ToolDisplaySpec("打开应用", "正在打开目标应用", "已打开目标应用", ToolDisplayType.LAUNCH),
    "open_app" to ToolDisplaySpec("打开应用", "正在打开目标应用", "已打开目标应用", ToolDisplayType.LAUNCH),
    "start_app" to ToolDisplaySpec("打开应用", "正在打开目标应用", "已打开目标应用", ToolDisplayType.LAUNCH),
    "tap" to ToolDisplaySpec("点击屏幕", "正在点击目标位置", "已完成点击", ToolDisplayType.TAP),
    "tap_element" to ToolDisplaySpec("点击屏幕", "正在点击目标位置", "已完成点击", ToolDisplayType.TAP),
    "click" to ToolDisplaySpec("点击屏幕", "正在点击目标位置", "已完成点击", ToolDisplayType.TAP),
    "type" to ToolDisplaySpec("输入内容", "正在输入内容", "已完成输入", ToolDisplayType.TYPE),
    "input_text" to ToolDisplaySpec("输入内容", "正在输入内容", "已完成输入", ToolDisplayType.TYPE),
    "type_text" to ToolDisplaySpec("输入内容", "正在输入内容", "已完成输入", ToolDisplayType.TYPE),
    "swipe" to ToolDisplaySpec("滑动屏幕", "正在滑动页面", "已完成滑动", ToolDisplayType.SWIPE),
    "scroll" to ToolDisplaySpec("滑动屏幕", "正在滑动页面", "已完成滑动", ToolDisplayType.SWIPE),
    "longpress" to ToolDisplaySpec("长按屏幕", "正在长按目标位置", "已完成长按", ToolDisplayType.LONG_PRESS),
    "long_press" to ToolDisplaySpec("长按屏幕", "正在长按目标位置", "已完成长按", ToolDisplayType.LONG_PRESS),
    "doubletap" to ToolDisplaySpec("双击屏幕", "正在双击目标位置", "已完成双击", ToolDisplayType.DOUBLE_TAP),
    "double_tap" to ToolDisplaySpec("双击屏幕", "正在双击目标位置", "已完成双击", ToolDisplayType.DOUBLE_TAP),
    "keyevent" to ToolDisplaySpec("发送按键", "正在执行系统按键", "已完成按键操作", ToolDisplayType.KEY_EVENT),
    "key_event" to ToolDisplaySpec("发送按键", "正在执行系统按键", "已完成按键操作", ToolDisplayType.KEY_EVENT),
    "back" to ToolDisplaySpec("返回上一页", "正在返回上一页", "已返回上一页", ToolDisplayType.KEY_EVENT),
    "home" to ToolDisplaySpec("回到桌面", "正在回到桌面", "已回到桌面", ToolDisplayType.KEY_EVENT),
    "interact" to ToolDisplaySpec("等待确认", "需要你在手机上确认", "已收到确认", ToolDisplayType.INTERACT),
    "take_over" to ToolDisplaySpec("等待接管", "需要你手动完成当前步骤", "已继续执行", ToolDisplayType.INTERACT),
    "list_apps" to ToolDisplaySpec("读取应用", "正在整理应用列表", "已整理应用列表", ToolDisplayType.SYSTEM),
    "create_event" to ToolDisplaySpec("创建日程", "正在创建日程", "已创建日程", ToolDisplayType.SYSTEM),
    "list_events" to ToolDisplaySpec("读取日程", "正在读取日程", "已读取日程", ToolDisplayType.SYSTEM),
    "update_event" to ToolDisplaySpec("更新日程", "正在更新日程", "已更新日程", ToolDisplayType.SYSTEM),
    "list_reminders" to ToolDisplaySpec("读取提醒", "正在读取提醒事项", "已读取提醒事项", ToolDisplayType.SYSTEM),
    "update_reminders" to ToolDisplaySpec("更新提醒", "正在更新提醒事项", "已更新提醒事项", ToolDisplayType.SYSTEM),
    "get_location" to ToolDisplaySpec("获取位置", "正在获取当前位置", "已获取当前位置", ToolDisplayType.SYSTEM),
)

fun displayToolCall(toolCall: ToolCall): ToolDisplayInfo {
    val spec = toolCall.displayKeys()
        .firstNotNullOfOrNull { key -> toolDisplaySpecs[key] }
        ?: genericSpecFor(toolCall.label)

    return ToolDisplayInfo(
        title = spec.title,
        subtitle = when (toolCall.status) {
            ToolCallStatus.RUNNING -> spec.runningSubtitle
            ToolCallStatus.COMPLETED -> spec.completedSubtitle
            ToolCallStatus.FAILED -> "操作未完成，可稍后重试"
        },
        statusText = when (toolCall.status) {
            ToolCallStatus.RUNNING -> "执行中"
            ToolCallStatus.COMPLETED -> "已完成"
            ToolCallStatus.FAILED -> "需要重试"
        },
        type = spec.type,
    )
}

private fun ToolCall.displayKeys(): List<String> =
    listOfNotNull(toolName, progressKey, label)
        .flatMap { value ->
            val normalized = value.normalizeToolKey()
            listOf(normalized, normalized.replace("_", ""))
        }
        .distinct()

private fun String.normalizeToolKey(): String =
    trim()
        .replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

private fun genericSpecFor(label: String): ToolDisplaySpec {
    val trimmed = label.trim()
    val title = if (trimmed.isNotBlank() && !trimmed.containsTechnicalMarker()) {
        trimmed.take(18)
    } else {
        "执行操作"
    }
    return ToolDisplaySpec(
        title = title,
        runningSubtitle = "正在处理当前任务",
        completedSubtitle = "已完成当前任务",
        type = ToolDisplayType.GENERIC,
    )
}

private fun String.containsTechnicalMarker(): Boolean {
    val lower = lowercase(Locale.US)
    val markers = listOf(
        "{",
        "}",
        "\"",
        "adb",
        "agent",
        "websocket",
        "intent",
        "activity",
        "shell",
        "package",
        "currentpackage",
        "mobile_agent",
        "com.",
        "android.",
        "_",
    )
    return markers.any { marker -> lower.contains(marker) }
}
