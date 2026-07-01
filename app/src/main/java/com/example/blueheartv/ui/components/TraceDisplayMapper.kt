package com.example.blueheartv.ui.components

import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus

internal data class TraceStepDisplayModel(
    val stepId: String,
    val title: String,
    val summary: String,
    val detailLines: List<String>,
)

private enum class TraceStepIntent {
    UNDERSTAND,
    OPEN_APP,
    CONFIRM,
    PHONE_ACTION,
    OBSERVE,
    WEATHER,
    GENERIC,
}

private data class KnownAppRule(
    val pattern: Regex,
    val displayName: String,
)

private val knownAppRules = listOf(
    KnownAppRule(Regex("\\bcom\\.tencent\\.mm\\b", RegexOption.IGNORE_CASE), "微信"),
    KnownAppRule(Regex("微信"), "微信"),
    KnownAppRule(Regex("\\bcom\\.ss\\.android\\.lark\\b", RegexOption.IGNORE_CASE), "飞书"),
    KnownAppRule(Regex("飞书|\\blark\\b", RegexOption.IGNORE_CASE), "飞书"),
    KnownAppRule(Regex("\\bcom\\.android\\.settings\\b", RegexOption.IGNORE_CASE), "系统设置"),
    KnownAppRule(Regex("系统设置|\\bsystem settings\\b|\\bsettings\\b", RegexOption.IGNORE_CASE), "系统设置"),
    KnownAppRule(Regex("浏览器|\\bbrowser\\b|\\bcom\\.android\\.browser\\b", RegexOption.IGNORE_CASE), "浏览器"),
    KnownAppRule(Regex("电话|\\bdialer\\b|\\bphone\\b|\\bcom\\.android\\.dialer\\b", RegexOption.IGNORE_CASE), "电话"),
    KnownAppRule(Regex("相机|\\bcamera\\b|\\bcom\\.android\\.camera\\b", RegexOption.IGNORE_CASE), "相机"),
    KnownAppRule(Regex("相册|图库|\\bgallery\\b|\\balbum\\b|\\bcom\\.miui\\.gallery\\b", RegexOption.IGNORE_CASE), "相册"),
)

private val rawPackageNamePattern = Regex(
    "\\b[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*){2,}\\b",
    RegexOption.IGNORE_CASE,
)

private const val MAX_DISPLAY_SUMMARY_CHARS = 120
private const val MAX_DISPLAY_DETAIL_CHARS = 96

internal fun traceStepDisplayModels(
    steps: List<TraceStep>,
    traceSummary: String? = null,
    displayContext: String? = null,
): List<TraceStepDisplayModel> {
    val visibleSteps = steps.filter { it.visibleToUser }
    val sharedTargetApp = detectKnownAppName(
        listOfNotNull(traceSummary, displayContext) + visibleSteps.flatMap { it.rawDisplayTexts() },
    )
    val displayModels = visibleSteps.map { step ->
        val stepApp = detectKnownAppName(step.rawDisplayTexts())
        val targetApp = stepApp ?: sharedTargetApp
        val intent = classifyStepIntent(step = step, targetApp = targetApp)
        val title = productTitle(step = step, intent = intent, targetApp = targetApp)
        val summary = productSummary(step = step, intent = intent, targetApp = targetApp)
        TraceStepDisplayModel(
            stepId = step.id,
            title = title,
            summary = summary,
            detailLines = productDetailLines(step = step, intent = intent, targetApp = targetApp),
        )
    }
    return avoidRepeatedTitles(displayModels)
}

internal fun traceStepDisplayModel(
    step: TraceStep,
    traceSummary: String? = null,
    displayContext: String? = null,
): TraceStepDisplayModel =
    traceStepDisplayModels(listOf(step), traceSummary, displayContext).single()

