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
        val status = event.status.ifBlank { current?.status ?: "running" }
        val terminal = status in setOf("completed", "failed", "cancelled", "taken_over")
        val confirmationId = event.confirmationId ?: current?.confirmationId?.takeUnless { terminal }
        val stepTitle = event.stepTitle
            ?: event.message
            ?: current?.stepTitle
            ?: event.label
        return TaskProgressState(
            runId = event.runId ?: current?.runId,
            threadId = event.threadId ?: current?.threadId,
            taskTitle = event.taskTitle
                ?: current?.taskTitle
                ?: event.label,
            status = status,
            currentStep = event.currentStep ?: current?.currentStep,
            totalSteps = event.totalSteps ?: current?.totalSteps,
            stepTitle = stepTitle,
            phase = event.phase.ifBlank { current?.phase },
            toolName = event.toolName ?: current?.toolName,
            requiresConfirmation = !terminal && event.requiresConfirmation,
            confirmationId = confirmationId,
            canCancel = !terminal && event.canCancel,
            canTakeOver = !terminal && event.canTakeOver,
            message = event.message ?: current?.message ?: stepTitle,
        )
    }
}
