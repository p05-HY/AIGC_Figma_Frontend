package com.example.blueheartv.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.components.*
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.viewmodel.ChatSessionState
import com.example.blueheartv.viewmodel.ChatState
import com.example.blueheartv.viewmodel.ChatViewModel
import com.example.blueheartv.viewmodel.HomeUiState

@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val shouldAutoScroll by remember(uiState.messages.size, uiState.sessionState) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = uiState.messages.size
            total == 0 || lastVisible >= total - 3
        }
    }

    LaunchedEffect(
        uiState.messages.size,
        uiState.messages.lastOrNull()?.content?.length,
        uiState.sessionState,
    ) {
        if (uiState.messages.isNotEmpty() && shouldAutoScroll) {
            listState.scrollToItem(uiState.messages.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Decorative background
        DecorativeBackground()

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Top bar
            AppTopBar(
                onMenuClick = { viewModel.toggleDrawer() },
                onAvatarClick = onNavigateToSettings,
            )

            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (uiState.chatState) {
                    ChatState.DEFAULT -> DefaultContent(uiState, viewModel)
                    ChatState.CHAT_SIMPLE,
                    ChatState.CHAT_TOOL_CALLING,
                    -> ChatContent(
                        uiState = uiState,
                        listState = listState,
                    )
                }
            }

            val lastError = uiState.lastError
            if (uiState.sessionState == ChatSessionState.ERROR && lastError != null) {
                ErrorRetryBar(
                    message = lastError,
                    canRetry = uiState.canRetry,
                    onRetry = { viewModel.retryLastMessage() },
                )
            }

            // Quick action buttons
            GlassButtonRow(
                buttons = listOf("读取屏幕", "今日行程", "今日快递", "今日天气"),
                onButtonClick = { index ->
                    when (index) {
                        0 -> viewModel.switchToSimpleChat()
                        1 -> viewModel.switchToToolCallingChat()
                        else -> viewModel.switchToSimpleChat()
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom input bar
            BottomInputBar(
                value = uiState.inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                onSend = { viewModel.sendMessage() },
                sendEnabled = uiState.sessionState != ChatSessionState.RESPONDING,
            )
        }

        // History drawer overlay
        HistoryDrawer(
            isOpen = uiState.isDrawerOpen,
            histories = uiState.histories,
            onClose = { viewModel.closeDrawer() },
            onNewChat = { viewModel.startNewConversation() },
            onHistoryClick = { history -> viewModel.selectHistory(history.id) },
        )
    }
}

@Composable
private fun ErrorRetryBar(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = BlueAccentLight,
            modifier = Modifier.weight(1f),
        )
        if (canRetry) {
            Text(
                text = "重试",
                fontSize = 13.sp,
                color = BlueAccent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onRetry() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DefaultContent(
    uiState: HomeUiState,
    viewModel: ChatViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // "智能推荐" label
        Text(
            text = "智能推荐",
            fontSize = 18.sp,
            color = TextBlack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Smart recommendation cards
        SmartCardRow(recommendations = uiState.recommendations)

        Spacer(modifier = Modifier.weight(1f))

        // Welcome text
        Text(
            text = "您好！我是超级蓝心小V~",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DarkPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "有什么我可以帮助您的吗？",
            fontSize = 14.sp,
            color = MutedText,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun ChatContent(
    uiState: HomeUiState,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.messages, key = { it.id }) { message ->
            Column {
                if (message.isUser) {
                    UserBubble(message = message)
                } else {
                    AiBubble(message = message)
                    if (!message.isLoading) {
                        ActionToolbar()
                    }
                }
            }
        }
    }
}
