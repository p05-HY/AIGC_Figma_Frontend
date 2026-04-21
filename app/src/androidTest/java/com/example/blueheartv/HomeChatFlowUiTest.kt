package com.example.blueheartv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatSessionsSnapshot
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.ui.screens.HomeScreen
import com.example.blueheartv.viewmodel.ChatViewModel
import org.junit.Rule
import org.junit.Test

class HomeChatFlowUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sendMessage_showsMergedToolCallAndReply() {
        val viewModel = ChatViewModel(
            chatProvider = object : ChatProvider {
                override suspend fun streamReply(
                    prompt: String,
                    onEvent: (ChatStreamEvent) -> Unit,
                ) {
                    onEvent(ChatStreamEvent.ToolCallStarted("获取当前位置"))
                    onEvent(ChatStreamEvent.ToolCallCompleted("获取当前位置"))
                    onEvent(ChatStreamEvent.TextDelta("这是回复正文"))
                    onEvent(ChatStreamEvent.Completed)
                }
            },
            snapshotLoader = { ChatSessionsSnapshot(null, emptyList()) },
            snapshotSaver = {},
            idProvider = { "test-${System.nanoTime()}" },
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
