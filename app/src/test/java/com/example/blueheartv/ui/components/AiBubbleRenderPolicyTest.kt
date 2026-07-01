package com.example.blueheartv.ui.components

import com.example.blueheartv.model.AssistantTrace
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.model.TraceRunStatus
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus
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

    @Test
    fun completedSimpleConversationWithOnlyAnalysisTrace_hidesTrace() {
        val message = Message(
            id = "assistant-1",
            content = "OK",
            isUser = false,
            deliveryState = MessageDeliveryState.COMPLETED,
            trace = AssistantTrace(
                runId = "run-1",
                runStatus = TraceRunStatus.SUCCEEDED,
                hasTerminal = true,
                steps = listOf(
                    TraceStep(
                        id = "analysis",
                        kind = "summary",
                        title = "分析请求",
                        summary = "已完成请求处理。",
                        status = TraceStepStatus.SUCCEEDED,
                    ),
                ),
            ),
        )

        assertFalse(shouldRenderAssistantTrace(message, traceRenderEnabled = true))
    }

    @Test
    fun completedToolConversationWithPhoneTrace_keepsTraceVisible() {
        val message = Message(
            id = "assistant-1",
            content = "微信已打开。",
            isUser = false,
            deliveryState = MessageDeliveryState.COMPLETED,
            trace = AssistantTrace(
                runId = "run-1",
                runStatus = TraceRunStatus.SUCCEEDED,
                hasTerminal = true,
                steps = listOf(
                    TraceStep(
                        id = "launch",
                        kind = "phone_action",
                        title = "打开应用",
                        summary = "已发起打开应用操作。",
                        status = TraceStepStatus.SUCCEEDED,
                    ),
                ),
            ),
        )

        assertTrue(shouldRenderAssistantTrace(message, traceRenderEnabled = true))
    }
}
