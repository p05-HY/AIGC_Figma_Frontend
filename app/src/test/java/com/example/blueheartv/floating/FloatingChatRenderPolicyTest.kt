package com.example.blueheartv.floating

import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.model.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FloatingChatRenderPolicyTest {

    @Test
    fun processOnlyAssistantMessageIsHidden() {
        val userMessage = Message(
            id = "user-1",
            content = "帮我查询天气",
            isUser = true,
        )
        val processMessage = Message(
            id = "assistant-1",
            content = "",
            isUser = false,
            deliveryState = MessageDeliveryState.STREAMING,
            thinking = "正在分析用户位置",
            toolCalls = listOf(ToolCall(label = "查询天气")),
        )

        val result = listOf(userMessage, processMessage).toFloatingChatMessages()

        assertEquals(listOf(userMessage), result)
    }

    @Test
    fun finalAssistantMessageKeepsAnswerAndDropsProcessDetails() {
        val finalMessage = Message(
            id = "assistant-1",
            content = "今天多云，最高 28°C。",
            isUser = false,
            thinking = "先定位，再查询天气。",
            toolCalls = listOf(ToolCall(label = "查询天气")),
        )

        val result = listOf(finalMessage).toFloatingChatMessages()

        assertEquals(1, result.size)
        assertEquals(finalMessage.content, result.single().content)
        assertNull(result.single().thinking)
        assertNull(result.single().toolCalls)
    }

    @Test
    fun userMessageIsPreservedWithoutCopying() {
        val userMessage = Message(
            id = "user-1",
            content = "继续",
            isUser = true,
        )

        val result = listOf(userMessage).toFloatingChatMessages()

        assertSame(userMessage, result.single())
    }

    @Test
    fun widthClassificationSupportsSmallMediumAndLargeWindows() {
        assertEquals(FloatingChatSizeClass.SMALL, classifyFloatingChatWidth(240f))
        assertEquals(FloatingChatSizeClass.MEDIUM, classifyFloatingChatWidth(320f))
        assertEquals(FloatingChatSizeClass.LARGE, classifyFloatingChatWidth(420f))
    }
}
