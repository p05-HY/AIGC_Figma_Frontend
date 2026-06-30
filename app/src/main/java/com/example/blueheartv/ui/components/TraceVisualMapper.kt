package com.example.blueheartv.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.blueheartv.model.TraceStepStatus

data class TraceVisualSpec(
    val label: String,
    val icon: ImageVector?,
    val accent: Color,
    val container: Color,
)

fun traceVisualSpec(
    kind: String,
    status: TraceStepStatus,
    colorScheme: ColorScheme,
): TraceVisualSpec {
    val accent = traceStatusAccent(status, colorScheme)
    val container = accent.copy(alpha = traceStatusContainerAlpha(status))
    val key = kind.normalizedVisualKey()
    val base = when (key) {
        "vision", "observation", "observe" -> "观察屏幕" to Icons.Outlined.Visibility
        "phone_action", "execute_phone_todo", "launch" -> "手机操作" to Icons.Outlined.PhoneAndroid
        "tap", "type", "swipe", "scroll", "back", "home", "wait" -> "手机操作" to Icons.Outlined.TouchApp
        "system", "external_tools_status", "list_apps" -> "系统能力" to Icons.Outlined.Build
        "life_service", "weather_query", "amap_mcp_tool", "get_location" -> "生活服务" to Icons.Outlined.Cloud
        "office", "feishu_cli_readonly", "wecom_cli_readonly" -> "办公协作" to Icons.Outlined.CalendarMonth
        "approval" -> "用户确认" to Icons.Outlined.CheckCircle
        "tool", "run_cli_command" -> "工具调用" to Icons.Outlined.Build
        "error" -> "错误" to Icons.Outlined.ErrorOutline
        "generic" -> "执行操作" to Icons.Outlined.AutoAwesome
        else -> "执行操作" to Icons.Outlined.Info
    }
    return TraceVisualSpec(
        label = base.first,
        icon = base.second,
        accent = accent,
        container = container,
    )
}

private fun traceStatusAccent(status: TraceStepStatus, colorScheme: ColorScheme): Color = when (status) {
    TraceStepStatus.QUEUED -> colorScheme.onSurfaceVariant
    TraceStepStatus.RUNNING -> colorScheme.primary
    TraceStepStatus.SUCCEEDED -> colorScheme.tertiary
    TraceStepStatus.FAILED -> colorScheme.error
    TraceStepStatus.CANCELLED -> colorScheme.onSurfaceVariant
    TraceStepStatus.WAITING_FOR_USER -> Color(0xFF9A6A18)
}

private fun traceStatusContainerAlpha(status: TraceStepStatus): Float = when (status) {
    TraceStepStatus.RUNNING -> 0.12f
    TraceStepStatus.WAITING_FOR_USER -> 0.14f
    TraceStepStatus.FAILED -> 0.10f
    else -> 0.08f
}

private fun String.normalizedVisualKey(): String =
    trim()
        .lowercase()
        .replace('-', '_')
        .replace(' ', '_')
