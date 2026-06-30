package com.example.blueheartv.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.model.AssistantTrace
import com.example.blueheartv.model.TraceDetail
import com.example.blueheartv.model.TraceDetailKind
import com.example.blueheartv.model.TraceRunStatus
import com.example.blueheartv.model.TraceStep
import com.example.blueheartv.model.TraceStepStatus

@Composable
fun TraceTimeline(
    trace: AssistantTrace,
    modifier: Modifier = Modifier,
) {
    AgentTraceCard(trace = trace, modifier = modifier)
}

@Composable
fun AgentTraceCard(
    trace: AssistantTrace,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val colorScheme = MaterialTheme.colorScheme
    val layouts = visibleTraceStepLayouts(trace.steps)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colorScheme.surfaceContainerLowest.copy(alpha = 0.92f), shape)
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TraceHeader(trace = trace)
        if (layouts.isEmpty()) {
            val summary = trace.summary?.let(::compactTraceSummary)
                ?: "已接收请求，正在连接 Agent。"
            Text(
                text = summary,
                color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (effectiveTraceRunStatus(trace) == TraceRunStatus.RUNNING) {
                StreamingThinkingIndicator()
            }
        } else {
            layouts.forEach { layout ->
                TraceStepCard(
                    step = layout.step,
                    depth = layout.depth,
                    defaultExpanded = defaultTraceStepExpanded(layout.step.status),
                )
            }
        }
    }
}

@Composable
fun TraceHeader(trace: AssistantTrace) {
    val status = effectiveTraceRunStatus(trace)
    val colorScheme = MaterialTheme.colorScheme
    val accent = traceRunAccent(status)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = traceHeaderTitle(status),
                color = colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trace.summary?.let { summary ->
                Text(
                    text = compactTraceSummary(summary),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TraceStatusLabel(status = status, accent = accent)
    }
}

@Composable
fun TraceStepCard(
    step: TraceStep,
    depth: Int,
    defaultExpanded: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val spec = traceVisualSpec(step.kind, step.status, colorScheme)
    val title = userFacingTraceTitle(step)
    val summary = userFacingTraceSummary(step)
    val details = visibleTraceDetails(step)
    val expandable = details.isNotEmpty() || summary.length > 72 || step.status in expandableTraceStatuses()
    var expanded by rememberSaveable(step.id) { mutableStateOf(defaultExpanded) }
    val shape = RoundedCornerShape(18.dp)
    val indent = traceStepStartPaddingDp(depth).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TraceKindIcon(kind = step.kind, status = step.status)
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .background(spec.container, shape)
                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.32f), shape)
                .clickable(enabled = expandable) { expanded = !expanded }
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        color = colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = spec.label,
                        color = spec.accent,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (step.status == TraceStepStatus.RUNNING) {
                    StreamingThinkingIndicator()
                }
                if (expandable) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起执行步骤" else "展开执行步骤",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = summary,
                color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = traceSummaryMaxLines(expanded),
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded && step.status == TraceStepStatus.WAITING_FOR_USER) {
                Text(
                    text = waitingForUserGuidanceText(),
                    color = spec.accent,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            if (expanded && details.isNotEmpty()) {
                TraceDetailList(details = details)
            }
        }
    }
}

@Composable
fun TraceStatusDot(status: TraceStepStatus) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(traceStepStatusAccent(status), CircleShape),
    )
}

@Composable
fun TraceKindIcon(
    kind: String,
    status: TraceStepStatus,
) {
    val spec = traceVisualSpec(kind, status, MaterialTheme.colorScheme)
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(22.dp)
            .background(spec.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (spec.icon != null) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = spec.accent,
                modifier = Modifier.size(14.dp),
            )
        } else {
            TraceStatusDot(status = status)
        }
    }
}

@Composable
fun TraceDetailList(details: List<TraceDetail>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        details.forEach { detail ->
            TraceDetailChip(detail = detail)
        }
    }
}

@Composable
fun TraceDetailChip(detail: TraceDetail) {
    val colorScheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(detail.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = traceDetailContainer(detail.kind),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = userFacingDetailTitle(detail),
                color = traceDetailAccent(detail.kind),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = userFacingDetailText(detail),
                color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = traceDetailMaxLines(expanded),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StreamingThinkingIndicator() {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "trace_streaming")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 0.72f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 680, delayMillis = index * 120),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "trace_dot_$index",
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(color.copy(alpha = alpha), CircleShape),
            )
        }
    }
}

