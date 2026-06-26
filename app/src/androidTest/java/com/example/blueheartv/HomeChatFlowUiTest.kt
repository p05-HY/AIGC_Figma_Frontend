package com.example.blueheartv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.chat.ChatPrompt
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.chat.RemoteChatThread
import com.example.blueheartv.model.Message
import com.example.blueheartv.ui.screens.HomeScreen
import com.example.blueheartv.ui.screens.SettingsScreen
import com.example.blueheartv.viewmodel.ChatSessionRepository
import com.example.blueheartv.viewmodel.ChatViewModel
import org.junit.Rule
import org.junit.Test

class HomeChatFlowUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sendMessage_showsMergedToolCallAndReply() {
        AgentServerConfigStore.setForTesting(baseUrl = "http://localhost:2024", apiKey = "test-key")
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
                    onEvent(ChatStreamEvent.TaskProgress(label = "获取当前位置", status = "running", phase = "phone_tool"))
                    onEvent(ChatStreamEvent.TaskProgress(label = "获取当前位置", status = "completed", phase = "phone_tool"))
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
        composeRule.onNodeWithContentDescription("发送")
            .performClick()

        composeRule.onNodeWithText("获取当前位置")
            .assertIsDisplayed()
        composeRule.onNodeWithText("这是回复正文")
            .assertIsDisplayed()
    }

    @Test
    fun imeSend_sendsMessageAndShowsReply() {
        AgentServerConfigStore.setForTesting(baseUrl = "http://localhost:2024", apiKey = "test-key")
        val viewModel = ChatViewModel(
            chatProvider = object : EmptyProvider() {
                override suspend fun streamReply(
                    threadId: String,
                    prompt: ChatPrompt,
                    onEvent: (ChatStreamEvent) -> Unit,
                ) {
                    onEvent(ChatStreamEvent.TextDelta("收到:${prompt.text}"))
                    onEvent(ChatStreamEvent.Completed)
                }
            },
            repo = ChatSessionRepository(idProvider = { "ime-${System.nanoTime()}" }),
        )

        composeRule.setContent {
            HomeScreen(viewModel = viewModel)
        }

        composeRule.onNode(hasSetTextAction())
            .performTextInput("键盘发送")
        composeRule.onNode(hasSetTextAction())
            .performImeAction()

        composeRule.onNodeWithText("收到:键盘发送")
            .assertIsDisplayed()
    }

    @Test
    fun longConversation_showsScrollToBottomFabAndReturnsToLatestMessage() {
        AgentServerConfigStore.setForTesting(baseUrl = "http://localhost:2024", apiKey = "test-key")
        val messages = (0 until 7).flatMap { index ->
            listOf(
                Message("u-$index", "用户消息 $index", isUser = true),
                Message("a-$index", if (index == 6) "最新回答" else "助手回答 $index", isUser = false),
            )
        }
        val viewModel = ChatViewModel(
            chatProvider = object : EmptyProvider() {
                override suspend fun loadThreads(limit: Int): List<RemoteChatThread> {
                    return listOf(RemoteChatThread("thread-long", "长对话", 2_000L, messages))
                }
            },
            repo = ChatSessionRepository(idProvider = { "long-${System.nanoTime()}" }),
        )

        composeRule.setContent {
            HomeScreen(viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("最新回答").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chat_list").performTouchInput { swipeDown() }
        composeRule.onNodeWithContentDescription("回到底部").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("回到底部").performClick()

        composeRule.onNodeWithText("最新回答").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsSectionHeaders() {
        composeRule.setContent {
            SettingsScreen(onBack = {})
        }

        composeRule.onNodeWithText("账号与偏好").assertIsDisplayed()
        composeRule.onNodeWithText("服务与数据").assertIsDisplayed()
        composeRule.onNodeWithText("手机控制").assertIsDisplayed()
        composeRule.onNodeWithText("支持与关于").assertIsDisplayed()
    }

    private open class EmptyProvider : ChatProvider {
        override suspend fun createThread(titleHint: String?): RemoteChatThread {
            return RemoteChatThread("thread-${System.nanoTime()}", titleHint ?: "当前对话", 0L, emptyList())
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
            onEvent(ChatStreamEvent.Completed)
        }
    }
}
