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

    data object Completed : ChatStreamEvent

    data class Error(
        val message: String,
        val retryable: Boolean = true,
    ) : ChatStreamEvent
}
