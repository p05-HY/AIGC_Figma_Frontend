package com.example.blueheartv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.BuildConfig
import com.example.blueheartv.R
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.model.ToolCallStatus
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.util.ToastType
import com.example.blueheartv.util.ToastUtil
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

internal fun streamingDisplayText(fullText: String, isStreaming: Boolean): String =
    if (isStreaming) fullText else fullText

@Composable
fun rememberTypewriterText(fullText: String, isStreaming: Boolean): String {
    return streamingDisplayText(fullText, isStreaming)
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
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 6.dp,
    )
    val colorScheme = MaterialTheme.colorScheme

    var showMenu by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(message.content) }
    var enableTextSelection by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val deleteTitle = stringResource(R.string.delete_message_title)
    val deleteMessage = stringResource(R.string.delete_user_message_message)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)
    val copyText = stringResource(R.string.action_copy_message)
    val editTextLabel = stringResource(R.string.action_edit_message)
    val selectText = stringResource(R.string.action_select_text)
    val deleteText = stringResource(R.string.action_delete)

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(deleteTitle) },
            text = { Text(deleteMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(message.id)
                }) { Text(confirmText) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(cancelText) }
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
            val bubbleMaxWidth = (maxWidth * 0.82f).coerceIn(200.dp, 480.dp)
            Column(horizontalAlignment = Alignment.End) {
                Box {
                    Box(
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f), bubbleShape)
                        .background(colorScheme.primaryContainer.copy(alpha = 0.78f), bubbleShape)
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
                                    color = colorScheme.onPrimaryContainer,
                                    lineHeight = 24.sp,
                                ),
                                cursorBrush = SolidColor(colorScheme.primary),
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
                                    Icon(Icons.Outlined.Close, cancelText, tint = ErrorRed)
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
                                    Icon(Icons.Outlined.Check, confirmText, tint = BlueAccent)
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
                                color = colorScheme.onPrimaryContainer,
                                lineHeight = 24.sp,
                            )
                        }
                    } else {
                        Text(
                            text = message.content,
                            fontSize = 16.sp,
                            color = colorScheme.onPrimaryContainer,
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
                        text = { Text(copyText) },
                        onClick = {
                            showMenu = false
                            onCopy(message.content)
                        },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text(editTextLabel) },
                        onClick = {
                            showMenu = false
                            editText = message.content
                            isEditing = true
                        },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text(selectText) },
                        onClick = {
                            showMenu = false
                            enableTextSelection = true
                        },
                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text(deleteText, color = ErrorRed) },
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
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 6.dp,
        bottomEnd = 20.dp,
    )
    val colorScheme = MaterialTheme.colorScheme

    var showMenu by remember { mutableStateOf(false) }
    var enableTextSelection by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val deleteTitle = stringResource(R.string.delete_message_title)
    val deleteMessage = stringResource(R.string.delete_ai_message_message)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)
    val selectText = stringResource(R.string.action_select_text)
    val deleteText = stringResource(R.string.action_delete)

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(deleteTitle) },
            text = { Text(deleteMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(message.id)
                }) { Text(confirmText) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(cancelText) }
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
            val bubbleMaxWidth = (maxWidth * 0.88f).coerceIn(220.dp, 560.dp)
            Column {
                Box {
                    Box(
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.36f), bubbleShape)
                        .background(colorScheme.surfaceContainerLow.copy(alpha = 0.92f), bubbleShape)
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
                        val traceVisible = BuildConfig.TRACE_V1_RENDER_ENABLED && message.trace != null
                        val shouldShowAssistantText = message.content.isNotBlank() || !traceVisible
                        val displayText = rememberTypewriterText(
                            if (shouldShowAssistantText) {
                                message.content.ifBlank {
                                    when (message.deliveryState) {
                                        com.example.blueheartv.model.MessageDeliveryState.STREAMING -> "Echo 正在输入..."
                                        com.example.blueheartv.model.MessageDeliveryState.FAILED -> "响应失败，请重试"
                                        else -> message.trace?.summary ?: "已收到回复"
                                    }
                                }
                            } else {
                                ""
                            },
                            isStreaming,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (traceVisible) {
                                TraceTimeline(trace = requireNotNull(message.trace))
                            }
                            message.toolCalls?.takeIf { it.isNotEmpty() && !traceVisible }?.let { toolCalls ->
                                ToolCallCard(toolCalls = toolCalls)
                            }
                            if (shouldShowAssistantText && displayText.isNotBlank()) {
                                if (enableTextSelection) {
                                    SelectionContainer {
                                        MarkdownMessageContent(content = displayText)
                                    }
                                } else {
                                    MarkdownMessageContent(content = displayText)
                                }
                            }
                            if (isStreaming && message.content.isNotBlank()) {
                                Text(
                                    text = "Echo 正在输入...",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
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
                        text = { Text(selectText) },
                        onClick = {
                            showMenu = false
                            enableTextSelection = true
                        },
                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text(deleteText, color = ErrorRed) },
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
            }
        }
    }
}

