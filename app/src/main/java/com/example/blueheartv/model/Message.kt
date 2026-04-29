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

data class ChatAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val base64Data: String,
)

data class ToolCall(
    val label: String,
    val isComplete: Boolean = false,
)

data class ChatHistory(
    val id: String,
    val title: String,
    val timestamp: String,
    val isCurrent: Boolean = false,
    val isPinned: Boolean = false,
)

data class SmartRecommendation(
    val title: String,
    val subtitle: String,
)

val defaultRecommendations = listOf(
    SmartRecommendation("您下午3点有产品评审会议", "需要我帮您准备会议材料和议程摘要？"),
    SmartRecommendation("今天深圳多云转晴 28°C", "出门建议带把伞，下午可能有阵雨"),
    SmartRecommendation("您有3个未读快递通知", "需要我帮您查看物流详情吗？"),
)

data class PermissionItem(
    val name: String,
    val isGranted: Boolean = true,
)
