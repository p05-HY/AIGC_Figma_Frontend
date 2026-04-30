package com.example.blueheartv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.blueheartv.chat.ChatPrompt
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.chat.RemoteChatThread
import com.example.blueheartv.ui.screens.HomeScreen
import com.example.blueheartv.viewmodel.ChatSessionRepository
import com.example.blueheartv.viewmodel.ChatViewModel
import org.junit.Rule
import org.junit.Test

class HomeChatFlowUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sendMessage_showsMergedToolCallAndReply() {
        val repo = ChatSessionRepository(
            idProvider = { "test-${System.nanoTime()}" },
        )
        val viewModel = ChatViewModel(
            chatProvider = object : ChatProvider {
                override suspend fun createThread(titleHint: String?): RemoteChatThread {
                    return RemoteChatThread("thread-1", titleHint ?: "当前对话", 0L, emptyList())
                }

                override suspend fun loadThreads(limit: Int): List<RemoteChatThread> = emptyList()

                override suspend fun loadThread(threadId: String): RemoteChatThread? = null

                override suspend fun renameThread(threadId: String, title: String) = Unit

                override suspend fun deleteThread(threadId: String) = Unit

                override suspend fun streamReply(
                    threadId: String,
                    prompt: ChatPrompt,
                    onEvent: (ChatStreamEvent) -> Unit,
                ) {
                    onEvent(ChatStreamEvent.ToolCallStarted("获取当前位置"))
                    onEvent(ChatStreamEvent.ToolCallCompleted("获取当前位置"))
                    onEvent(ChatStreamEvent.TextDelta("这是回复正文"))
                    onEvent(ChatStreamEvent.Completed)
                }
            },
            repo = repo,
        )

        composeRule.setContent {
            HomeScreen(viewModel = viewModel)
        }

        composeRule.onNode(hasSetTextAction())
            .performTextInput("测试工具调用")
        composeRule.onNodeWithContentDescription("Send")
            .performClick()

        composeRule.onNodeWithText("获取当前位置")
            .assertIsDisplayed()
        composeRule.onNodeWithText("这是回复正文")
            .assertIsDisplayed()
    }
}
