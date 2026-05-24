package com.example.blueheartv.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.blueheartv.model.ChatAttachment
import com.example.blueheartv.ui.components.*
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.util.AppGlobalUiHost
import com.example.blueheartv.util.ToastType
import com.example.blueheartv.util.ToastUtil
import com.example.blueheartv.viewmodel.ChatSessionState
import com.example.blueheartv.viewmodel.ChatState
import com.example.blueheartv.viewmodel.ChatViewModel
import com.example.blueheartv.viewmodel.HomeUiState
import com.example.blueheartv.voice.VoiceRecordingState

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
    val screenshotInDevText = stringResource(R.string.feature_in_dev_screenshot)
    val translateInDevText = stringResource(R.string.feature_in_dev_translate)
    val summarizeInDevText = stringResource(R.string.feature_in_dev_summarize)

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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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
                icons = listOf(
                    Icons.Outlined.Visibility,
                    Icons.Outlined.EventNote,
                    Icons.Outlined.LocalShipping,
                    Icons.Outlined.WbSunny,
                ),
                onButtonClick = { index -> actions.requestQuickAction(index) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            AttachmentPreviewRow(
                attachments = uiState.imageAttachments,
                onRemove = { viewModel.removeImageAttachment(it) },
            )

            BottomInputBar(
                value = uiState.inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                onSend = { viewModel.sendMessage() },
                sendEnabled = uiState.sessionState != ChatSessionState.RESPONDING,
                onAttachClick = { actions.requestAttach() },
                onMicClick = { actions.requestMic() },
                inputMode = uiState.inputMode,
                voiceRecordingState = actions.voiceRecordingState.value,
                onVoiceStart = { actions.startVoiceRecording() },
                onVoiceEnd = { actions.stopVoiceRecording() },
                onVoiceCancel = { actions.cancelVoiceRecording() },
                onVoiceModeTap = { actions.requestMic() },
                onSwipeToCancelling = { actions.setVoiceCancelling() },
                onSwipeBackToRecording = { actions.setVoiceRecording() },
            )

            Spacer(modifier = Modifier.height(8.dp))
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

        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            val menuEndPadding = maxWidth * 0.08f
            val menuBottomPadding = maxHeight * 0.12f
            FloatingMenu(
                visible = showFloatingMenu,
                onDismiss = { showFloatingMenu = false },
                items = listOf(
                    FloatingMenuItem(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.menu_new_chat)) {
                        viewModel.startNewConversation()
                    },
                    FloatingMenuItem(Icons.Outlined.Screenshot, stringResource(R.string.menu_screenshot)) {
                        showFloatingMenu = false
                        ToastUtil.show(screenshotInDevText, ToastType.INFO)
                    },
                    FloatingMenuItem(Icons.Outlined.Translate, stringResource(R.string.menu_translate)) {
                        showFloatingMenu = false
                        ToastUtil.show(translateInDevText, ToastType.INFO)
                    },
                    FloatingMenuItem(Icons.Outlined.Summarize, stringResource(R.string.menu_summarize)) {
                        showFloatingMenu = false
                        ToastUtil.show(summarizeInDevText, ToastType.INFO)
                    },
                ),
                modifier = Modifier.padding(end = menuEndPadding, bottom = menuBottomPadding),
            )

            FloatingWidget { showFloatingMenu = !showFloatingMenu }
        }

        VoiceRecordingOverlay(
            visible = actions.voiceRecordingState.value != VoiceRecordingState.IDLE,
            recordingState = actions.voiceRecordingState.value,
            partialText = actions.partialText.value,
            amplitudeDb = actions.amplitudeDb.value,
        )

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
private fun AttachmentPreviewRow(
    attachments: List<ChatAttachment>,
    onRemove: (String) -> Unit,
) {
    if (attachments.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            Surface(
                color = SurfaceWhite,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = BlueAccent,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = attachment.displayName,
                        fontSize = 12.sp,
                        color = TextBlack,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove attachment",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onRemove(attachment.id) },
                        tint = MutedText,
                    )
                }
            }
        }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.smart_recommendations),
            fontSize = 18.sp,
            color = TextBlack,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        )

        SmartCardCarousel(
            recommendations = uiState.recommendations,
            onCardClick = { rec -> viewModel.sendRecommendation(rec) },
            modifier = Modifier.offset(y = (-40).dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-40).dp)
                .padding(horizontal = 40.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.welcome_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DarkPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.welcome_subtitle),
                fontSize = 14.sp,
                color = MutedText,
                textAlign = TextAlign.Center,
            )
        }
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
