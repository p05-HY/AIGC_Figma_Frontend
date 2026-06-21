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
}
