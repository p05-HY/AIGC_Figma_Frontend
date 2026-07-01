package com.example.blueheartv.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamTextAccumulatorTest {

    @Test
    fun append_collectsManyChunksWithoutLosingText() {
        val accumulator = StreamTextAccumulator()

        repeat(100) { index ->
            accumulator.append("assistant-1", invocationId = "model-final", chunk = "$index,")
        }

        assertEquals((0 until 100).joinToString(separator = "") { "$it," }, accumulator.take("assistant-1"))
        assertEquals(0, accumulator.activeCount)
    }

    @Test
    fun append_resetsWhenInvocationChanges() {
        val accumulator = StreamTextAccumulator()

        accumulator.append("assistant-1", invocationId = "model-1", chunk = "draft")
        val latest = accumulator.append("assistant-1", invocationId = "model-2", chunk = "final")

        assertEquals("final", latest)
        assertEquals("final", accumulator.take("assistant-1"))
    }

    @Test
    fun append_stripsStandaloneClosingThinkTagAcrossChunks() {
        val accumulator = StreamTextAccumulator()

        val first = accumulator.append("assistant-1", runId = "run-1", invocationId = "model-final", chunk = "</")
        val second = accumulator.append("assistant-1", runId = "run-1", invocationId = "model-final", chunk = "think>最终")

        assertEquals("", first)
        assertEquals("最终", second)
        assertEquals("最终", accumulator.take("assistant-1", runId = "run-1"))
    }

    @Test
    fun append_stripsStandaloneClosingThinkingTagAcrossChunks() {
        val accumulator = StreamTextAccumulator()

        val first = accumulator.append("assistant-1", runId = "run-1", invocationId = "model-final", chunk = "OK\n")
        val second = accumulator.append("assistant-1", runId = "run-1", invocationId = "model-final", chunk = "</")
        val third = accumulator.append("assistant-1", runId = "run-1", invocationId = "model-final", chunk = "thinking>")

        assertEquals("OK\n", first)
        assertEquals("OK\n", second)
        assertEquals("OK\n", third)
        assertEquals("OK\n", accumulator.take("assistant-1", runId = "run-1"))
    }

    @Test
    fun append_isIsolatedByRunIdAndMessageId() {
        val accumulator = StreamTextAccumulator()

        accumulator.append("assistant-1", runId = "old-run", invocationId = "model-final", chunk = "old")
        val current = accumulator.append("assistant-1", runId = "new-run", invocationId = "model-final", chunk = "new")

        assertEquals("new", current)
        assertEquals("old", accumulator.take("assistant-1", runId = "old-run"))
        assertEquals("new", accumulator.take("assistant-1", runId = "new-run"))
    }

    @Test
    fun clearAll_removesActiveStreams() {
        val accumulator = StreamTextAccumulator()
        accumulator.append("assistant-1", invocationId = null, chunk = "one")
        accumulator.append("assistant-2", invocationId = null, chunk = "two")

        accumulator.clearAll()

        assertEquals(0, accumulator.activeCount)
        assertEquals("", accumulator.take("assistant-1"))
    }
}
