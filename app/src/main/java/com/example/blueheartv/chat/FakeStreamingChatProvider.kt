package com.example.blueheartv.chat

import com.example.blueheartv.model.Message
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class FakeStreamingChatProvider : ChatProvider {
    override suspend fun streamReply(
        messages: List<Message>,
        onEvent: (ChatStreamEvent) -> Unit,
    ) {
        val lastUserContent = messages.lastOrNull { it.isUser }?.content?.trim().orEmpty()
        if (lastUserContent.isEmpty()) {
            onEvent(ChatStreamEvent.Error("输入为空", retryable = false))
            return
        }

        if (lastUserContent.contains("失败测试")) {
            delay(300.milliseconds)
            onEvent(ChatStreamEvent.Error("模拟网络错误，请稍后重试"))
            return
        }

        val isRouteQuery = lastUserContent.contains("路线") ||
                lastUserContent.contains("导航") ||
                lastUserContent.contains("地图")

        if (isRouteQuery) {
            streamRouteReply(onEvent)
        } else {
            streamGeneralReply(onEvent)
        }
    }

    private suspend fun streamRouteReply(onEvent: (ChatStreamEvent) -> Unit) {
        val toolCalls = listOf(
            "获取当前位置",
            "调用高德地图",
            "规划路线",
        )

        toolCalls.forEach { tool ->
            onEvent(ChatStreamEvent.ToolCallStarted(tool))
            delay(240.milliseconds)
            onEvent(ChatStreamEvent.ToolCallCompleted(tool))
        }

        val chunks = listOf(
            "已为你规划好前往南山科技园的路线。\n\n",
            "公共交通：推荐地铁 1 号线到高新园站 B 口，步行约 5 分钟。\n",
            "自驾/打车：优先走深南大道或北环大道，避开早晚高峰。\n",
            "如需我继续，我可以给你输出分时段出发建议。",
        )

        chunks.forEach { chunk ->
            delay(180.milliseconds)
            onEvent(ChatStreamEvent.TextDelta(chunk))
        }

        onEvent(ChatStreamEvent.Completed)
    }

    private suspend fun streamGeneralReply(onEvent: (ChatStreamEvent) -> Unit) {
        val chunks = listOf(
            "Choosing the best programming language depends on your goals. ",
            "For beginners, Python is usually the easiest start. ",
            "For web apps, JavaScript is essential. ",
            "If you want, I can suggest a learning path for your current level.",
        )

        chunks.forEach { chunk ->
            delay(150.milliseconds)
            onEvent(ChatStreamEvent.TextDelta(chunk))
        }

        onEvent(ChatStreamEvent.Completed)
    }
}