internal fun traceHeaderActivityText(steps: List<TraceStep>, displayContext: String? = null): String {
    val displays = traceStepDisplayModels(steps, displayContext = displayContext)
    val activeStep = steps.lastOrNull {
        it.visibleToUser && it.status in setOf(TraceStepStatus.RUNNING, TraceStepStatus.QUEUED)
    }
    val activeTitle = activeStep?.let { step ->
        displays.firstOrNull { it.stepId == step.id }?.title
    }
    return when {
        activeTitle != null && activeTitle.startsWith("打开") -> activeTitle.replaceFirst("打开", "正在打开")
        activeTitle != null && activeTitle != "理解需求" -> activeTitle
        steps.any { it.visibleToUser && it.kind.normalizedDisplayKey().contains("phone") } -> "正在操作手机"
        steps.any { it.visibleToUser } -> "正在处理步骤"
        else -> "正在准备任务"
    }
}

private fun classifyStepIntent(step: TraceStep, targetApp: String?): TraceStepIntent {
    val key = "${step.kind} ${step.title}".normalizedDisplayKey()
    val text = step.rawDisplayTexts().joinToString(" ").normalizedDisplayKey()
    return when {
        key.contains("分析请求") ||
            key.contains("理解需求") ||
            key.contains("request_analysis") ||
            text.contains("请求处理") -> TraceStepIntent.UNDERSTAND

        key.contains("weather_query") || text.contains("查询天气") -> TraceStepIntent.WEATHER

        key.contains("observe") || key.contains("observation") || text.contains("观察屏幕") -> TraceStepIntent.OBSERVE

        key.contains("launch") ||
            key.contains("open_app") ||
            text.contains("打开应用") ||
            text.contains("目标应用") ||
            (targetApp != null && key.contains("phone_action")) -> TraceStepIntent.OPEN_APP

            targetApp != null && (
                key.contains("execute_phone_todo") ||
                    key.contains("controlled_action") ||
                    text.contains("受控操作") ||
                    key.contains("执行操作")
                ) -> TraceStepIntent.CONFIRM

        key.contains("phone_action") ||
            key.contains("tap") ||
            key.contains("swipe") ||
            key.contains("type") ||
            key.contains("back") ||
            key.contains("home") -> TraceStepIntent.PHONE_ACTION

        else -> TraceStepIntent.GENERIC
    }
}

private fun productTitle(
    step: TraceStep,
    intent: TraceStepIntent,
    targetApp: String?,
): String {
    return when (intent) {
        TraceStepIntent.UNDERSTAND -> "理解需求"
        TraceStepIntent.OPEN_APP -> "打开${targetApp ?: "目标应用"}"
        TraceStepIntent.CONFIRM -> if (
            targetApp != null ||
            step.summary.contains("完成") ||
            step.title.contains("执行操作")
        ) {
            "确认完成"
        } else {
            "确认结果"
        }
        TraceStepIntent.PHONE_ACTION -> phoneActionTitle(step)
        TraceStepIntent.OBSERVE -> "观察屏幕"
        TraceStepIntent.WEATHER -> "查询天气"
        TraceStepIntent.GENERIC -> if (step.summary.contains("完成")) {
            "处理完成"
        } else {
            safeFreeText(step.title, targetApp)
            ?.takeUnless { it.isGenericStepTitle() }
            ?: statusTitle(step.status)
        }
    }
}

private fun phoneActionTitle(step: TraceStep): String {
    val key = "${step.kind} ${step.title}".normalizedDisplayKey()
    return when {
        key.contains("tap") -> "点击屏幕"
        key.contains("type") -> "输入内容"
        key.contains("swipe") || key.contains("scroll") -> "滑动页面"
        key.contains("back") -> "返回上一级"
        key.contains("home") -> "回到桌面"
        else -> "操作手机"
    }
}

