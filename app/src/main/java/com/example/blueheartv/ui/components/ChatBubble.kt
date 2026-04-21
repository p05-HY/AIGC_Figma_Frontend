package com.example.blueheartv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.ui.theme.*

@Composable
fun UserBubble(
    message: Message,
    modifier: Modifier = Modifier,
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 24.dp,
        bottomEnd = 10.dp,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .border(1.dp, BorderGray, bubbleShape)
                .background(SurfaceWhite, bubbleShape)
                .padding(16.dp),
        ) {
            Text(
                text = message.content,
                fontSize = 16.sp,
                color = TextDark,
                lineHeight = 24.sp,
            )
        }
    }
}

@Composable
fun AiBubble(
    message: Message,
    modifier: Modifier = Modifier,
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 10.dp,
        bottomEnd = 24.dp,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // Robot avatar
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(4.dp, CircleShape)
                .background(SurfaceWhite, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.robot_mascot),
                contentDescription = null,
                modifier = Modifier.size(24.dp, 36.dp),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .shadow(2.dp, bubbleShape)
                    .background(LightGray, bubbleShape)
                    .padding(16.dp),
            ) {
                if (message.isLoading) {
                    LoadingDots()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        message.toolCalls?.takeIf { it.isNotEmpty() }?.let { toolCalls ->
                            ToolCallCard(toolCalls = toolCalls)
                        }
                        MarkdownMessageContent(content = message.content)
                    }
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
private fun MarkdownMessageContent(content: String) {
    val clipboardManager = LocalClipboardManager.current
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.TextBlock -> {
                    if (block.text.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = block.text,
                                fontSize = 16.sp,
                                color = TextBlack,
                                lineHeight = 24.sp,
                            )
                        }
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
