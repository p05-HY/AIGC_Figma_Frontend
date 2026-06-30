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
    fun clearAll_removesActiveStreams() {
        val accumulator = StreamTextAccumulator()
        accumulator.append("assistant-1", invocationId = null, chunk = "one")
        accumulator.append("assistant-2", invocationId = null, chunk = "two")

        accumulator.clearAll()

        assertEquals(0, accumulator.activeCount)
        assertEquals("", accumulator.take("assistant-1"))
    }
}
