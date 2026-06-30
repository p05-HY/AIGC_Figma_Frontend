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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.model.ChatAttachment
import com.example.blueheartv.ui.components.*
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.util.AppGlobalUiHost
import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.viewmodel.ChatSessionState
import com.example.blueheartv.viewmodel.ChatState
import com.example.blueheartv.viewmodel.ChatViewModel
import com.example.blueheartv.viewmodel.HomeUiState
import com.example.blueheartv.voice.VoiceRecordingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val actions = rememberHomeScreenActions(viewModel, snackbarHostState)
    val undoActionLabel = stringResource(R.string.action_undo)
    val deletedMessageText = stringResource(R.string.snackbar_message_deleted)
    val editedMessageText = stringResource(R.string.snackbar_message_edited)
    val scrollToBottomDescription = stringResource(R.string.action_scroll_to_bottom)
    val goSettingsText = stringResource(R.string.go_settings)
    val retryText = stringResource(R.string.retry)

    fun showUndoSnackbar(message: String, undoToken: Long) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastMessageMutation(undoToken)
            }
        }
    }

    fun sendCurrentMessage() {
        val canSend = uiState.inputText.isNotBlank() || uiState.imageAttachments.isNotEmpty()
        if (!canSend) return
        viewModel.sendMessage()
        focusManager.clearFocus()
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToSettings.collect { onNavigateToSettings() }
    }

    val chatEntries = remember(uiState.messages) {
        chatListEntries(groupConversationMessages(uiState.messages))
    }
    val chatEntryCount = chatEntries.size
    val lastMessage = uiState.messages.lastOrNull()
    val streamingScrollTick = remember(lastMessage?.id, lastMessage?.content?.length, uiState.sessionState) {
        if (uiState.sessionState == ChatSessionState.RESPONDING) {
            streamingScrollBucket(lastMessage?.content?.length ?: 0)
        } else {
            0
        }
    }

    val shouldAutoScroll by remember(chatEntryCount, uiState.sessionState) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            shouldAutoFollowChatScroll(lastVisibleIndex = lastVisible, totalItems = chatEntryCount)
        }
    }

    LaunchedEffect(chatEntryCount, lastMessage?.id, uiState.sessionState, streamingScrollTick) {
        if (chatEntryCount > 0 && shouldAutoScroll) {
            delay(80)
            listState.scrollToItem(chatEntryCount - 1)
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
                        chatEntries = chatEntries,
                        listState = listState,
                        onRefresh = { viewModel.retryLastMessage() },
                        onCopy = { actions.copyToClipboard(it) },
                        onDelete = {
                            viewModel.deleteMessage(it)?.let { result ->
                                showUndoSnackbar(deletedMessageText, result.token)
                            }
                        },
                        onSpeak = { actions.speak(it) },
                        onEditUserMessage = { messageId, newContent ->
                            viewModel.editAndResend(messageId, newContent)?.let { result ->
                                showUndoSnackbar(editedMessageText, result.token)
                            }
                        },
                        onDeleteAiMessage = {
                            viewModel.deleteAiMessage(it)?.let { result ->
                                showUndoSnackbar(deletedMessageText, result.token)
                            }
                        },
                    )
                }

                if (!shouldAutoScroll && uiState.messages.size > 5) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (chatEntryCount > 0) {
                                    listState.animateScrollToItem(chatEntryCount - 1)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 20.dp),
                        containerColor = SurfaceWhite,
                        contentColor = BlueAccent,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = scrollToBottomDescription,
                        )
                    }
                }
            }

            val lastError = uiState.lastError
            if (uiState.sessionState == ChatSessionState.ERROR && lastError != null) {
                val isConfigError = !AgentServerConfigStore.snapshot().isConfigured
                val retryAction: () -> Unit = { viewModel.retryLastMessage() }
                ErrorRetryBar(
                    message = lastError,
                    actionText = if (isConfigError) goSettingsText else retryText,
                    canAction = if (isConfigError) true else uiState.canRetry,
                    onAction = if (isConfigError) onNavigateToSettings else retryAction,
                )
            }
            if (uiState.sessionState in setOf(
                    ChatSessionState.CANCELLING,
                    ChatSessionState.BACKEND_STILL_RUNNING,
                )
            ) {
                ErrorRetryBar(
                    message = lastError ?: uiState.streamingStep ?: "正在确认任务状态。",
                    actionText = stringResource(R.string.action_stop),
                    canAction = uiState.canCancel,
                    onAction = { viewModel.cancelActiveRun() },
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
                onSend = { sendCurrentMessage() },
                sendEnabled = uiState.sessionState in setOf(
                    ChatSessionState.IDLE,
                    ChatSessionState.CANCELLED,
                    ChatSessionState.ERROR,
                ) &&
                    (uiState.inputText.isNotBlank() || uiState.imageAttachments.isNotEmpty()),
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
                canCancel = uiState.canCancel,
                onCancel = { viewModel.cancelActiveRun() },
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

        VoiceRecordingOverlay(
            visible = actions.voiceRecordingState.value != VoiceRecordingState.IDLE,
            recordingState = actions.voiceRecordingState.value,
            partialText = actions.partialText.value,
            amplitudeDb = actions.amplitudeDb.value,
            resultText = actions.resultText.value,
            onCancel = { actions.cancelVoiceRecording() },
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
    val removeAttachmentDescription = stringResource(R.string.action_remove_attachment)
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
                        contentDescription = removeAttachmentDescription,
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
private fun ErrorRetryBar(
    message: String,
    actionText: String = "重试",
    canAction: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = message, fontSize = 12.sp, color = BlueAccentLight, modifier = Modifier.weight(1f))
        if (canAction) {
            Text(
                text = actionText,
                fontSize = 13.sp,
                color = BlueAccent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onAction() }
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
    chatEntries: List<ChatListEntry>,
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
        modifier = Modifier
            .fillMaxSize()
            .testTag("chat_list"),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chatEntries, key = { it.key }) { entry ->
            when (entry) {
                is ChatListEntry.Header -> ConversationTimestampHeader(timestamp = entry.timestamp)
                is ChatListEntry.MessageItem -> {
                    val message = entry.message
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
}

