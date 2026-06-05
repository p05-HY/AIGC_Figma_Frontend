package com.example.blueheartv.chat

sealed interface ChatStreamEvent {
    data class ToolCallStarted(
        val label: String,
        val args: String? = null,
    ) : ChatStreamEvent

    data class ToolCallCompleted(
        val label: String,
        val result: String? = null,
    ) : ChatStreamEvent

    data class ToolCallFailed(
        val label: String,
        val error: String? = null,
    ) : ChatStreamEvent

    data class TextDelta(
        val chunk: String,
        val invocationId: String? = null,
    ) : ChatStreamEvent

    data class TaskComplexity(
        val complexity: String,
        val trackSteps: Boolean,
        val reason: String,
        val message: String? = null,
    ) : ChatStreamEvent

    data class TaskProgress(
        val label: String,
        val status: String,
        val phase: String,
        val message: String? = null,
        val toolName: String? = null,
        val progressKey: String? = null,
        val currentStep: Int? = null,
        val totalSteps: Int? = null,
        val completedSteps: List<TaskProgressStep> = emptyList(),
        val error: String? = null,
    ) : ChatStreamEvent

    data class TaskProgressStep(
        val index: Int?,
        val name: String,
        val status: String,
    )

    data object Completed : ChatStreamEvent

    data class Error(
        val message: String,
        val retryable: Boolean = true,
    ) : ChatStreamEvent
}
