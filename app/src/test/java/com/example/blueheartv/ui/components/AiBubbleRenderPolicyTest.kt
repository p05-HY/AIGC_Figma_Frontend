package com.example.blueheartv.ui.components

import com.example.blueheartv.model.AssistantTrace
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBubbleRenderPolicyTest {

    @Test
    fun streamingBlankMessageWithTrace_doesNotUseSkeletonPlaceholder() {
        val message = Message(
            id = "assistant-1",
            content = "",
            isUser = false,
            deliveryState = MessageDeliveryState.STREAMING,
            trace = AssistantTrace(
                runId = "run-1",
                summary = "已接收请求，正在准备任务。",
            ),
        )

        assertFalse(shouldShowAiLoadingSkeleton(message, traceRenderEnabled = true))
    }

    @Test
    fun streamingBlankMessageWithoutTrace_usesSkeletonPlaceholder() {
        val message = Message(
            id = "assistant-1",
            content = "",
            isUser = false,
            deliveryState = MessageDeliveryState.STREAMING,
        )

        assertTrue(shouldShowAiLoadingSkeleton(message, traceRenderEnabled = true))
    }
}