@Composable
private fun TraceStatusLabel(status: TraceRunStatus, accent: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.10f),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = traceHeaderStatusText(status),
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun traceRunAccent(status: TraceRunStatus): Color {
    val colorScheme = MaterialTheme.colorScheme
    return when (status) {
        TraceRunStatus.RUNNING -> colorScheme.primary
        TraceRunStatus.SUCCEEDED -> colorScheme.tertiary
        TraceRunStatus.FAILED -> colorScheme.error
        TraceRunStatus.CANCELLED -> colorScheme.onSurfaceVariant
        TraceRunStatus.WAITING_FOR_USER -> Color(0xFF9A6A18)
        TraceRunStatus.INTERRUPTED -> colorScheme.error
    }
}

@Composable
private fun traceStepStatusAccent(status: TraceStepStatus): Color =
    traceVisualSpec("generic", status, MaterialTheme.colorScheme).accent

@Composable
private fun traceDetailAccent(kind: TraceDetailKind): Color {
    val colorScheme = MaterialTheme.colorScheme
    return when (kind) {
        TraceDetailKind.ERROR -> colorScheme.error
        TraceDetailKind.WARNING, TraceDetailKind.RETRY -> Color(0xFF9A6A18)
        TraceDetailKind.TOOL_CALL,
        TraceDetailKind.TOOL_ARGS_SUMMARY,
        TraceDetailKind.TOOL_RESULT -> colorScheme.primary
        TraceDetailKind.OBSERVATION,
        TraceDetailKind.REASONING_SUMMARY,
        TraceDetailKind.PLAN,
        TraceDetailKind.DECISION -> colorScheme.onSurfaceVariant
    }
}

@Composable
private fun traceDetailContainer(kind: TraceDetailKind): Color =
    traceDetailAccent(kind).copy(alpha = 0.08f)

internal fun defaultTraceStepExpanded(status: TraceStepStatus): Boolean =
    status in expandableTraceStatuses()

internal fun traceSummaryMaxLines(expanded: Boolean): Int = if (expanded) 6 else 2

internal fun traceDetailMaxLines(expanded: Boolean): Int = if (expanded) 5 else 1

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
    "涉及发送、支付、授权或登录，需要你确认后继续。"

internal fun effectiveTraceRunStatus(trace: AssistantTrace): TraceRunStatus {
    val stepStatuses = trace.steps.filter { it.visibleToUser }.map { it.status }
    return when {
        trace.runStatus == TraceRunStatus.FAILED || TraceStepStatus.FAILED in stepStatuses -> TraceRunStatus.FAILED
        trace.runStatus == TraceRunStatus.WAITING_FOR_USER ||
            TraceStepStatus.WAITING_FOR_USER in stepStatuses -> TraceRunStatus.WAITING_FOR_USER
        trace.runStatus == TraceRunStatus.CANCELLED || TraceStepStatus.CANCELLED in stepStatuses -> TraceRunStatus.CANCELLED
        trace.runStatus == TraceRunStatus.INTERRUPTED -> TraceRunStatus.INTERRUPTED
        trace.runStatus == TraceRunStatus.RUNNING || TraceStepStatus.RUNNING in stepStatuses -> TraceRunStatus.RUNNING
        else -> TraceRunStatus.SUCCEEDED
    }
}

internal fun traceHeaderTitle(status: TraceRunStatus): String = when (status) {
    TraceRunStatus.RUNNING -> "Echo 正在处理"
    TraceRunStatus.SUCCEEDED -> "执行过程"
    TraceRunStatus.FAILED -> "处理未完成"
    TraceRunStatus.CANCELLED -> "处理已取消"
    TraceRunStatus.WAITING_FOR_USER -> "需要你确认"
    TraceRunStatus.INTERRUPTED -> "连接中断，可重试"
}

internal fun traceHeaderStatusText(status: TraceRunStatus): String = when (status) {
    TraceRunStatus.RUNNING -> "运行中"
    TraceRunStatus.SUCCEEDED -> "已完成"
    TraceRunStatus.FAILED -> "未完成"
    TraceRunStatus.CANCELLED -> "已取消"
    TraceRunStatus.WAITING_FOR_USER -> "等待确认"
    TraceRunStatus.INTERRUPTED -> "连接中断"
}

private fun expandableTraceStatuses(): Set<TraceStepStatus> =
    setOf(
        TraceStepStatus.QUEUED,
        TraceStepStatus.RUNNING,
        TraceStepStatus.FAILED,
        TraceStepStatus.CANCELLED,
        TraceStepStatus.WAITING_FOR_USER,
    )
