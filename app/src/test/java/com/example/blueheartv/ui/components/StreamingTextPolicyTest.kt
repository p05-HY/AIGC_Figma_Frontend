package com.example.blueheartv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingTextPolicyTest {

    @Test
    fun streamingDisplayText_returnsFullDeltaImmediately() {
        val longText = "这是一段较长的流式文本".repeat(80)

        assertEquals(longText, streamingDisplayText(longText, isStreaming = true))
        assertEquals(longText, streamingDisplayText(longText, isStreaming = false))
    }
}
