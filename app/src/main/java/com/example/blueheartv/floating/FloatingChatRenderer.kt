package com.example.blueheartv.floating

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.model.Message
import com.example.blueheartv.ui.theme.LightGray
import com.example.blueheartv.ui.theme.MutedText
import com.example.blueheartv.ui.theme.SurfaceWhite
import com.example.blueheartv.ui.theme.TextBlack
import com.example.blueheartv.ui.theme.TextDark
import kotlinx.coroutines.delay

@Immutable
internal data class FloatingChatMetrics(
    val sizeClass: FloatingChatSizeClass,
    val cardRadius: Dp,
    val headerHorizontalPadding: Dp,
    val headerVerticalPadding: Dp,
    val headerButtonSize: Dp,
    val headerIconSize: Dp,
    val titleFontSize: TextUnit,
    val listHorizontalPadding: Dp,
    val listVerticalPadding: Dp,
    val itemSpacing: Dp,
    val avatarSize: Dp,
    val avatarSpacing: Dp,
    val bubbleHorizontalPadding: Dp,
    val bubbleVerticalPadding: Dp,
    val bubbleRadius: Dp,
    val bodyFontSize: TextUnit,
    val bodyLineHeight: TextUnit,
    val codeFontSize: TextUnit,
    val codeLineHeight: TextUnit,
    val codePadding: Dp,
    val markdownSpacing: Dp,
    val inputOuterPadding: Dp,
    val inputHeight: Dp,
    val inputHorizontalPadding: Dp,
    val inputActionSize: Dp,
    val inputIconSize: Dp,
    val inputFontSize: TextUnit,
    val sendButtonSize: Dp,
)

internal fun floatingChatMetrics(sizeClass: FloatingChatSizeClass): FloatingChatMetrics {
    return when (sizeClass) {
        FloatingChatSizeClass.SMALL -> FloatingChatMetrics(
            sizeClass = sizeClass,
            cardRadius = 14.dp,
            headerHorizontalPadding = 8.dp,
            headerVerticalPadding = 6.dp,
            headerButtonSize = 28.dp,
            headerIconSize = 14.dp,
            titleFontSize = 14.sp,
            listHorizontalPadding = 6.dp,
            listVerticalPadding = 4.dp,
            itemSpacing = 4.dp,
            avatarSize = 20.dp,
            avatarSpacing = 4.dp,
            bubbleHorizontalPadding = 8.dp,
            bubbleVerticalPadding = 6.dp,
            bubbleRadius = 12.dp,
            bodyFontSize = 13.sp,
            bodyLineHeight = 18.sp,
            codeFontSize = 11.sp,
            codeLineHeight = 16.sp,
            codePadding = 6.dp,
            markdownSpacing = 4.dp,
            inputOuterPadding = 5.dp,
            inputHeight = 34.dp,
            inputHorizontalPadding = 6.dp,
            inputActionSize = 26.dp,
            inputIconSize = 13.dp,
            inputFontSize = 12.sp,
            sendButtonSize = 24.dp,
        )

        FloatingChatSizeClass.MEDIUM -> FloatingChatMetrics(
            sizeClass = sizeClass,
            cardRadius = 16.dp,
            headerHorizontalPadding = 10.dp,
            headerVerticalPadding = 8.dp,
            headerButtonSize = 30.dp,
            headerIconSize = 15.dp,
            titleFontSize = 15.sp,
            listHorizontalPadding = 8.dp,
            listVerticalPadding = 6.dp,
            itemSpacing = 5.dp,
            avatarSize = 24.dp,
            avatarSpacing = 6.dp,
            bubbleHorizontalPadding = 10.dp,
            bubbleVerticalPadding = 8.dp,
            bubbleRadius = 14.dp,
            bodyFontSize = 14.sp,
            bodyLineHeight = 20.sp,
            codeFontSize = 12.sp,
            codeLineHeight = 17.sp,
            codePadding = 8.dp,
            markdownSpacing = 5.dp,
            inputOuterPadding = 7.dp,
            inputHeight = 38.dp,
            inputHorizontalPadding = 8.dp,
            inputActionSize = 28.dp,
            inputIconSize = 14.dp,
            inputFontSize = 13.sp,
            sendButtonSize = 26.dp,
        )

        FloatingChatSizeClass.LARGE -> FloatingChatMetrics(
            sizeClass = sizeClass,
            cardRadius = 18.dp,
            headerHorizontalPadding = 12.dp,
            headerVerticalPadding = 9.dp,
            headerButtonSize = 32.dp,
            headerIconSize = 16.dp,
            titleFontSize = 16.sp,
            listHorizontalPadding = 10.dp,
            listVerticalPadding = 8.dp,
            itemSpacing = 6.dp,
            avatarSize = 28.dp,
            avatarSpacing = 8.dp,
            bubbleHorizontalPadding = 12.dp,
            bubbleVerticalPadding = 9.dp,
            bubbleRadius = 16.dp,
            bodyFontSize = 15.sp,
            bodyLineHeight = 22.sp,
            codeFontSize = 13.sp,
            codeLineHeight = 18.sp,
            codePadding = 9.dp,
            markdownSpacing = 6.dp,
            inputOuterPadding = 8.dp,
            inputHeight = 42.dp,
            inputHorizontalPadding = 10.dp,
            inputActionSize = 30.dp,
            inputIconSize = 15.dp,
            inputFontSize = 14.sp,
            sendButtonSize = 28.dp,
        )
    }
}

