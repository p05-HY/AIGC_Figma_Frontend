package com.example.blueheartv.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.example.blueheartv.model.Message
import com.example.blueheartv.ui.theme.MutedText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class ConversationGroup(
    val id: String,
    val timestamp: Long,
    val messages: List<Message>,
)

internal sealed interface ChatListEntry {
    val key: String

    data class Header(
        val groupId: String,
        val timestamp: Long,
    ) : ChatListEntry {
        override val key: String = "header-$groupId"
    }

    data class MessageItem(
        val message: Message,
    ) : ChatListEntry {
        override val key: String = "message-${message.id}"
    }
}

private data class MutableConversationGroup(
    val id: String,
    val timestamp: Long,
    val messages: MutableList<Message>,
)

internal fun groupConversationMessages(messages: List<Message>): List<ConversationGroup> {
    val groups = mutableListOf<MutableConversationGroup>()

    messages.forEach { message ->
        if (message.isUser || groups.isEmpty()) {
            groups += MutableConversationGroup(
                id = "conversation-${message.id}",
                timestamp = message.timestamp,
                messages = mutableListOf(message),
            )
        } else {
            groups.last().messages += message
        }
    }

    return groups.map { group ->
        ConversationGroup(
            id = group.id,
            timestamp = group.timestamp,
            messages = group.messages,
        )
    }
}

internal fun chatListEntries(groups: List<ConversationGroup>): List<ChatListEntry> =
    buildList {
        groups.forEach { group ->
            add(ChatListEntry.Header(group.id, group.timestamp))
            group.messages.forEach { message ->
                add(ChatListEntry.MessageItem(message))
            }
        }
    }

internal fun shouldAutoFollowChatScroll(
    lastVisibleIndex: Int,
    totalItems: Int,
    thresholdItems: Int = 3,
): Boolean {
    if (totalItems <= 0) return true
    return lastVisibleIndex >= totalItems - thresholdItems
}

internal fun streamingScrollBucket(contentLength: Int, bucketSize: Int = 240): Int =
    if (contentLength <= 0) 0 else contentLength / bucketSize

internal fun formatConversationTimestamp(
    timestamp: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", locale).apply {
        this.timeZone = timeZone
    }.format(Date(timestamp))
}

@Composable
internal fun ConversationTimestampHeader(
    timestamp: Long,
    modifier: Modifier = Modifier,
) {
    val text = remember(timestamp) { formatConversationTimestamp(timestamp) }

    Text(
        text = text,
        fontSize = 12.sp,
        color = MutedText,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
