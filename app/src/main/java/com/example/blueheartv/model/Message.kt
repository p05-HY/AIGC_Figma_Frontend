package com.example.blueheartv.model

enum class MessageDeliveryState {
    SENDING,
    STREAMING,
    COMPLETED,
    FAILED,
}

data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val deliveryState: MessageDeliveryState = MessageDeliveryState.COMPLETED,
    val toolCalls: List<ToolCall>? = null,
    val errorMessage: String? = null,
) {
    val isLoading: Boolean
        get() = deliveryState == MessageDeliveryState.SENDING ||
            deliveryState == MessageDeliveryState.STREAMING
}

data class ToolCall(
    val label: String,
    val isComplete: Boolean = false,
)

data class ChatHistory(
    val id: String,
    val title: String,
    val timestamp: String,
    val isCurrent: Boolean = false,
)

data class SmartRecommendation(
    val title: String,
    val subtitle: String,
)

data class PermissionItem(
    val name: String,
    val isGranted: Boolean = true,
)
