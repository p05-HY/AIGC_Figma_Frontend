package com.example.blueheartv.ui.components

import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus

internal const val INTERNAL_TRACE_DATA_MESSAGE = "该详情包含内部执行数据，已隐藏。"

private const val MAX_TRACE_TITLE_CHARS = 48
private const val MAX_TRACE_SUMMARY_CHARS = 240
private const val MAX_TRACE_DETAIL_TEXT_CHARS = 500

private val internalTraceMarkers = listOf(
    "<think",
    "tool_calls",
    "invalid_tool_calls",
    "additional_kwargs",
    "response_metadata",
    "api_key",
    "access_token",
    "authorization",
    "base64,",
    "<node",
    "<hierarchy",
    "\"kwargs\"",
    "\"messages\"",
    "{",
    "}",
)

fun userFacingTraceTitle(step: TraceStep): String {
    val rawTitle = step.title.trim()
    val mapped = traceToolCopy(rawTitle)
        ?: traceToolCopy(step.kind)
        ?: traceKindCopy(step.kind)
        ?: rawTitle.takeIf { it.isReadableChineseLabel() }
        ?: statusTitleFallback(step.status)
    return mapped.boundedText(MAX_TRACE_TITLE_CHARS)
}

fun userFacingTraceSummary(step: TraceStep): String {
    val rawSummary = step.summary.trim()
    if (rawSummary.isInternalTraceText()) return INTERNAL_TRACE_DATA_MESSAGE
    return rawSummary
        .takeIf { it.isNotBlank() }
        ?.boundedText(MAX_TRACE_SUMMARY_CHARS)
        ?: statusSummaryFallback(step.status)
}

fun userFacingDetailTitle(detail: TraceDetail): String {
    val rawTitle = detail.title.trim()
    val mapped = traceDetailKindCopy(detail.kind)
        ?: traceToolCopy(rawTitle)
        ?: rawTitle.takeIf { it.isReadableChineseLabel() }
        ?: "详情"
    return mapped.boundedText(MAX_TRACE_TITLE_CHARS)
}

fun userFacingDetailText(detail: TraceDetail): String {
    val rawText = detail.text.trim()
    if (rawText.isInternalTraceText()) return INTERNAL_TRACE_DATA_MESSAGE
    return rawText
        .takeIf { it.isNotBlank() }
        ?.boundedText(MAX_TRACE_DETAIL_TEXT_CHARS)
        ?: "暂无更多说明。"
}

internal fun containsInternalTraceData(text: String): Boolean = text.isInternalTraceText()

internal fun compactTraceSummary(text: String): String =
    if (text.isInternalTraceText()) INTERNAL_TRACE_DATA_MESSAGE else text.trim().boundedText(MAX_TRACE_SUMMARY_CHARS)

private fun statusTitleFallback(status: TraceStepStatus): String = when (status) {
    TraceStepStatus.QUEUED -> "准备执行"
    TraceStepStatus.RUNNING -> "正在执行"
    TraceStepStatus.SUCCEEDED -> "执行完成"
    TraceStepStatus.FAILED -> "处理未完成"
    TraceStepStatus.CANCELLED -> "已取消"
    TraceStepStatus.WAITING_FOR_USER -> "等待你确认"
}

private fun statusSummaryFallback(status: TraceStepStatus): String = when (status) {
    TraceStepStatus.QUEUED -> "等待开始"
    TraceStepStatus.RUNNING -> "正在执行"
    TraceStepStatus.SUCCEEDED -> "已完成"
    TraceStepStatus.FAILED -> "未完成"
    TraceStepStatus.CANCELLED -> "已取消"
    TraceStepStatus.WAITING_FOR_USER -> "等待用户确认"
}

private fun traceKindCopy(kind: String): String? = when (kind.normalizedTraceKey()) {
    "vision", "observation", "observe" -> "观察屏幕"
    "phone_action" -> "执行手机操作"
    "system" -> "系统能力"
    "life_service" -> "生活服务"
    "office" -> "办公协作"
    "approval" -> "等待确认"
    "tool" -> "工具调用"
    "generic" -> "执行操作"
    "error" -> "错误"
    else -> null
}

private fun traceDetailKindCopy(kind: TraceDetailKind): String? = when (kind) {
    TraceDetailKind.REASONING_SUMMARY -> "理解"
    TraceDetailKind.PLAN -> "计划"
    TraceDetailKind.DECISION -> "判断"
    TraceDetailKind.TOOL_CALL -> "调用"
    TraceDetailKind.TOOL_ARGS_SUMMARY -> "参数"
    TraceDetailKind.TOOL_RESULT -> "结果"
    TraceDetailKind.OBSERVATION -> "观察"
    TraceDetailKind.RETRY -> "重试"
    TraceDetailKind.WARNING -> "提醒"
    TraceDetailKind.ERROR -> "错误"
}

private fun traceToolCopy(value: String): String? = when (value.normalizedTraceKey()) {
    "observe" -> "观察屏幕"
    "launch" -> "打开应用"
    "tap" -> "点击屏幕"
    "type" -> "输入内容"
    "swipe", "scroll" -> "滑动页面"
    "back" -> "返回上一级"
    "home" -> "回到桌面"
    "wait" -> "等待页面"
    "execute_phone_todo" -> "执行手机操作"
    "weather_query" -> "查询天气"
    "amap_mcp_tool" -> "调用高德服务"
    "external_tools_status" -> "检查外部工具"
    "list_apps" -> "读取应用"
    "list_events" -> "读取日程"
    "create_event" -> "创建日程"
    "update_event" -> "修改日程"
    "get_location" -> "获取位置"
    "feishu_cli_readonly" -> "查询飞书"
    "wecom_cli_readonly" -> "查询企业微信"
    "run_cli_command" -> "调用外部服务"
    else -> null
}

private fun String.normalizedTraceKey(): String =
    trim()
        .lowercase()
        .replace('-', '_')
        .replace(' ', '_')

private fun String.isReadableChineseLabel(): Boolean =
    isNotBlank() && any { it in '\u4e00'..'\u9fff' } && !isInternalTraceText()

private fun String.isInternalTraceText(): Boolean {
    val lower = lowercase()
    return internalTraceMarkers.any { marker -> marker in lower }
}

private fun String.boundedText(limit: Int): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= limit) normalized else normalized.take(limit - 1) + "…"
}
