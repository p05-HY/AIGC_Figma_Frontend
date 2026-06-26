package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.model.AssistantTrace
import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceRunStatus
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus
import com.example.blueheartv.ui.theme.BrandPrimary
import com.example.blueheartv.ui.theme.DividerColor
import com.example.blueheartv.ui.theme.ErrorRed
import com.example.blueheartv.ui.theme.MutedText
import com.example.blueheartv.ui.theme.SuccessGreen
import com.example.blueheartv.ui.theme.TextDarkAlt

@Composable
fun TraceTimeline(
    trace: AssistantTrace,
    modifier: Modifier = Modifier,
) {
    val (title, color) = traceHeader(trace.runStatus)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, color = color, fontSize = 12.sp)
        if (trace.runStatus == TraceRunStatus.WAITING_FOR_USER) {
            Text(
                text = waitingForUserGuidanceText(),
                color = MutedText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        visibleTraceStepLayouts(trace.steps).forEach { layout ->
            TraceStepRow(step = layout.step, depth = layout.depth)
        }
    }
}

@Composable
private fun TraceStepRow(step: TraceStep, depth: Int) {
    var expanded by rememberSaveable(step.id) {
        mutableStateOf(defaultTraceStepExpanded(step.status))
    }
    val color = stepColor(step.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = traceStepStartPaddingDp(depth).dp)
            .clickable { expanded = !expanded },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineRail(color = color)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step.title,
                    color = TextDarkAlt,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起执行步骤" else "展开执行步骤",
                    tint = MutedText,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(text = traceStepStatusText(step.status), color = color, fontSize = 11.sp)
            if (expanded) {
                ExpandableTraceText(text = step.summary)
                visibleTraceDetails(step).forEach { detail ->
                    TraceDetailRow(detail = detail)
                }
            }
        }
    }
}

@Composable
private fun TimelineRail(color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Box(modifier = Modifier.width(1.dp).heightIn(min = 18.dp).background(DividerColor))
    }
}

@Composable
private fun TraceDetailRow(detail: TraceDetail) {
    var expanded by rememberSaveable(detail.id) { mutableStateOf(false) }
    val color = traceDetailColor(detail.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp)
            .clickable { expanded = !expanded },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .background(color, CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = detail.title,
                color = color,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail.text,
                color = MutedText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = traceDetailMaxLines(expanded),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExpandableTraceText(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Text(
        text = text,
        color = MutedText,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        maxLines = traceSummaryMaxLines(expanded),
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable { expanded = !expanded },
    )
}

internal fun defaultTraceStepExpanded(status: TraceStepStatus): Boolean =
    status in setOf(
        TraceStepStatus.QUEUED,
        TraceStepStatus.RUNNING,
        TraceStepStatus.FAILED,
        TraceStepStatus.CANCELLED,
        TraceStepStatus.WAITING_FOR_USER,
    )

internal fun traceSummaryMaxLines(expanded: Boolean): Int = if (expanded) Int.MAX_VALUE else 4

internal fun traceDetailMaxLines(expanded: Boolean): Int = if (expanded) Int.MAX_VALUE else 4

internal data class TraceStepLayout(
    val step: TraceStep,
    val depth: Int,
)

internal fun visibleTraceStepLayouts(steps: List<TraceStep>): List<TraceStepLayout> {
    val visibleSteps = steps.filter { it.visibleToUser }
    val visibleIds = visibleSteps.map { it.id }.toSet()
    return visibleSteps.map { step ->
        TraceStepLayout(
            step = step,
            depth = if (step.parentId in visibleIds) 1 else 0,
        )
    }
}

internal fun traceStepStartPaddingDp(depth: Int): Int =
    depth.coerceIn(0, 1) * 16

internal fun visibleTraceDetails(step: TraceStep): List<TraceDetail> =
    step.details.filter { detail ->
        detail.visibleToUser && detail.title.isNotBlank() && detail.text.isNotBlank()
    }

internal fun waitingForUserGuidanceText(): String =
    "该操作需要你确认。我不会继续自动执行。请发送新消息说明继续、取消或换一种方式。"

private fun traceHeader(status: TraceRunStatus): Pair<String, Color> = when (status) {
    TraceRunStatus.RUNNING -> "正在处理请求" to BrandPrimary
    TraceRunStatus.SUCCEEDED -> "执行过程" to SuccessGreen
    TraceRunStatus.FAILED -> "处理未能完成" to ErrorRed
    TraceRunStatus.CANCELLED -> "处理已取消" to MutedText
    TraceRunStatus.WAITING_FOR_USER -> "需要你确认" to BrandPrimary
    TraceRunStatus.INTERRUPTED -> "连接中断，可重试" to ErrorRed
}

private fun traceStepStatusText(status: TraceStepStatus): String = when (status) {
    TraceStepStatus.QUEUED -> "等待执行"
    TraceStepStatus.RUNNING -> "执行中"
    TraceStepStatus.SUCCEEDED -> "已完成"
    TraceStepStatus.FAILED -> "未完成"
    TraceStepStatus.CANCELLED -> "已取消"
    TraceStepStatus.WAITING_FOR_USER -> "等待你处理"
}

private fun stepColor(status: TraceStepStatus): Color = when (status) {
    TraceStepStatus.QUEUED -> MutedText
    TraceStepStatus.RUNNING, TraceStepStatus.WAITING_FOR_USER -> BrandPrimary
    TraceStepStatus.SUCCEEDED -> SuccessGreen
    TraceStepStatus.FAILED -> ErrorRed
    TraceStepStatus.CANCELLED -> MutedText
}

private fun traceDetailColor(kind: TraceDetailKind): Color = when (kind) {
    TraceDetailKind.ERROR -> ErrorRed
    TraceDetailKind.WARNING, TraceDetailKind.RETRY -> BrandPrimary
    TraceDetailKind.TOOL_CALL,
    TraceDetailKind.TOOL_ARGS_SUMMARY,
    TraceDetailKind.TOOL_RESULT,
    TraceDetailKind.OBSERVATION,
    TraceDetailKind.REASONING_SUMMARY,
    TraceDetailKind.PLAN,
    TraceDetailKind.DECISION -> MutedText
}
