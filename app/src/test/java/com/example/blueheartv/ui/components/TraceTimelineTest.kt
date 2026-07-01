package com.example.blueheartv.ui.components

import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceRunStatus
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceTimelineTest {

    @Test
    fun succeededStep_isCollapsedByDefault() {
        assertFalse(defaultTraceStepExpanded(TraceStepStatus.SUCCEEDED))
    }

    @Test
    fun displayState_mapsTerminalStatusCopyAndExpansionRules() {
        val succeeded = traceDisplayState(
            com.example.blueheartv.model.AssistantTrace(
                runId = "run-1",
                runStatus = TraceRunStatus.SUCCEEDED,
                hasTerminal = true,
                steps = listOf(traceStep("step-1", "打开应用").copy(status = TraceStepStatus.SUCCEEDED)),
            ),
        )
        val canceled = traceDisplayState(
            com.example.blueheartv.model.AssistantTrace(
                runId = "run-2",
                runStatus = TraceRunStatus.CANCELLED,
                hasTerminal = true,
            ),
        )
        val running = traceDisplayState(
            com.example.blueheartv.model.AssistantTrace(runId = "run-3"),
        )

        assertEquals("已完成", succeeded.title)
        assertEquals("已完成 1 个步骤", succeeded.subtitle)
        assertFalse(succeeded.defaultExpanded)
        assertEquals("已停止", canceled.title)
        assertFalse(canceled.defaultExpanded)
        assertEquals("正在处理", running.title)
        assertTrue(running.defaultExpanded)
    }

    @Test
    fun liveTraceFirstRender_defaultsExpanded() {
        val snapshot = initialTraceExpansionSnapshot(
            status = TraceRunStatus.RUNNING,
            isLiveMessage = true,
        )

        assertTrue(snapshot.expanded)
        assertFalse(snapshot.userTouchedExpansion)
    }

    @Test
    fun runningTraceKeepsExpandedUnlessUserTouchedIt() {
        val autoState = traceExpansionAfterStatusChange(
            TraceExpansionSnapshot(
                expanded = false,
                userTouchedExpansion = false,
                showAllSteps = false,
                observedStatus = TraceRunStatus.RUNNING,
            ),
            nextStatus = TraceRunStatus.RUNNING,
        )
        val userCollapsedState = traceExpansionAfterStatusChange(
            TraceExpansionSnapshot(
                expanded = false,
                userTouchedExpansion = true,
                showAllSteps = false,
                observedStatus = TraceRunStatus.RUNNING,
            ),
            nextStatus = TraceRunStatus.RUNNING,
        )

        assertTrue(autoState.expanded)
        assertFalse(userCollapsedState.expanded)
    }

    @Test
    fun succeededAndCanceledTerminalAutoCollapseWhenUserHasNotTouchedExpansion() {
        val running = TraceExpansionSnapshot(
            expanded = true,
            userTouchedExpansion = false,
            showAllSteps = true,
            observedStatus = TraceRunStatus.RUNNING,
        )

        val succeeded = traceExpansionAfterStatusChange(running, TraceRunStatus.SUCCEEDED)
        val canceled = traceExpansionAfterStatusChange(running, TraceRunStatus.CANCELLED)

        assertFalse(succeeded.expanded)
        assertFalse(succeeded.showAllSteps)
        assertFalse(canceled.expanded)
        assertFalse(canceled.showAllSteps)
    }

    @Test
    fun failedInterruptedAndWaitingStayExpandedAfterTerminalWhenUserHasNotTouchedExpansion() {
        val running = TraceExpansionSnapshot(
            expanded = true,
            userTouchedExpansion = false,
            showAllSteps = false,
            observedStatus = TraceRunStatus.RUNNING,
        )

        assertTrue(traceExpansionAfterStatusChange(running, TraceRunStatus.FAILED).expanded)
        assertTrue(traceExpansionAfterStatusChange(running, TraceRunStatus.INTERRUPTED).expanded)
        assertTrue(traceExpansionAfterStatusChange(running, TraceRunStatus.WAITING_FOR_USER).expanded)
    }

    @Test
    fun userTouchedExpansionIsNotOverriddenByTerminalOrNewSteps() {
        val userExpanded = TraceExpansionSnapshot(
            expanded = true,
            userTouchedExpansion = true,
            showAllSteps = false,
            observedStatus = TraceRunStatus.RUNNING,
        )
        val userCollapsed = TraceExpansionSnapshot(
            expanded = false,
            userTouchedExpansion = true,
            showAllSteps = false,
            observedStatus = TraceRunStatus.RUNNING,
        )

        assertTrue(traceExpansionAfterStatusChange(userExpanded, TraceRunStatus.SUCCEEDED).expanded)
        assertFalse(traceExpansionAfterStatusChange(userCollapsed, TraceRunStatus.RUNNING).expanded)
    }

    @Test
    fun historyTraceDefaultsCollapsed() {
        assertFalse(initialTraceExpansionSnapshot(TraceRunStatus.SUCCEEDED, isLiveMessage = false).expanded)
        assertFalse(initialTraceExpansionSnapshot(TraceRunStatus.FAILED, isLiveMessage = false).expanded)
        assertFalse(initialTraceExpansionSnapshot(TraceRunStatus.INTERRUPTED, isLiveMessage = false).expanded)
        assertFalse(initialTraceExpansionSnapshot(TraceRunStatus.WAITING_FOR_USER, isLiveMessage = false).expanded)
    }

    @Test
    fun headerToggleMarksUserTouchedAndCollapseClearsShowAllSteps() {
        val expanded = TraceExpansionSnapshot(
            expanded = true,
            userTouchedExpansion = false,
            showAllSteps = true,
            observedStatus = TraceRunStatus.RUNNING,
        )

        val collapsed = traceExpansionAfterHeaderToggle(expanded)

        assertFalse(collapsed.expanded)
        assertTrue(collapsed.userTouchedExpansion)
        assertFalse(collapsed.showAllSteps)
    }

    @Test
    fun showMoreToggleDoesNotChangeExpansionOrUserTouchedState() {
        val state = TraceExpansionSnapshot(
            expanded = true,
            userTouchedExpansion = true,
            showAllSteps = false,
            observedStatus = TraceRunStatus.RUNNING,
        )

        val showMore = traceExpansionAfterShowAllToggle(state)

        assertTrue(showMore.expanded)
        assertTrue(showMore.userTouchedExpansion)
        assertTrue(showMore.showAllSteps)
        assertTrue(traceExpansionAfterStatusChange(showMore, TraceRunStatus.SUCCEEDED).expanded)
    }

    @Test
    fun failedInterruptedAndWaitingDisplayStates_expandByDefault() {
        val failed = traceDisplayState(
            com.example.blueheartv.model.AssistantTrace(
                runId = "run-failed",
                runStatus = TraceRunStatus.FAILED,
                hasTerminal = true,
            ),
        )
        val interrupted = traceDisplayState(
            com.example.blueheartv.model.AssistantTrace(
                runId = "run-interrupted",
                runStatus = TraceRunStatus.INTERRUPTED,
            ),
        )
        val waiting = traceDisplayState(
            com.example.blueheartv.model.AssistantTrace(
                runId = "run-waiting",
                runStatus = TraceRunStatus.WAITING_FOR_USER,
            ),
        )

        assertEquals("执行失败", failed.title)
        assertTrue(failed.defaultExpanded)
        assertEquals("连接中断", interrupted.title)
        assertTrue(interrupted.defaultExpanded)
        assertEquals("等待确认", waiting.title)
        assertTrue(waiting.defaultExpanded)
    }

    @Test
    fun activeFailureAndWaitingSteps_areExpandedByDefault() {
        assertTrue(defaultTraceStepExpanded(TraceStepStatus.RUNNING))
        assertTrue(defaultTraceStepExpanded(TraceStepStatus.FAILED))
        assertTrue(defaultTraceStepExpanded(TraceStepStatus.WAITING_FOR_USER))
    }

    @Test
    fun summaryPreview_isTwoLinesUntilUserExpandsIt() {
        assertEquals(2, traceSummaryMaxLines(expanded = false))
        assertEquals(6, traceSummaryMaxLines(expanded = true))
    }

    @Test
    fun detailPreview_isOneLineUntilUserExpandsIt() {
        assertEquals(1, traceDetailMaxLines(expanded = false))
        assertEquals(5, traceDetailMaxLines(expanded = true))
    }

    @Test
    fun runningLongTrace_previewsRecentFiveStepsUntilShowMore() {
        val steps = (1..8).map { index ->
            traceStep(id = "step-$index", title = "步骤 $index")
        }

        val preview = visibleTraceStepLayouts(
            steps = steps,
            runStatus = TraceRunStatus.RUNNING,
            expanded = true,
            showAll = false,
        )
        val all = visibleTraceStepLayouts(
            steps = steps,
            runStatus = TraceRunStatus.RUNNING,
            expanded = true,
            showAll = true,
        )

        assertEquals(listOf("step-4", "step-5", "step-6", "step-7", "step-8"), preview.map { it.step.id })
        assertEquals(8, all.size)
        assertTrue(traceHasMoreSteps(steps, preview))
    }

    @Test
    fun collapsedTraceShowsNoStepRows() {
        val steps = listOf(traceStep(id = "step-1", title = "打开应用"))

        val layouts = visibleTraceStepLayouts(
            steps = steps,
            runStatus = TraceRunStatus.SUCCEEDED,
            expanded = false,
            showAll = false,
        )

        assertTrue(layouts.isEmpty())
    }

    @Test
    fun visibleTraceDetails_filtersHiddenAndBlankDetails() {
        val step = TraceStep(
            id = "step-1",
            kind = "tool",
            title = "观察屏幕",
            summary = "正在读取当前手机屏幕状态。",
            status = TraceStepStatus.RUNNING,
            details = listOf(
                TraceDetail(
                    id = "detail-1",
                    kind = TraceDetailKind.WARNING,
                    title = "需要确认",
                    text = "该操作需要你确认。我不会继续自动执行。",
                ),
                TraceDetail(
                    id = "detail-2",
                    kind = TraceDetailKind.ERROR,
                    title = "错误",
                    text = "",
                ),
                TraceDetail(
                    id = "detail-3",
                    kind = TraceDetailKind.TOOL_RESULT,
                    title = "工具结果",
                    text = "不显示",
                    visibleToUser = false,
                ),
            ),
        )

        val details = visibleTraceDetails(step)

        assertEquals(1, details.size)
        assertEquals("需要确认", details.single().title)
        assertTrue(details.single().text.contains("不会继续自动执行"))
    }

    @Test
    fun visibleTraceStepLayouts_placesChildStepsAtOneLevelIndent() {
        val parent = traceStep(id = "parent", title = "执行手机操作")
        val child = traceStep(id = "child", parentId = "parent", title = "点击屏幕")
        val grandChild = traceStep(id = "grand-child", parentId = "child", title = "判断结果")

        val layouts = visibleTraceStepLayouts(listOf(parent, child, grandChild))

        assertEquals(listOf("parent", "child", "grand-child"), layouts.map { it.step.id })
        assertEquals(listOf(0, 1, 1), layouts.map { it.depth })
        assertEquals(16, traceStepStartPaddingDp(layouts[1].depth))
    }

    @Test
    fun visibleTraceStepLayouts_keepsOrphanChildVisibleAsTopLevel() {
        val child = traceStep(id = "child", parentId = "missing-parent", title = "点击屏幕")

        val layouts = visibleTraceStepLayouts(listOf(child))

        assertEquals(1, layouts.size)
        assertEquals("child", layouts.single().step.id)
        assertEquals(0, layouts.single().depth)
    }

    @Test
    fun waitingForUserGuidance_makesManualResumeExplicit() {
        assertEquals(
            "涉及发送、支付、授权或登录，需要你确认后继续。",
            waitingForUserGuidanceText(),
        )
    }

    @Test
    fun waitingForUserStep_takesHeaderPriorityOverRunning() {
        val trace = com.example.blueheartv.model.AssistantTrace(
            runId = "run-1",
            runStatus = TraceRunStatus.RUNNING,
            steps = listOf(
                traceStep(id = "running", title = "查询天气"),
                traceStep(
                    id = "approval",
                    title = "等待你确认",
                    parentId = null,
                ).copy(status = TraceStepStatus.WAITING_FOR_USER),
            ),
        )

        val status = effectiveTraceRunStatus(trace)

        assertEquals(TraceRunStatus.WAITING_FOR_USER, status)
        assertEquals("需要你确认", traceHeaderTitle(status))
        assertEquals("等待确认", traceHeaderStatusText(status))
    }

    @Test
    fun failedStep_takesHeaderPriorityOverWaiting() {
        val trace = com.example.blueheartv.model.AssistantTrace(
            runId = "run-1",
            runStatus = TraceRunStatus.WAITING_FOR_USER,
            steps = listOf(
                traceStep(id = "approval", title = "等待你确认").copy(status = TraceStepStatus.WAITING_FOR_USER),
                traceStep(id = "failed", title = "查询天气").copy(status = TraceStepStatus.FAILED),
            ),
        )

        assertEquals(TraceRunStatus.FAILED, effectiveTraceRunStatus(trace))
    }

    @Test
    fun copyMapper_mapsTechnicalToolsToUserFacingChinese() {
        val step = traceStep(
            id = "weather",
            title = "weather_query",
            parentId = null,
        ).copy(kind = "life_service", summary = "")

        assertEquals("查询天气", userFacingTraceTitle(step))
        assertEquals("正在执行", userFacingTraceSummary(step))
    }

    @Test
    fun internalDetailData_isHidden() {
        val detail = TraceDetail(
            id = "detail-raw",
            kind = TraceDetailKind.TOOL_RESULT,
            title = "Raw",
            text = """{"tool_calls":[{"kwargs":{"api_key":"secret"}}]}""",
        )

        assertEquals(INTERNAL_TRACE_DATA_MESSAGE, userFacingDetailText(detail))
    }

    @Test
    fun internalEventNamesAndSensitiveFields_areHidden() {
        val detail = TraceDetail(
            id = "detail-raw",
            kind = TraceDetailKind.ERROR,
            title = "trace.v1",
            text = "assistant.delta stream.eof Authorization: Bearer token x-api-key=abc cookie=secret Traceback screenshot base64 UI tree",
        )
        val step = traceStep(id = "raw-step", title = "trace.v1")
            .copy(summary = "stream.started raw event with x-api-key and screenshot base64")

        assertEquals("详情", userFacingDetailTitle(detail))
        assertEquals(INTERNAL_TRACE_DATA_MESSAGE, userFacingDetailText(detail))
        assertEquals(INTERNAL_TRACE_DATA_MESSAGE, userFacingTraceSummary(step))
    }

    @Test
    fun longSummary_isBoundedForCompactMobileLayout() {
        val summary = "天气".repeat(180)
        val step = traceStep(id = "long", title = "查询天气").copy(summary = summary)

        assertTrue(userFacingTraceSummary(step).length <= 240)
    }

    private fun traceStep(
        id: String,
        title: String,
        parentId: String? = null,
    ): TraceStep {
        return TraceStep(
            id = id,
            parentId = parentId,
            kind = "phone_action",
            title = title,
            summary = "执行过程。",
            status = TraceStepStatus.RUNNING,
        )
    }
}