private fun productSummary(
    step: TraceStep,
    intent: TraceStepIntent,
    targetApp: String?,
): String {
    val safeRawSummary = safeFreeText(step.summary, targetApp)?.takeUnless { it.isTemplateTraceText() }
    return when (intent) {
        TraceStepIntent.UNDERSTAND -> {
            if (targetApp != null) "准备为你打开$targetApp。" else "已理解你的需求。"
        }
        TraceStepIntent.OPEN_APP -> {
            val appSuffix = targetApp?.let { it + "操作" } ?: "操作"
            val openingTarget = targetApp ?: "目标应用"
            when {
                targetApp != null &&
                    (step.status == TraceStepStatus.RUNNING || step.status == TraceStepStatus.QUEUED) -> "正在打开$openingTarget。"
                step.summary.contains("已发起") || step.summary.contains("尝试打开") -> "已发起打开$appSuffix。"
                step.status == TraceStepStatus.RUNNING || step.status == TraceStepStatus.QUEUED -> "正在打开$openingTarget。"
                step.status == TraceStepStatus.SUCCEEDED -> "已发起打开$appSuffix。"
                else -> safeRawSummary ?: statusSummary(step.status)
            }
        }
        TraceStepIntent.CONFIRM -> {
            when {
                targetApp != null && step.status == TraceStepStatus.SUCCEEDED -> "${targetApp}已打开。"
                targetApp != null && step.summary.contains("完成") -> "${targetApp}已打开。"
                step.summary.contains("完成") -> "已完成处理。"
                step.status == TraceStepStatus.RUNNING || step.status == TraceStepStatus.QUEUED -> "正在确认处理结果。"
                else -> safeRawSummary ?: statusSummary(step.status)
            }
        }
        TraceStepIntent.PHONE_ACTION -> safeRawSummary ?: statusSummary(step.status)
        TraceStepIntent.OBSERVE -> safeRawSummary ?: "正在确认当前屏幕状态。"
        TraceStepIntent.WEATHER -> safeRawSummary ?: "正在查询天气信息。"
        TraceStepIntent.GENERIC -> safeRawSummary ?: if (step.summary.contains("完成")) {
            "已完成处理。"
        } else {
            statusSummary(step.status)
        }
    }.boundedDisplayText(MAX_DISPLAY_SUMMARY_CHARS)
}

private fun productDetailLines(
    step: TraceStep,
    intent: TraceStepIntent,
    targetApp: String?,
): List<String> {
    return step.details
        .filter { it.visibleToUser && it.title.isNotBlank() && it.text.isNotBlank() }
        .mapNotNull { productDetailLine(detail = it, intent = intent, targetApp = targetApp) }
        .distinct()
        .take(4)
}

private fun productDetailLine(
    detail: TraceDetail,
    intent: TraceStepIntent,
    targetApp: String?,
): String? {
    val detailApp = detectKnownAppName(listOf(detail.title, detail.text)) ?: targetApp
    val safeText = safeFreeText(detail.text, detailApp)
    if (safeText == null &&
        (containsInternalTraceData(detail.text) || rawPackageNamePattern.containsMatchIn(replaceKnownAppMarkers(detail.text)))
    ) {
        return null
    }
    val label = when (detail.kind) {
        TraceDetailKind.REASONING_SUMMARY -> "理解"
        TraceDetailKind.PLAN -> "计划"
        TraceDetailKind.DECISION -> "判断"
        TraceDetailKind.TOOL_CALL -> "动作"
        TraceDetailKind.TOOL_ARGS_SUMMARY -> "目标"
        TraceDetailKind.TOOL_RESULT -> "进展"
        TraceDetailKind.OBSERVATION -> "观察"
        TraceDetailKind.RETRY -> "重试"
        TraceDetailKind.WARNING -> "提醒"
        TraceDetailKind.ERROR -> "异常"
    }
    val text = when (detail.kind) {
        TraceDetailKind.TOOL_CALL -> when (intent) {
            TraceStepIntent.OPEN_APP -> "打开${detailApp ?: "目标应用"}"
            TraceStepIntent.CONFIRM -> detailApp?.let { "确认${it}已打开" } ?: "确认处理结果"
            else -> safeText
        }
        TraceDetailKind.TOOL_ARGS_SUMMARY -> detailApp ?: safeText
        TraceDetailKind.TOOL_RESULT -> when (intent) {
            TraceStepIntent.OPEN_APP -> "已发起打开${detailApp?.let { it + "操作" } ?: "操作"}。"
            TraceStepIntent.CONFIRM -> detailApp?.let { "${it}已打开。" } ?: "已完成处理。"
            else -> safeText
        }
        else -> safeText
    }?.takeUnless { it.isTemplateTraceText() && detail.kind !in productizedTemplateDetailKinds() }
        ?: return null

    return "$label：${text.boundedDisplayText(MAX_DISPLAY_DETAIL_CHARS)}"
}