@Composable
internal fun FloatingChatRenderer(
    messages: List<Message>,
    listState: LazyListState,
    metrics: FloatingChatMetrics,
    modifier: Modifier = Modifier,
) {
    val renderMessages = remember(messages) { messages.toFloatingChatMessages() }
    val lastMessage = renderMessages.lastOrNull()

    LaunchedEffect(renderMessages.size, lastMessage?.content?.length) {
        if (renderMessages.isNotEmpty()) {
            delay(80)
            listState.scrollToItem(renderMessages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = metrics.listHorizontalPadding,
            vertical = metrics.listVerticalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
    ) {
        items(
            items = renderMessages,
            key = { it.id },
            contentType = { if (it.isUser) "floating_user" else "floating_assistant" },
        ) { message ->
            if (message.isUser) {
                FloatingUserMessage(message = message, metrics = metrics)
            } else {
                FloatingAssistantMessage(message = message, metrics = metrics)
            }
        }
    }
}

@Composable
private fun FloatingUserMessage(
    message: Message,
    metrics: FloatingChatMetrics,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        BoxWithConstraints {
            val maxBubbleWidth = when (metrics.sizeClass) {
                FloatingChatSizeClass.SMALL -> maxWidth * 0.92f
                FloatingChatSizeClass.MEDIUM -> maxWidth * 0.86f
                FloatingChatSizeClass.LARGE -> maxWidth * 0.78f
            }
            SelectionContainer {
                Text(
                    text = message.content,
                    modifier = Modifier
                        .widthIn(max = maxBubbleWidth)
                        .background(
                            color = SurfaceWhite,
                            shape = RoundedCornerShape(metrics.bubbleRadius),
                        )
                        .padding(
                            horizontal = metrics.bubbleHorizontalPadding,
                            vertical = metrics.bubbleVerticalPadding,
                        ),
                    color = TextDark,
                    fontSize = metrics.bodyFontSize,
                    lineHeight = metrics.bodyLineHeight,
                )
            }
        }
    }
}

@Composable
private fun FloatingAssistantMessage(
    message: Message,
    metrics: FloatingChatMetrics,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_echo_face),
            contentDescription = null,
            modifier = Modifier.size(metrics.avatarSize),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.width(metrics.avatarSpacing))
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val maxBubbleWidth = when (metrics.sizeClass) {
                FloatingChatSizeClass.SMALL -> maxWidth
                FloatingChatSizeClass.MEDIUM -> maxWidth * 0.96f
                FloatingChatSizeClass.LARGE -> maxWidth * 0.9f
            }
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .background(
                        color = LightGray,
                        shape = RoundedCornerShape(metrics.bubbleRadius),
                    )
                    .padding(
                        horizontal = metrics.bubbleHorizontalPadding,
                        vertical = metrics.bubbleVerticalPadding,
                    ),
            ) {
                FloatingMarkdownRenderer(
                    content = message.content,
                    metrics = metrics,
                )
            }
        }
    }
}

@Composable
private fun FloatingMarkdownRenderer(
    content: String,
    metrics: FloatingChatMetrics,
) {
    val blocks = remember(content) { parseFloatingMarkdownBlocks(content) }

    Column(verticalArrangement = Arrangement.spacedBy(metrics.markdownSpacing)) {
        blocks.forEach { block ->
            when (block) {
                is FloatingMarkdownBlock.TextBlock -> FloatingMarkdownText(
                    text = block.text,
                    metrics = metrics,
                )

                is FloatingMarkdownBlock.CodeBlock -> FloatingCodeBlock(
                    block = block,
                    metrics = metrics,
                )
            }
        }
    }
}

