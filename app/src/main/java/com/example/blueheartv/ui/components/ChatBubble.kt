package com.example.blueheartv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.util.ToastType
import com.example.blueheartv.util.ToastUtil
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val TYPEWRITER_DELAY_MS = 35L

@Composable
fun rememberTypewriterText(fullText: String, isStreaming: Boolean): String {
    var displayedLength by remember { mutableIntStateOf(0) }
    val currentFullText by rememberUpdatedState(fullText)

    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            displayedLength = currentFullText.length
            return@LaunchedEffect
        }
        while (displayedLength < currentFullText.length) {
            delay(TYPEWRITER_DELAY_MS.milliseconds)
            val target = currentFullText.length
            if (displayedLength < target) {
                displayedLength++
            }
        }
    }

    LaunchedEffect(fullText) {
        if (isStreaming && displayedLength < fullText.length) {
            while (displayedLength < fullText.length) {
                delay(TYPEWRITER_DELAY_MS.milliseconds)
                displayedLength++
            }
        } else if (!isStreaming) {
            displayedLength = fullText.length
        }
    }

    return if (displayedLength >= fullText.length) fullText else fullText.substring(0, displayedLength)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onEditConfirm: (String) -> Unit = {},
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 24.dp,
        bottomEnd = 10.dp,
    )

    var showMenu by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(message.content) }
    var enableTextSelection by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除消息") },
            text = { Text("确定要删除这条消息吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(message.id)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .border(1.dp, BorderGray, bubbleShape)
                    .background(SurfaceWhite, bubbleShape)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { if (!isEditing) showMenu = true },
                    )
                    .padding(16.dp),
            ) {
                if (isEditing) {
                    Column {
                        BasicTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = TextDark,
                                lineHeight = 24.sp,
                            ),
                            cursorBrush = SolidColor(BlueAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    isEditing = false
                                    editText = message.content
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Outlined.Close, "取消", tint = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    isEditing = false
                                    val newContent = editText.trim()
                                    if (newContent.isNotBlank() && newContent != message.content) {
                                        onEditConfirm(newContent)
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Outlined.Check, "确认", tint = BlueAccent)
                            }
                        }
                    }
                    LaunchedEffect(Unit) {
                        delay(100.milliseconds)
                        focusRequester.requestFocus()
                    }
                } else if (enableTextSelection) {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            fontSize = 16.sp,
                            color = TextDark,
                            lineHeight = 24.sp,
                        )
                    }
                } else {
                    Text(
                        text = message.content,
                        fontSize = 16.sp,
                        color = TextDark,
                        lineHeight = 24.sp,
                    )
                }
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset(0.dp, 0.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = {
                        showMenu = false
                        onCopy(message.content)
                    },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)) },
                )
                DropdownMenuItem(
                    text = { Text("修改") },
                    onClick = {
                        showMenu = false
                        editText = message.content
                        isEditing = true
                    },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)) },
                )
                DropdownMenuItem(
                    text = { Text("选取文字") },
                    onClick = {
                        showMenu = false
                        enableTextSelection = true
                    },
                    leadingIcon = { Icon(Icons.Outlined.SelectAll, null, Modifier.size(18.dp)) },
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        showDeleteConfirm = true
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Delete, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit = {},
    onSpeak: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 10.dp,
        bottomEnd = 24.dp,
    )

    var showMenu by remember { mutableStateOf(false) }
    var enableTextSelection by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除消息") },
            text = { Text("确定要删除这条回答吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(message.id)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(4.dp, CircleShape)
                .background(SurfaceWhite, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_echo_face),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Box {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .shadow(2.dp, bubbleShape)
                        .background(LightGray, bubbleShape)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (!message.isLoading) showMenu = true
                            },
                        )
                        .padding(16.dp),
                ) {
                    if (message.isLoading && message.content.isBlank()) {
                        LoadingDots()
                    } else {
                        val isStreaming = message.deliveryState == com.example.blueheartv.model.MessageDeliveryState.STREAMING
                        val displayText = rememberTypewriterText(message.content, isStreaming)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            message.toolCalls?.takeIf { it.isNotEmpty() }?.let { toolCalls ->
                                ToolCallCard(toolCalls = toolCalls)
                            }
                            if (enableTextSelection) {
                                SelectionContainer {
                                    MarkdownMessageContent(content = displayText)
                                }
                            } else {
                                MarkdownMessageContent(content = displayText)
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    offset = DpOffset(0.dp, 0.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("创建文档") },
                        onClick = {
                            showMenu = false
                            ToastUtil.show("创建文档功能开发中", ToastType.INFO)
                        },
                        leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text("选取文字") },
                        onClick = {
                            showMenu = false
                            enableTextSelection = true
                        },
                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text("收藏") },
                        onClick = {
                            showMenu = false
                            ToastUtil.show("收藏功能开发中", ToastType.INFO)
                        },
                        leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text("导出文件") },
                        onClick = {
                            showMenu = false
                            ToastUtil.show("导出文件功能开发中", ToastType.INFO)
                        },
                        leadingIcon = { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCallCard(toolCalls: List<ToolCall>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(0.5.dp, DividerColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        toolCalls.forEach { toolCall ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (toolCall.isComplete) Color(0xFF22C55E) else BlueAccent,
                            CircleShape,
                        ),
                )
                Text(
                    text = toolCall.label,
                    fontSize = 13.sp,
                    color = TextDarkAlt,
                )
            }
        }
    }
}

@Composable
fun MarkdownMessageContent(content: String) {
    val clipboardManager = LocalClipboardManager.current
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.TextBlock -> {
                    if (block.text.isNotBlank()) {
                        Text(
                            text = block.text,
                            fontSize = 16.sp,
                            color = TextBlack,
                            lineHeight = 24.sp,
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = block.language.ifBlank { "code" },
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(block.code))
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "复制代码",
                                    tint = Color.White,
                                )
                            }
                        }

                        SelectionContainer {
                            Text(
                                text = block.code,
                                fontSize = 13.sp,
                                color = Color(0xFFF9FAFB),
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock
    data class CodeBlock(
        val language: String,
        val code: String,
    ) : MarkdownBlock
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    if (!content.contains("```")) {
        return listOf(MarkdownBlock.TextBlock(content))
    }

    val blocks = mutableListOf<MarkdownBlock>()
    var cursor = 0

    while (cursor < content.length) {
        val codeStart = content.indexOf("```", cursor)
        if (codeStart < 0) {
            blocks.add(MarkdownBlock.TextBlock(content.substring(cursor)))
            break
        }

        if (codeStart > cursor) {
            blocks.add(MarkdownBlock.TextBlock(content.substring(cursor, codeStart)))
        }

        val headerEnd = content.indexOf('\n', codeStart + 3)
        if (headerEnd < 0) {
            blocks.add(MarkdownBlock.TextBlock(content.substring(codeStart)))
            break
        }

        val language = content.substring(codeStart + 3, headerEnd).trim()
        val codeEnd = content.indexOf("```", headerEnd + 1)
        if (codeEnd < 0) {
            blocks.add(MarkdownBlock.TextBlock(content.substring(codeStart)))
            break
        }

        val code = content.substring(headerEnd + 1, codeEnd).trimEnd()
        blocks.add(MarkdownBlock.CodeBlock(language = language, code = code))
        cursor = codeEnd + 3
    }

    return blocks.ifEmpty { listOf(MarkdownBlock.TextBlock(content)) }
}

@Composable
fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        Color(0xFFBCBCBC).copy(alpha = alpha),
                        CircleShape,
                    ),
            )
        }
    }
}
