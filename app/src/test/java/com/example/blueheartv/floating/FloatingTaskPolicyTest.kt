package com.example.blueheartv.floating

import com.example.blueheartv.viewmodel.TaskComplexityLevel
import com.example.blueheartv.viewmodel.TaskProgressState
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingTaskPolicyTest {

    @Test
    fun notificationText_neverReportsCompletedForWaitingOrRunningTask() {
        assertEquals(
            "等待确认",
            floatingTaskNotificationText(task(status = "waiting_confirmation")),
        )
        assertEquals(
            "任务执行中",
            floatingTaskNotificationText(task(status = "running")),
        )
        assertEquals(
            "任务已完成",
            floatingTaskNotificationText(task(status = "completed")),
        )
    }

    @Test
    fun complexTaskComplexity_keepsOrOpensChatWindowInsteadOfCollapsingToNotification() {
        assertEquals(
            FloatingTaskComplexityAction.OPEN_CHAT,
            floatingTaskComplexityAction(
                currentState = FloatingState.STATE1,
                complexity = TaskComplexityLevel.COMPLEX,
            ),
        )
        assertEquals(
            FloatingTaskComplexityAction.KEEP,
            floatingTaskComplexityAction(
                currentState = FloatingState.STATE2,
                complexity = TaskComplexityLevel.COMPLEX,
            ),
        )
    }

    @Test
    fun taskActions_showCancelAndTakeOverForRunningAndWaitingConfirmation() {
        assertEquals(
            listOf(FloatingTaskAction.CANCEL_TASK, FloatingTaskAction.TAKE_OVER),
            floatingTaskActions(
                task(
                    status = "running",
                    canCancel = true,
                    canTakeOver = true,
                ),
            ),
        )
        assertEquals(
            listOf(
                FloatingTaskAction.CONFIRM,
                FloatingTaskAction.REJECT,
                FloatingTaskAction.CANCEL_TASK,
                FloatingTaskAction.TAKE_OVER,
            ),
            floatingTaskActions(
                task(
                    status = "waiting_confirmation",
                    requiresConfirmation = true,
                    confirmationId = "confirm-1",
                    canCancel = true,
                    canTakeOver = true,
                ),
            ),
        )
        assertEquals(
            emptyList<FloatingTaskAction>(),
            floatingTaskActions(task(status = "completed")),
        )
    }

    private fun task(
        status: String,
        requiresConfirmation: Boolean = false,
        confirmationId: String? = null,
        canCancel: Boolean = false,
        canTakeOver: Boolean = false,
    ): TaskProgressState = TaskProgressState(
        runId = "run-1",
        threadId = "thread-1",
        taskTitle = "为会议通知创建提醒",
        status = status,
        currentStep = 2,
        totalSteps = 3,
        stepTitle = "等待确认是否创建会议提醒",
        phase = "confirmation",
        toolName = "needs_confirmation",
        requiresConfirmation = requiresConfirmation,
        confirmationId = confirmationId,
        canCancel = canCancel,
        canTakeOver = canTakeOver,
        message = "检测到会议通知，是否创建提醒？",
    )
}