@Composable
private fun FloatingMarkdownText(
    text: String,
    metrics: FloatingChatMetrics,
) {
    val lines = remember(text) { text.lines() }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            val heading = FLOATING_HEADING_REGEX.matchEntire(line)
            val bullet = FLOATING_BULLET_REGEX.matchEntire(line)
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                heading != null -> {
                    val level = heading.groupValues[1].length
                    val headingSize = when (level) {
                        1 -> (metrics.bodyFontSize.value + 3f).sp
                        2 -> (metrics.bodyFontSize.value + 2f).sp
                        else -> (metrics.bodyFontSize.value + 1f).sp
                    }
                    Text(
                        text = heading.groupValues[2],
                        color = TextBlack,
                        fontSize = headingSize,
                        lineHeight = (headingSize.value + 4f).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                bullet != null -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "•",
                            color = TextBlack,
                            fontSize = metrics.bodyFontSize,
                            lineHeight = metrics.bodyLineHeight,
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = bullet.groupValues[1],
                            modifier = Modifier.weight(1f),
                            color = TextBlack,
                            fontSize = metrics.bodyFontSize,
                            lineHeight = metrics.bodyLineHeight,
                        )
                    }
                }

                else -> Text(
                    text = line,
                    color = TextBlack,
                    fontSize = metrics.bodyFontSize,
                    lineHeight = metrics.bodyLineHeight,
                )
            }
        }
    }
}

@Composable
private fun FloatingCodeBlock(
    block: FloatingMarkdownBlock.CodeBlock,
    metrics: FloatingChatMetrics,
) {
    val clipboardManager = LocalClipboardManager.current
    val shape = RoundedCornerShape(metrics.bubbleRadius / 2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF111827))
            .padding(metrics.codePadding),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.language.ifBlank { "code" },
                modifier = Modifier.weight(1f),
                color = Color(0xFF9CA3AF),
                fontSize = metrics.codeFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(metrics.inputActionSize)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(block.code))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    modifier = Modifier.size(metrics.inputIconSize),
                    tint = Color.White,
                )
            }
        }
        SelectionContainer {
            Text(
                text = block.code,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                color = Color(0xFFF9FAFB),
                fontSize = metrics.codeFontSize,
                fontFamily = FontFamily.Monospace,
                lineHeight = metrics.codeLineHeight,
            )
        }
    }
}

private sealed interface FloatingMarkdownBlock {
    data class TextBlock(val text: String) : FloatingMarkdownBlock

    data class CodeBlock(
        val language: String,
        val code: String,
    ) : FloatingMarkdownBlock
}

private fun parseFloatingMarkdownBlocks(content: String): List<FloatingMarkdownBlock> {
    if (!content.contains("```")) {
        return listOf(FloatingMarkdownBlock.TextBlock(content))
    }

    val blocks = mutableListOf<FloatingMarkdownBlock>()
    var cursor = 0

    while (cursor < content.length) {
        val codeStart = content.indexOf("```", cursor)
        if (codeStart < 0) {
            blocks.add(FloatingMarkdownBlock.TextBlock(content.substring(cursor)))
            break
        }

        if (codeStart > cursor) {
            blocks.add(FloatingMarkdownBlock.TextBlock(content.substring(cursor, codeStart)))
        }

        val headerEnd = content.indexOf('\n', codeStart + 3)
        if (headerEnd < 0) {
            blocks.add(FloatingMarkdownBlock.TextBlock(content.substring(codeStart)))
            break
        }

        val language = content.substring(codeStart + 3, headerEnd).trim()
        val codeEnd = content.indexOf("```", headerEnd + 1)
        if (codeEnd < 0) {
            blocks.add(FloatingMarkdownBlock.TextBlock(content.substring(codeStart)))
            break
        }

        blocks.add(
            FloatingMarkdownBlock.CodeBlock(
                language = language,
                code = content.substring(headerEnd + 1, codeEnd).trimEnd(),
            )
        )
        cursor = codeEnd + 3
    }

    return blocks.ifEmpty { listOf(FloatingMarkdownBlock.TextBlock(content)) }
}

private val FLOATING_HEADING_REGEX = Regex("^\\s*(#{1,6})\\s+(.+?)\\s*$")
private val FLOATING_BULLET_REGEX = Regex("^\\s*[-*+]\\s+(.+?)\\s*$")
