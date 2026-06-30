package com.example.blueheartv.ui.screens

import com.example.blueheartv.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

class ConversationGroupingTest {

    @Test
    fun groupConversationMessages_groupsAssistantReplyWithItsUserMessage() {
        val firstTurnTime = 1_000L
        val secondTurnTime = 2_000L
        val messages = listOf(
            Message(id = "user-1", content = "你好", isUser = true, timestamp = firstTurnTime),
            Message(id = "assistant-1", content = "你好呀", isUser = false, timestamp = firstTurnTime + 500L),
            Message(id = "user-2", content = "今天天气", isUser = true, timestamp = secondTurnTime),
            Message(id = "assistant-2", content = "晴天", isUser = false, timestamp = secondTurnTime + 500L),
        )

        val groups = groupConversationMessages(messages)

        assertEquals(2, groups.size)
        assertEquals(firstTurnTime, groups[0].timestamp)
        assertEquals(listOf("user-1", "assistant-1"), groups[0].messages.map { it.id })
        assertEquals(secondTurnTime, groups[1].timestamp)
        assertEquals(listOf("user-2", "assistant-2"), groups[1].messages.map { it.id })
    }

    @Test
    fun formatConversationTimestamp_usesFullDateAndMinute() {
        val timestamp = LocalDateTime.of(2025, 1, 15, 13, 26)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

        val text = formatConversationTimestamp(
            timestamp = timestamp,
            locale = Locale.CHINA,
            timeZone = TimeZone.getTimeZone("UTC"),
        )

        assertEquals("2025-01-15 13:26", text)
    }

    @Test
    fun chatListEntries_useStableKeysForHeadersAndMessages() {
        val messages = listOf(
            Message(id = "user-1", content = "你好", isUser = true, timestamp = 1_000L),
            Message(id = "assistant-1", content = "你好呀", isUser = false, timestamp = 1_500L),
        )

        val entries = chatListEntries(groupConversationMessages(messages))

        assertEquals(
            listOf(
                "header-conversation-user-1",
                "message-user-1",
                "message-assistant-1",
            ),
            entries.map { it.key },
        )
    }

    @Test
    fun shouldAutoFollowChatScroll_onlyWhenUserIsNearBottom() {
        assertEquals(true, shouldAutoFollowChatScroll(lastVisibleIndex = -1, totalItems = 0))
        assertEquals(true, shouldAutoFollowChatScroll(lastVisibleIndex = 7, totalItems = 10))
        assertEquals(false, shouldAutoFollowChatScroll(lastVisibleIndex = 4, totalItems = 10))
    }

    @Test
    fun streamingScrollBucket_throttlesContentLengthChanges() {
        assertEquals(0, streamingScrollBucket(contentLength = 0))
        assertEquals(0, streamingScrollBucket(contentLength = 239))
        assertEquals(1, streamingScrollBucket(contentLength = 240))
        assertEquals(1, streamingScrollBucket(contentLength = 479))
        assertEquals(2, streamingScrollBucket(contentLength = 480))
    }
}