private fun productizedTemplateDetailKinds(): Set<TraceDetailKind> =
    setOf(
        TraceDetailKind.TOOL_CALL,
        TraceDetailKind.TOOL_ARGS_SUMMARY,
        TraceDetailKind.TOOL_RESULT,
    )

private fun avoidRepeatedTitles(displays: List<TraceStepDisplayModel>): List<TraceStepDisplayModel> {
    val seen = mutableMapOf<String, Int>()
    return displays.map { display ->
        val count = seen.getOrDefault(display.title, 0)
        seen[display.title] = count + 1
        if (count == 0) {
            display
        } else {
            val nextTitle = when {
                display.title.startsWith("打开") -> "确认打开结果"
                display.title == "完成手机操作" -> "确认完成"
                display.title == "确认结果" -> "确认完成"
                display.title == "操作手机" -> "继续操作手机"
                display.title == "处理步骤" -> "继续处理"
                else -> "${display.title}${count + 1}"
            }
            display.copy(title = nextTitle)
        }
    }
}

private fun detectKnownAppName(texts: List<String>): String? {
    val text = texts.joinToString(" ")
    return knownAppRules.firstOrNull { rule -> rule.pattern.containsMatchIn(text) }?.displayName
}

private fun safeFreeText(raw: String, targetApp: String?): String? {
    if (raw.isBlank()) return null
    if (containsInternalTraceData(raw)) return null
    val withKnownApps = replaceKnownAppMarkers(raw.trim())
    if (rawPackageNamePattern.containsMatchIn(withKnownApps)) return null
    if (withKnownApps.containsInternalEventName()) return null
    return withKnownApps
        .replace("execute_phone_todo", "操作手机")
        .replace("tool_call", "使用工具")
        .replace("phone_action", "操作手机")
        .replace("open_app", "打开应用")
        .replace("controlled_action", "完成手机操作")
        .replace("目标应用", targetApp ?: "目标应用")
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun replaceKnownAppMarkers(raw: String): String {
    var text = raw
    knownAppRules.forEach { rule ->
        text = rule.pattern.replace(text, rule.displayName)
    }
    return text
}

private fun String.containsInternalEventName(): Boolean {
    val key = lowercase()
    return listOf(
        "trace.v1",
        "assistant.delta",
        "stream.eof",
        "stream.error",
        "run.terminal",
        "task_progress",
        "task_complexity",
    ).any { it in key }
}

private fun String.isGenericStepTitle(): Boolean =
    this in setOf("执行操作", "执行手机操作", "手机操作", "工具调用", "使用工具")

private fun String.isTemplateTraceText(): Boolean =
    this in setOf(
        "执行过程。",
        "已完成请求处理。",
        "正在执行。",
        "已完成。",
        "打开应用。",
        "尝试打开目标应用。",
        "已发起打开应用操作。",
        "正在执行受控操作。",
        "已完成受控操作。",
    )

private fun statusTitle(status: TraceStepStatus): String = when (status) {
    TraceStepStatus.QUEUED -> "准备执行"
    TraceStepStatus.RUNNING -> "处理步骤"
    TraceStepStatus.SUCCEEDED -> "处理完成"
    TraceStepStatus.FAILED -> "处理未完成"
    TraceStepStatus.CANCELLED -> "已取消"
    TraceStepStatus.WAITING_FOR_USER -> "等待你确认"
}

private fun statusSummary(status: TraceStepStatus): String = when (status) {
    TraceStepStatus.QUEUED -> "等待开始。"
    TraceStepStatus.RUNNING -> "正在处理。"
    TraceStepStatus.SUCCEEDED -> "已完成。"
    TraceStepStatus.FAILED -> "这一步未能完成。"
    TraceStepStatus.CANCELLED -> "这一步已停止。"
    TraceStepStatus.WAITING_FOR_USER -> "需要你确认后继续。"
}

private fun TraceStep.rawDisplayTexts(): List<String> =
    listOf(kind, title, summary) + details.flatMap { listOf(it.title, it.text) }

private fun String.normalizedDisplayKey(): String =
    trim()
        .lowercase()
        .replace('-', '_')
        .replace(' ', '_')

private fun String.boundedDisplayText(limit: Int): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= limit) normalized else normalized.take(limit - 1) + "…"
}
