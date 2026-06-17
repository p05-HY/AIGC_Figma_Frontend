package com.example.blueheartv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.blueheartv.model.ToolCallStatus
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.util.ToastType
import com.example.blueheartv.util.ToastUtil
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.*
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
        verticalAlignment = Alignment.Bottom,
    ) {
        BoxWithConstraints {
            val bubbleMaxWidth = (maxWidth * 0.78f).coerceIn(200.dp, 480.dp)
            Column(horizontalAlignment = Alignment.End) {
                Box {
                    Box(
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
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
                                    Icon(Icons.Outlined.Close, "取消", tint = ErrorRed)
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
                        text = { Text("删除", color = ErrorRed) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete, null, Modifier.size(18.dp),
                                tint = ErrorRed,
                            )
                        },
                    )
                }
                }
                MessageTimestamp(timestamp = message.timestamp)
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
                .size(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_echo_face),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        BoxWithConstraints {
            val bubbleMaxWidth = (maxWidth * 0.85f).coerceIn(220.dp, 560.dp)
            Column {
                Box {
                    Box(
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
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
                            message.thinking?.takeIf { it.isNotBlank() }?.let { thinking ->
                                ThinkingCard(thinking = thinking, isStreaming = isStreaming)
                            }
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
                        text = { Text("选取文字") },
                        onClick = {
                            showMenu = false
                            enableTextSelection = true
                        },
                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = ErrorRed) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete, null, Modifier.size(18.dp),
                                tint = ErrorRed,
                            )
                        },
                    )
                }
                MessageTimestamp(timestamp = message.timestamp)
            }
            }
        }
    }
}

@Composable
private fun MessageTimestamp(timestamp: Long) {
    val formatter = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    val timeText = remember(timestamp) {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000}分钟前"
            diff < 86400_000 -> "${diff / 3600_000}小时前"
            else -> formatter.format(Date(timestamp))
        }
    }
    Text(
        text = timeText,
        fontSize = 11.sp,
        color = MutedText,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun ThinkingCard(thinking: String, isStreaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MutedText,
            )
            Text(
                text = if (isStreaming) "思考中…" else "思考过程",
                fontSize = 13.sp,
                color = MutedText,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(18.dp),
                tint = MutedText,
            )
        }
        if (expanded) {
            Text(
                text = thinking,
                fontSize = 12.sp,
                color = TextDarkAlt,
                lineHeight = 18.sp,
            )
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
            ToolCallRow(toolCall = toolCall)
        }
    }
}

@Composable
private fun ToolCallRow(toolCall: ToolCall) {
    val hasDetail = !toolCall.args.isNullOrBlank() ||
        !toolCall.result.isNullOrBlank() ||
        !toolCall.error.isNullOrBlank() ||
        !toolCall.message.isNullOrBlank() ||
        !toolCall.phase.isNullOrBlank() ||
        toolCall.currentStep != null ||
        toolCall.totalSteps != null ||
        toolCall.completedSteps.isNotEmpty()
    var expanded by remember { mutableStateOf(false) }

    val (dotColor, statusText) = when (toolCall.status) {
        ToolCallStatus.RUNNING -> BlueAccent to "执行中"
        ToolCallStatus.COMPLETED -> SuccessGreen to "成功"
        ToolCallStatus.FAILED -> ErrorRed to "失败"
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasDetail) Modifier.clickable { expanded = !expanded } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Text(
                text = buildString {
                    append(toolCall.label)
                    val step = formatStep(toolCall)
                    if (step != null) append("  ").append(step)
                },
                fontSize = 13.sp,
                color = TextDarkAlt,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusText,
                fontSize = 11.sp,
                color = dotColor,
            )
            if (hasDetail) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(16.dp),
                    tint = MutedText,
                )
            }
        }
        if (expanded && hasDetail) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                toolCall.phase?.takeIf { it.isNotBlank() }?.let {
                    ToolDetailField(label = "阶段", value = it, valueColor = TextDarkAlt)
                }
                toolCall.message?.takeIf { it.isNotBlank() }?.let {
                    ToolDetailField(label = "说明", value = it, valueColor = TextDarkAlt)
                }
                toolCall.args?.takeIf { it.isNotBlank() }?.let {
                    ToolDetailField(label = "入参", value = it, valueColor = TextDarkAlt)
                }
                toolCall.result?.takeIf { it.isNotBlank() }?.let {
                    ToolDetailField(label = "出参", value = it, valueColor = TextDarkAlt)
                }
                toolCall.error?.takeIf { it.isNotBlank() }?.let {
                    ToolDetailField(label = "错误", value = it, valueColor = ErrorRed)
                }
                toolCall.completedSteps.takeIf { it.isNotEmpty() }?.let { steps ->
                    ToolDetailField(
                        label = "已完成步骤",
                        value = steps.joinToString("\n") { step ->
                            buildString {
                                step.index?.let { append(it).append(". ") }
                                append(step.name)
                                if (step.status.isNotBlank()) append(" - ").append(step.status)
                            }
                        },
                        valueColor = TextDarkAlt,
                    )
                }
            }
        }
    }
}

private fun formatStep(toolCall: ToolCall): String? {
    val current = toolCall.currentStep
    val total = toolCall.totalSteps
    return when {
        current != null && total != null -> "($current/$total)"
        current != null -> "(step $current)"
        total != null -> "(共 $total 步)"
        else -> null
    }
}

@Composable
private fun ToolDetailField(label: String, value: String, valueColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, fontSize = 11.sp, color = MutedText)
        Text(
            text = value,
            fontSize = 12.sp,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(ToolDetailBackground, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
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
                            .background(CodeBlockBackground, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = block.language.ifBlank { "code" },
                                fontSize = 12.sp,
                                color = CodeBlockLabel,
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
                                color = CodeBlockText,
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Skeleton bars for visual weight during loading
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.55f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "skeleton_$index",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == 2) 0.6f else 1f)
                    .height(12.dp)
                    .background(Color(0xFFBCBCBC).copy(alpha = alpha), RoundedCornerShape(6.dp)),
            )
        }
    }
}
