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
import androidx.room.Room
import com.example.blueheartv.chat.ChatProvider
import com.example.blueheartv.chat.ChatStreamEvent
import com.example.blueheartv.db.AppDatabase
import com.example.blueheartv.model.Message
import com.example.blueheartv.ui.screens.HomeScreen
import com.example.blueheartv.viewmodel.ChatSessionRepository
import com.example.blueheartv.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test

class HomeChatFlowUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sendMessage_showsMergedToolCallAndReply() {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val db = Room.inMemoryDatabaseBuilder(
            composeRule.activity, AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repo = ChatSessionRepository(
            dao = db.chatDao(),
            idProvider = { "test-${System.nanoTime()}" },
            persistDebounceMs = 0L,
            scope = testScope,
        )
        val viewModel = ChatViewModel(
            chatProvider = object : ChatProvider {
                override suspend fun streamReply(
                    messages: List<Message>,
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
