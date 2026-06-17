package com.example.blueheartv.floating

import com.example.blueheartv.model.Message

internal enum class FloatingChatSizeClass {
    SMALL,
    MEDIUM,
    LARGE,
}

internal fun classifyFloatingChatWidth(widthDp: Float): FloatingChatSizeClass {
    return when {
        widthDp < 280f -> FloatingChatSizeClass.SMALL
        widthDp < 380f -> FloatingChatSizeClass.MEDIUM
        else -> FloatingChatSizeClass.LARGE
    }
}

internal fun List<Message>.toFloatingChatMessages(): List<Message> {
    return mapNotNull { message ->
        when {
            message.isUser -> message
            message.content.isBlank() -> null
            message.thinking == null && message.toolCalls == null -> message
            else -> message.copy(
                thinking = null,
                toolCalls = null,
            )
        }
    }
}
