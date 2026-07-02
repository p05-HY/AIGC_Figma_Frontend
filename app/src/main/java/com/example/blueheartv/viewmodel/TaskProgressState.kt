package com.example.blueheartv.viewmodel

import com.example.blueheartv.chat.ChatStreamEvent

data class TaskProgressState(
    val runId: String?,
    val threadId: String?,
    val taskTitle: String,
    val status: String,
    val currentStep: Int?,
    val totalSteps: Int?,
    val stepTitle: String,
    val phase: String?,
    val toolName: String?,
    val requiresConfirmation: Boolean,
    val confirmationId: String?,
    val canCancel: Boolean,
    val canTakeOver: Boolean,
    val message: String?,
) {
    val isTerminal: Boolean
        get() = status in TERMINAL_STATUSES

    companion object {
        private val TERMINAL_STATUSES = setOf("completed", "failed", "cancelled", "taken_over")
    }
}

object TaskProgressReducer {
    fun reduce(
        current: TaskProgressState?,
        event: ChatStreamEvent.TaskProgress,
    ): TaskProgressState {
        val previous = current?.takeUnless { isDistinctTask(it, event) }
        val status = event.status.ifBlank { previous?.status ?: "running" }
        val terminal = status in setOf("completed", "failed", "cancelled", "taken_over")
        val confirmationId = event.confirmationId ?: previous?.confirmationId?.takeUnless { terminal }
        val stepTitle = event.stepTitle
            ?: event.message
            ?: previous?.stepTitle
            ?: event.label
        return TaskProgressState(
            runId = event.runId ?: previous?.runId,
            threadId = event.threadId ?: previous?.threadId,
            taskTitle = event.taskTitle
                ?: previous?.taskTitle
                ?: fallbackTaskTitle(event),
            status = status,
            currentStep = event.currentStep ?: previous?.currentStep,
            totalSteps = event.totalSteps ?: previous?.totalSteps,
            stepTitle = stepTitle,
            phase = event.phase.ifBlank { previous?.phase },
            toolName = event.toolName ?: previous?.toolName,
            requiresConfirmation = !terminal && event.requiresConfirmation,
            confirmationId = confirmationId,
            canCancel = !terminal && event.canCancel,
            canTakeOver = !terminal && event.canTakeOver,
            message = event.message ?: previous?.message ?: stepTitle,
        )
    }

    private fun isDistinctTask(
        current: TaskProgressState,
        event: ChatStreamEvent.TaskProgress,
    ): Boolean {
        if (!event.runId.isNullOrBlank() && !current.runId.isNullOrBlank() && event.runId != current.runId) {
            return true
        }
        if (!event.threadId.isNullOrBlank() && !current.threadId.isNullOrBlank() && event.threadId != current.threadId) {
            return true
        }
        if (!event.taskTitle.isNullOrBlank() && event.taskTitle != current.taskTitle) {
            return true
        }
        if (current.isTerminal && event.status !in TERMINAL_STATUSES) {
            return true
        }
        if (event.phase == PHONE_TOOL_PHASE && event.taskTitle.isNullOrBlank()) {
            if (current.phase != PHONE_TOOL_PHASE) {
                return true
            }
            if (current.requiresConfirmation || !current.confirmationId.isNullOrBlank()) {
                return true
            }
            val incomingTool = event.toolName ?: event.label
            if (!current.toolName.isNullOrBlank() && incomingTool.isNotBlank() && incomingTool != current.toolName) {
                return true
            }
        }
        return false
    }

    private fun fallbackTaskTitle(event: ChatStreamEvent.TaskProgress): String {
        if (event.phase == PHONE_TOOL_PHASE) {
            return when (event.toolName ?: event.label) {
                "launch" -> "打开手机应用"
                "observe" -> "观察当前屏幕"
                "tap" -> "点击屏幕"
                "type" -> "输入内容"
                "swipe", "scroll" -> "滑动屏幕"
                "back" -> "返回上一页"
                "home" -> "回到桌面"
                "keyevent" -> "执行手机按键"
                "wait" -> "等待手机响应"
                "interact", "take_over" -> "等待手动接管"
                else -> "执行手机任务"
            }
        }
        return event.stepTitle
            ?: event.message
            ?: event.label.ifBlank { "执行手机任务" }
    }

    private const val PHONE_TOOL_PHASE = "phone_tool"
    private val TERMINAL_STATUSES = setOf("completed", "failed", "cancelled", "taken_over")
}