@Composable
private fun ToolCallCard(toolCalls: List<ToolCall>) {
    val summary = remember(toolCalls) { summarizeToolProgress(toolCalls) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(0.5.dp, DividerColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = summary.label,
            fontSize = 12.sp,
            color = MutedText,
        )
        if (summary.indeterminate) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = BlueAccent,
                trackColor = DividerColor,
            )
        } else {
            LinearProgressIndicator(
                progress = { summary.progress ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = BlueAccent,
                trackColor = DividerColor,
            )
        }
        toolCalls.forEach { toolCall ->
            ToolCallRow(toolCall = toolCall)
        }
    }
}

@Composable
private fun ToolCallRow(toolCall: ToolCall) {
    val display = remember(toolCall) { displayToolCall(toolCall) }

    val dotColor = when (toolCall.status) {
        ToolCallStatus.RUNNING -> BrandPrimary
        ToolCallStatus.COMPLETED -> SuccessGreen
        ToolCallStatus.FAILED -> ErrorRed
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = buildString {
                        append(display.title)
                        val step = formatStep(toolCall)
                        if (step != null) append("  ").append(step)
                    },
                    fontSize = 13.sp,
                    color = TextDarkAlt,
                )
                Text(
                    text = display.subtitle,
                    fontSize = 11.sp,
                    color = MutedText,
                    lineHeight = 15.sp,
                )
            }
            Text(
                text = display.statusText,
                fontSize = 11.sp,
                color = dotColor,
            )
        }
    }
}

private fun formatStep(toolCall: ToolCall): String? {
    val current = toolCall.currentStep
    val total = toolCall.totalSteps
    return when {
        current != null && total != null -> "($current/$total)"
        current != null -> "(第 $current 步)"
        total != null -> "(共 $total 步)"
        else -> null
    }
}

@Composable
fun MarkdownMessageContent(content: String) {
    val clipboardManager = LocalClipboardManager.current
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> MarkdownInlineText(block.inlines)
                is MarkdownBlock.Heading -> MarkdownHeading(block)
                is MarkdownBlock.Quote -> MarkdownQuote(block.text)
                is MarkdownBlock.BulletList -> MarkdownList(block.items, ordered = false)
                is MarkdownBlock.OrderedList -> MarkdownList(block.items, ordered = true)
                is MarkdownBlock.TableText -> MarkdownCodeLikeText(block.text, label = "表格")
                is MarkdownBlock.CodeBlock -> MarkdownCodeBlock(block, onCopy = {
                    clipboardManager.setText(AnnotatedString(block.code))
                })
            }
        }
    }
}

@Composable
private fun MarkdownHeading(block: MarkdownBlock.Heading) {
    Text(
        text = block.text,
        fontSize = if (block.level == 1) 18.sp else 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextBlack,
        lineHeight = 24.sp,
    )
}

@Composable
private fun MarkdownQuote(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MutedText,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, DividerColor, RoundedCornerShape(8.dp))
            .background(ThinkingCardBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun MarkdownList(items: List<String>, ordered: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (ordered) "${index + 1}." else "•",
                    fontSize = 15.sp,
                    color = MutedText,
                )
                MarkdownInlineText(
                    inlines = parseMarkdownInlines(item),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlock.CodeBlock, onCopy: () -> Unit) {
    val defaultLanguage = stringResource(R.string.code_language_default)
    val copyCode = stringResource(R.string.copy_code)
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
                text = block.language.ifBlank { defaultLanguage },
                fontSize = 12.sp,
                color = CodeBlockLabel,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = copyCode,
                    tint = Color.White,
                )
            }
        }

        MarkdownCodeText(text = block.code)
    }
}

@Composable
private fun MarkdownCodeLikeText(text: String, label: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodeBlockBackground, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        label?.let {
            Text(text = it, fontSize = 12.sp, color = CodeBlockLabel)
            Spacer(modifier = Modifier.height(6.dp))
        }
        MarkdownCodeText(text = text)
    }
}

@Composable
private fun MarkdownCodeText(text: String) {
    SelectionContainer {
        Text(
            text = text,
            fontSize = 13.sp,
            color = CodeBlockText,
            fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun MarkdownInlineText(
    inlines: List<MarkdownInline>,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val linkOpenFailed = stringResource(R.string.link_open_failed)
    val annotated = remember(inlines) { inlines.toAnnotatedString() }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(
            fontSize = 16.sp,
            color = TextBlack,
            lineHeight = 24.sp,
        ),
        onClick = { offset ->
            annotated
                .getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let {
                    runCatching { uriHandler.openUri(it.item) }
                        .onFailure { ToastUtil.show(linkOpenFailed, ToastType.ERROR) }
                }
        },
    )
}

private fun List<MarkdownInline>.toAnnotatedString(): AnnotatedString {
    val builder = AnnotatedString.Builder()
    forEach { inline ->
        when (inline) {
            is MarkdownInline.Text -> builder.append(inline.text)
            is MarkdownInline.Bold -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(inline.text)
            }
            is MarkdownInline.Italic -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(inline.text)
            }
            is MarkdownInline.Code -> builder.withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = ThinkingCardBackground,
                ),
            ) {
                append(inline.text)
            }
            is MarkdownInline.Link -> {
                val start = builder.length
                builder.withStyle(
                    SpanStyle(
                        color = BlueAccent,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append(inline.text)
                }
                builder.addStringAnnotation("URL", inline.url, start, builder.length)
            }
        }
    }
    return builder.toAnnotatedString()
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
