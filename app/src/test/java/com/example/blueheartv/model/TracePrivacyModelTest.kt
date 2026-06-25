package com.example.blueheartv.model

import org.junit.Assert.assertFalse
import org.junit.Test

class TracePrivacyModelTest {

    @Test
    fun messageAndLegacyToolModels_doNotRetainPrivateReasoningOrRawToolData() {
        val messageFields = Message::class.java.declaredFields.map { it.name }.toSet()
        val toolFields = ToolCall::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("thinking" in messageFields)
        assertFalse("args" in toolFields)
        assertFalse("result" in toolFields)
        assertFalse("error" in toolFields)
    }
}
