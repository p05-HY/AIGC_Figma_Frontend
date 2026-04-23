package com.example.blueheartv.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.model.ChatHistory
import com.example.blueheartv.ui.theme.*

@Composable
fun HistoryDrawer(
    isOpen: Boolean,
    histories: List<ChatHistory>,
    onClose: () -> Unit,
    onNewChat: () -> Unit = {},
    onHistoryClick: (ChatHistory) -> Unit = {},
    onRename: (ChatHistory, String) -> Unit = { _, _ -> },
    onTogglePin: (ChatHistory) -> Unit = {},
    onShare: (ChatHistory) -> Unit = {},
    onDelete: (ChatHistory) -> Unit = {},
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                initialOffsetX = { -it },
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                targetOffsetX = { -it },
            ),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OverlayBlack)
                    .clickable { onClose() },
            )

            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(319.dp),
                shape = RoundedCornerShape(topEnd = 40.dp, bottomEnd = 0.dp),
                color = SurfaceWhite,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 24.dp),
                ) {
                    // Header: history icon + title on left, + on right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 23.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = TextBlack,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "历史对话",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextBlack,
                            letterSpacing = (-0.5).sp,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { onNewChat() },
                            tint = TextBlack,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(thickness = 0.5.dp, color = DividerColor)
                    Spacer(modifier = Modifier.height(8.dp))

                    // History list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 23.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(histories, key = { it.id }) { history ->
                            HistoryItem(
                                history = history,
                                onClick = { onHistoryClick(history) },
                                onRename = { newTitle -> onRename(history, newTitle) },
                                onTogglePin = { onTogglePin(history) },
                                onShare = { onShare(history) },
                                onDelete = { onDelete(history) },
                            )
                        }
                    }

                    // Bottom settings entry
                    Divider(thickness = 0.5.dp, color = DividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSettings() }
                            .padding(horizontal = 23.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(22.dp),
                            tint = MutedText,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "设置",
                            fontSize = 15.sp,
                            color = MutedText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    history: ChatHistory,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = CardBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (history.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = BlueAccent,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = history.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = history.timestamp,
                    fontSize = 12.sp,
                    color = MutedText,
                )
            }

            Box {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { showMenu = true },
                    tint = MutedText,
                )
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    offset = DpOffset(0.dp, 0.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (history.isPinned) "取消置顶" else "置顶") },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("分享") },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentTitle = history.title,
            onConfirm = { newTitle ->
                onRename(newTitle)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除对话") },
            text = { Text("确定要删除「${history.title}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun RenameDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
