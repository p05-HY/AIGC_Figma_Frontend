package com.example.blueheartv.ui.components

import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.model.ToolCallStatus

data class ToolProgressSummary(
    val label: String,
    val progress: Float?,
    val indeterminate: Boolean,
)

fun summarizeToolProgress(toolCalls: List<ToolCall>): ToolProgressSummary {
    val explicit = toolCalls.lastOrNull {
        it.status == ToolCallStatus.RUNNING && it.totalSteps != null && it.totalSteps > 0 && it.currentStep != null
    } ?: toolCalls.lastOrNull {
        it.totalSteps != null && it.totalSteps > 0 && it.currentStep != null
    }
    if (explicit != null) {
        val total = explicit.totalSteps ?: 1
        val current = (explicit.currentStep ?: 0).coerceIn(0, total)
        return ToolProgressSummary(
            label = "执行进度 $current/$total",
            progress = current.toFloat() / total.toFloat(),
            indeterminate = false,
        )
    }

    val total = toolCalls.size.coerceAtLeast(1)
    val completed = toolCalls.count { it.status == ToolCallStatus.COMPLETED }
    val running = toolCalls.any { it.status == ToolCallStatus.RUNNING }
    if (running) {
        return ToolProgressSummary(
            label = "正在执行 $completed/$total",
            progress = null,
            indeterminate = true,
        )
    }

    return ToolProgressSummary(
        label = "执行进度 $completed/$total",
        progress = completed.toFloat() / total.toFloat(),
        indeterminate = false,
    )
}
