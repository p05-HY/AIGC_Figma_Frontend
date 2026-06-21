package com.example.blueheartv.ui.components

import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolProgressSummaryTest {

    @Test
    fun summarizeToolProgress_prefersExplicitCurrentAndTotalSteps() {
        val summary = summarizeToolProgress(
            listOf(
                ToolCall(label = "规划路线", status = ToolCallStatus.RUNNING, currentStep = 2, totalSteps = 5),
            ),
        )

        assertEquals("执行进度 2/5", summary.label)
        assertEquals(0.4f, summary.progress)
        assertFalse(summary.indeterminate)
    }

    @Test
    fun summarizeToolProgress_usesRunningExplicitProgressInsteadOfMixingMaxValues() {
        val summary = summarizeToolProgress(
            listOf(
                ToolCall(label = "已完成阶段", status = ToolCallStatus.COMPLETED, currentStep = 5, totalSteps = 5),
                ToolCall(label = "当前阶段", status = ToolCallStatus.RUNNING, currentStep = 1, totalSteps = 10),
            ),
        )

        assertEquals("执行进度 1/10", summary.label)
        assertEquals(0.1f, summary.progress)
        assertFalse(summary.indeterminate)
    }

    @Test
    fun summarizeToolProgress_withoutTotalSteps_usesIndeterminateRunningState() {
        val summary = summarizeToolProgress(
            listOf(
                ToolCall(label = "观察屏幕", status = ToolCallStatus.COMPLETED),
                ToolCall(label = "点击按钮", status = ToolCallStatus.RUNNING),
            ),
        )

        assertEquals("正在执行 1/2", summary.label)
        assertNull(summary.progress)
        assertTrue(summary.indeterminate)
    }

    @Test
    fun summarizeToolProgress_whenAllFinished_usesCompletedCount() {
        val summary = summarizeToolProgress(
            listOf(
                ToolCall(label = "观察屏幕", status = ToolCallStatus.COMPLETED),
                ToolCall(label = "点击按钮", status = ToolCallStatus.FAILED),
                ToolCall(label = "返回结果", status = ToolCallStatus.COMPLETED),
            ),
        )

        assertEquals("执行进度 2/3", summary.label)
        assertEquals(2f / 3f, summary.progress)
        assertFalse(summary.indeterminate)
    }
}
