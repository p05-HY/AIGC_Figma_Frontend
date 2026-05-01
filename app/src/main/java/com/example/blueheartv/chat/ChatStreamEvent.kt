package com.example.blueheartv.chat

sealed interface ChatStreamEvent {
    data class ToolCallStarted(val label: String) : ChatStreamEvent

    data class ToolCallCompleted(val label: String) : ChatStreamEvent

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
