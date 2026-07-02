package com.example.blueheartv.viewmodel

import com.example.blueheartv.chat.ChatStreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskProgressReducerTest {

    @Test
    fun reduce_buildsTaskProgressStateFromStructuredEvent() {
        val state = TaskProgressReducer.reduce(
            current = null,
            event = ChatStreamEvent.TaskProgress(
                label = "会议通知",
                taskTitle = "为会议通知创建提醒",
                status = "waiting_confirmation",
                phase = "confirmation",
                stepTitle = "等待确认是否创建会议提醒",
                message = "检测到会议通知，是否创建提醒？",
                toolName = "needs_confirmation",
                currentStep = 2,
                totalSteps = 3,
                requiresConfirmation = true,
                confirmationId = "confirm-123",
                canCancel = true,
                canTakeOver = true,
                runId = "run-1",
                threadId = "thread-1",
            ),
        )

        assertEquals("run-1", state.runId)
        assertEquals("thread-1", state.threadId)
        assertEquals("为会议通知创建提醒", state.taskTitle)
        assertEquals("waiting_confirmation", state.status)
        assertEquals(2, state.currentStep)
        assertEquals(3, state.totalSteps)
        assertEquals("等待确认是否创建会议提醒", state.stepTitle)
        assertEquals("confirmation", state.phase)
        assertEquals("needs_confirmation", state.toolName)
        assertTrue(state.requiresConfirmation)
        assertEquals("confirm-123", state.confirmationId)
        assertTrue(state.canCancel)
        assertTrue(state.canTakeOver)
        assertEquals("检测到会议通知，是否创建提醒？", state.message)
        assertTrue(state.isTerminal.not())
    }

    @Test
    fun reduce_terminalStatesKeepVisibleStatusButDisableControls() {
        val current = TaskProgressState(
            runId = "run-1",
            threadId = "thread-1",
            taskTitle = "为会议通知创建提醒",
            status = "running",
            currentStep = 3,
            totalSteps = 3,
            stepTitle = "正在创建会议提醒",
            phase = "system_tool",
            toolName = "create_event",
            requiresConfirmation = false,
            confirmationId = null,
            canCancel = true,
            canTakeOver = true,
            message = "正在创建会议提醒",
        )

        val state = TaskProgressReducer.reduce(
            current = current,
            event = ChatStreamEvent.TaskProgress(
                label = "会议通知",
                taskTitle = "为会议通知创建提醒",
                status = "completed",
                phase = "finalizing",
                stepTitle = "会议提醒已创建",
                message = "会议提醒已创建",
                currentStep = 3,
                totalSteps = 3,
                canCancel = false,
                canTakeOver = false,
            ),
        )

        assertEquals("completed", state.status)
        assertEquals("会议提醒已创建", state.stepTitle)
        assertFalse(state.requiresConfirmation)
        assertFalse(state.canCancel)
        assertFalse(state.canTakeOver)
        assertTrue(state.isTerminal)
    }

    @Test
    fun reduce_differentRunPhoneProgressDoesNotInheritScenarioTaskFields() {
        val scenarioTask = TaskProgressState(
            runId = "scenario-run",
            threadId = "thread-1",
            taskTitle = "为会议通知创建提醒",
            status = "waiting_confirmation",
            currentStep = 2,
            totalSteps = 3,
            stepTitle = "等待确认是否创建会议提醒",
            phase = "confirmation",
            toolName = "needs_confirmation",
            requiresConfirmation = true,
            confirmationId = "confirm-1",
            canCancel = true,
            canTakeOver = true,
            message = "检测到会议通知，是否创建提醒？",
        )

        val state = TaskProgressReducer.reduce(
            current = scenarioTask,
            event = ChatStreamEvent.TaskProgress(
                label = "launch",
                status = "running",
                phase = "phone_tool",
                message = "Running phone tool: launch",
                toolName = "launch",
                canCancel = true,
                canTakeOver = true,
                runId = "phone-run",
                threadId = "thread-1",
            ),
        )

        assertEquals("phone-run", state.runId)
        assertEquals("打开手机应用", state.taskTitle)
        assertEquals(null, state.currentStep)
        assertEquals(null, state.totalSteps)
        assertEquals("Running phone tool: launch", state.stepTitle)
        assertFalse(state.requiresConfirmation)
        assertEquals(null, state.confirmationId)
        assertTrue(state.canCancel)
        assertTrue(state.canTakeOver)
    }

    @Test
    fun reduce_missingTaskTitlePhoneProgressUsesSafeFallbackInsteadOfPreviousTask() {
        val scenarioTask = TaskProgressState(
            runId = null,
            threadId = null,
            taskTitle = "为会议通知创建提醒",
            status = "completed",
            currentStep = 3,
            totalSteps = 3,
            stepTitle = "会议提醒已创建",
            phase = "finalizing",
            toolName = "create_event",
            requiresConfirmation = false,
            confirmationId = null,
            canCancel = false,
            canTakeOver = false,
            message = "会议提醒已创建",
        )

        val state = TaskProgressReducer.reduce(
            current = scenarioTask,
            event = ChatStreamEvent.TaskProgress(
                label = "observe",
                status = "running",
                phase = "phone_tool",
                message = "Running phone tool: observe",
                toolName = "observe",
                canCancel = true,
                canTakeOver = true,
            ),
        )

        assertEquals("观察当前屏幕", state.taskTitle)
        assertEquals(null, state.currentStep)
        assertEquals(null, state.totalSteps)
        assertEquals("observe", state.toolName)
        assertTrue(state.canCancel)
        assertTrue(state.canTakeOver)
    }
}
