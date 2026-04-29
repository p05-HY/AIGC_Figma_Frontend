package com.example.blueheartv.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.ui.components.*
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.util.AppGlobalUiHost
import com.example.blueheartv.util.ToastType
import com.example.blueheartv.util.ToastUtil
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
    var showFloatingMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val actions = rememberHomeScreenActions(viewModel, snackbarHostState)

    val shouldAutoScroll by remember(uiState.messages.size, uiState.sessionState) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = uiState.messages.size
            total == 0 || lastVisible >= total - 3
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content?.length, uiState.sessionState) {
        if (uiState.messages.isNotEmpty() && shouldAutoScroll) {
            listState.scrollToItem(uiState.messages.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DecorativeBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            AppTopBar(
                onMenuClick = { viewModel.toggleDrawer() },
                onAddClick = { viewModel.startNewConversation() },
            )

            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                when (uiState.chatState) {
                    ChatState.DEFAULT -> DefaultContent(uiState, viewModel)
                    ChatState.CHAT_SIMPLE,
                    ChatState.CHAT_TOOL_CALLING,
                        -> ChatContent(
                        uiState = uiState,
                        listState = listState,
                        onRefresh = { viewModel.retryLastMessage() },
                        onCopy = { actions.copyToClipboard(it) },
                        onDelete = { viewModel.deleteMessage(it) },
                        onSpeak = { actions.speak(it) },
                        onEditUserMessage = { messageId, newContent -> viewModel.editAndResend(messageId, newContent) },
                        onDeleteAiMessage = { viewModel.deleteAiMessage(it) },
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

            GlassButtonRow(
                buttons = listOf(
                    stringResource(R.string.btn_read_screen),
                    stringResource(R.string.btn_today_schedule),
                    stringResource(R.string.btn_today_delivery),
                    stringResource(R.string.btn_today_weather),
                ),
                onButtonClick = { index -> actions.requestQuickAction(index) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            BottomInputBar(
                value = uiState.inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                onSend = { viewModel.sendMessage() },
                sendEnabled = uiState.sessionState != ChatSessionState.RESPONDING,
                onAttachClick = { actions.requestAttach() },
                onMicClick = { actions.requestMic() },
            )
        }

        HistoryDrawer(
            isOpen = uiState.isDrawerOpen,
            histories = uiState.histories,
            onClose = { viewModel.closeDrawer() },
            onNewChat = { viewModel.startNewConversation() },
            onHistoryClick = { history -> viewModel.selectHistory(history.id) },
            onRename = { history, newTitle -> viewModel.renameSession(history.id, newTitle) },
            onTogglePin = { history -> viewModel.togglePin(history.id) },
            onShare = { history ->
                val text = viewModel.getShareText(history.id)
                if (text.isNotBlank()) {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "分享对话"))
                }
            },
            onDelete = { history -> viewModel.deleteSession(history.id) },
            onSettings = {
                viewModel.closeDrawer()
                onNavigateToSettings()
            },
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            FloatingMenu(
                visible = showFloatingMenu,
                onDismiss = { showFloatingMenu = false },
                items = listOf(
                    FloatingMenuItem(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.menu_new_chat)) {
                        viewModel.startNewConversation()
                    },
                    FloatingMenuItem(Icons.Outlined.Screenshot, stringResource(R.string.menu_screenshot)) {
                        showFloatingMenu = false
                        ToastUtil.show(context.getString(R.string.feature_in_dev_screenshot), ToastType.INFO)
                    },
                    FloatingMenuItem(Icons.Outlined.Translate, stringResource(R.string.menu_translate)) {
                        showFloatingMenu = false
                        ToastUtil.show(context.getString(R.string.feature_in_dev_translate), ToastType.INFO)
                    },
                    FloatingMenuItem(Icons.Outlined.Summarize, stringResource(R.string.menu_summarize)) {
                        showFloatingMenu = false
                        ToastUtil.show(context.getString(R.string.feature_in_dev_summarize), ToastType.INFO)
                    },
                ),
                modifier = Modifier.padding(end = 72.dp, bottom = 120.dp),
            )

            FloatingWidget { showFloatingMenu = !showFloatingMenu }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )

        AppGlobalUiHost()
    }
}

@Composable
private fun ErrorRetryBar(message: String, canRetry: Boolean, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = message, fontSize = 12.sp, color = BlueAccentLight, modifier = Modifier.weight(1f))
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
private fun DefaultContent(uiState: HomeUiState, viewModel: ChatViewModel) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.smart_recommendations),
            fontSize = 18.sp, color = TextBlack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        SmartCardRow(
            recommendations = uiState.recommendations,
            onCardClick = { rec -> viewModel.sendRecommendation(rec) })
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.welcome_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DarkPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            fontSize = 14.sp,
            color = MutedText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun ChatContent(
    uiState: HomeUiState,
    listState: LazyListState,
    onRefresh: () -> Unit = {},
    onCopy: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onSpeak: (String) -> Unit = {},
    onEditUserMessage: (String, String) -> Unit = { _, _ -> },
    onDeleteAiMessage: (String) -> Unit = {},
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
                    UserBubble(
                        message = message,
                        onCopy = onCopy,
                        onDelete = onDelete,
                        onEditConfirm = { newContent -> onEditUserMessage(message.id, newContent) },
                    )
                } else {
                    AiBubble(message = message, onCopy = onCopy, onSpeak = onSpeak, onDelete = onDeleteAiMessage)
                    if (!message.isLoading) {
                        ActionToolbar(messageContent = message.content, onSpeak = onSpeak, onRefresh = onRefresh)
                    }
                }
            }
        }
    }
}
