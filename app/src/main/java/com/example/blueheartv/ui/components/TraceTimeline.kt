package com.example.blueheartv.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
    isLiveMessage: Boolean = false,
    displayContext: String? = null,
) {
    AgentTraceCard(
        trace = trace,
        modifier = modifier,
        isLiveMessage = isLiveMessage,
        displayContext = displayContext,
    )
}

@Composable
fun AgentTraceCard(
    trace: AssistantTrace,
    modifier: Modifier = Modifier,
    isLiveMessage: Boolean = false,
    displayContext: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val combinedDisplayContext = traceDisplayContext(trace, displayContext)
    val displayState = traceDisplayState(trace, combinedDisplayContext)
    val totalLayouts = visibleTraceStepLayouts(trace.steps)
    val initialExpansion = initialTraceExpansionSnapshot(
        status = displayState.status,
        isLiveMessage = isLiveMessage,
    )
    var expanded by rememberSaveable(trace.runId) { mutableStateOf(initialExpansion.expanded) }
    var userTouchedExpansion by rememberSaveable("${trace.runId}:touched") {
        mutableStateOf(initialExpansion.userTouchedExpansion)
    }
    var showAllSteps by rememberSaveable("${trace.runId}:steps") {
        mutableStateOf(initialExpansion.showAllSteps)
    }
    var observedStatusName by rememberSaveable("${trace.runId}:status") {
        mutableStateOf(initialExpansion.observedStatus.name)
    }
    LaunchedEffect(trace.runId, displayState.status, isLiveMessage, userTouchedExpansion) {
        val next = traceExpansionAfterStatusChange(
            current = TraceExpansionSnapshot(
                expanded = expanded,
                userTouchedExpansion = userTouchedExpansion,
                showAllSteps = showAllSteps,
                observedStatus = traceRunStatusFromName(observedStatusName) ?: displayState.status,
            ),
            nextStatus = displayState.status,
        )
        expanded = next.expanded
        userTouchedExpansion = next.userTouchedExpansion
        showAllSteps = next.showAllSteps
        observedStatusName = next.observedStatus.name
    }
    val layouts = visibleTraceStepLayouts(
        steps = trace.steps,
        runStatus = displayState.status,
        expanded = expanded,
        showAll = showAllSteps,
    )
    val displayModelsById = traceStepDisplayModels(
        steps = totalLayouts.map { it.step },
        traceSummary = trace.summary,
        displayContext = combinedDisplayContext,
    ).associateBy { it.stepId }
    val canExpand = totalLayouts.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TraceHeader(
            trace = trace,
            displayState = displayState,
            expanded = expanded,
            canExpand = canExpand,
            onToggleExpanded = {
                if (canExpand) {
                    val next = traceExpansionAfterHeaderToggle(
                        TraceExpansionSnapshot(
                            expanded = expanded,
                            userTouchedExpansion = userTouchedExpansion,
                            showAllSteps = showAllSteps,
                            observedStatus = displayState.status,
                        ),
                    )
                    expanded = next.expanded
                    userTouchedExpansion = next.userTouchedExpansion
                    showAllSteps = next.showAllSteps
                    observedStatusName = next.observedStatus.name
                }
            },
        )
        if (layouts.isEmpty()) {
            if (expanded) {
                TraceCompactStatusLine(displayState = displayState)
            }
            if (displayState.status == TraceRunStatus.RUNNING && expanded) {
                StreamingThinkingIndicator()
            }
        } else {
            layouts.forEachIndexed { index, layout ->
                key(layout.step.id) {
                    TraceStepTimelineItem(
                        step = layout.step,
                        displayModel = displayModelsById[layout.step.id]
                            ?: traceStepDisplayModel(layout.step, trace.summary, combinedDisplayContext),
                        depth = layout.depth,
                        defaultExpanded = defaultTraceStepExpanded(layout.step.status),
                        isLast = index == layouts.lastIndex,
                    )
                }
            }
            if (totalLayouts.size > layouts.size || showAllSteps) {
                Text(
                    text = if (showAllSteps) "收起步骤" else "查看更多步骤",
                    color = colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(start = 30.dp)
                        .clickable {
                            val next = traceExpansionAfterShowAllToggle(
                                TraceExpansionSnapshot(
                                    expanded = expanded,
                                    userTouchedExpansion = userTouchedExpansion,
                                    showAllSteps = showAllSteps,
                                    observedStatus = displayState.status,
                                ),
                            )
                            expanded = next.expanded
                            userTouchedExpansion = next.userTouchedExpansion
                            showAllSteps = next.showAllSteps
                            observedStatusName = next.observedStatus.name
                        },
                )
            }
        }
    }
}

@Composable
internal fun TraceHeader(
    trace: AssistantTrace,
    displayState: TraceDisplayState = traceDisplayState(trace),
    expanded: Boolean = true,
    canExpand: Boolean = false,
    onToggleExpanded: () -> Unit = {},
) {
    val status = displayState.status
    val colorScheme = MaterialTheme.colorScheme
    val accent = traceRunAccent(status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canExpand, onClick = onToggleExpanded),
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
                text = displayState.title,
                color = colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (displayState.subtitle.isNotBlank()) {
                Text(
                    text = displayState.subtitle,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (canExpand) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起执行过程" else "展开执行过程",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun TraceStepTimelineItem(
    step: TraceStep,
    displayModel: TraceStepDisplayModel = traceStepDisplayModel(step),
    depth: Int,
    defaultExpanded: Boolean,
    isLast: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val title = displayModel.title
    val summary = displayModel.summary
    val details = displayModel.detailLines
    val expandable = details.isNotEmpty() || summary.length > 72 || step.status in expandableTraceStatuses()
    var expanded by rememberSaveable(step.id) { mutableStateOf(defaultExpanded) }
    val indent = traceStepStartPaddingDp(depth).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TraceTimelineMarker(kind = step.kind, status = step.status, isLast = isLast)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = expandable) { expanded = !expanded }
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
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
                    color = traceStepStatusAccent(step.status),
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
private fun TraceTimelineMarker(
    kind: String,
    status: TraceStepStatus,
    isLast: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        TraceKindIcon(kind = kind, status = status)
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )
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
fun TraceDetailList(details: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        details.forEach { detail ->
            TraceDetailLine(detail = detail)
        }
    }
}

@Composable
fun TraceDetailLine(detail: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(3.dp)
                .background(colorScheme.outlineVariant, CircleShape),
        )
        Text(
            text = detail,
            color = colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
private fun ExpandableTraceText(text: String, stateKey: String) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        maxLines = traceSummaryMaxLines(expanded),
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable { expanded = !expanded },
    )
}

@Composable
private fun TraceStatusLabel(status: TraceRunStatus, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.10f)),
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

private fun traceStepContainer(status: TraceStepStatus, activeContainer: Color): Color =
    when (status) {
        TraceStepStatus.RUNNING,
        TraceStepStatus.FAILED,
        TraceStepStatus.WAITING_FOR_USER -> activeContainer
        else -> Color.Transparent
    }

internal fun defaultTraceStepExpanded(status: TraceStepStatus): Boolean =
    false

internal fun traceSummaryMaxLines(expanded: Boolean): Int = if (expanded) 6 else 2

internal fun traceDetailMaxLines(expanded: Boolean): Int = if (expanded) 5 else 1

internal data class TraceStepLayout(
    val step: TraceStep,
    val depth: Int,
)

internal enum class TraceSeverity {
    ACTIVE,
    SUCCESS,
    ERROR,
    WARNING,
    NEUTRAL,
}

internal data class TraceDisplayState(
    val status: TraceRunStatus,
    val title: String,
    val subtitle: String,
    val defaultExpanded: Boolean,
    val severity: TraceSeverity,
    val isTerminal: Boolean,
)

internal data class TraceExpansionSnapshot(
    val expanded: Boolean,
    val userTouchedExpansion: Boolean,
    val showAllSteps: Boolean,
    val observedStatus: TraceRunStatus,
)

internal fun initialTraceExpansionSnapshot(
    status: TraceRunStatus,
    isLiveMessage: Boolean,
): TraceExpansionSnapshot =
    TraceExpansionSnapshot(
        expanded = when {
            status == TraceRunStatus.RUNNING -> true
            isLiveMessage && status in setOf(
                TraceRunStatus.FAILED,
                TraceRunStatus.INTERRUPTED,
                TraceRunStatus.WAITING_FOR_USER,
            ) -> true
            else -> false
        },
        userTouchedExpansion = false,
        showAllSteps = false,
        observedStatus = status,
    )

internal fun traceExpansionAfterStatusChange(
    current: TraceExpansionSnapshot,
    nextStatus: TraceRunStatus,
): TraceExpansionSnapshot {
    if (current.observedStatus == nextStatus) {
        if (nextStatus == TraceRunStatus.RUNNING && !current.userTouchedExpansion) {
            return current.copy(
                expanded = true,
                observedStatus = nextStatus,
            )
        }
        return current.copy(observedStatus = nextStatus)
    }
    if (current.userTouchedExpansion) {
        return current.copy(observedStatus = nextStatus)
    }
    val nextExpanded = when (nextStatus) {
        TraceRunStatus.RUNNING -> true
        TraceRunStatus.SUCCEEDED,
        TraceRunStatus.CANCELLED -> false
        TraceRunStatus.FAILED,
        TraceRunStatus.INTERRUPTED,
        TraceRunStatus.WAITING_FOR_USER -> true
    }
    return current.copy(
        expanded = nextExpanded,
        showAllSteps = if (nextExpanded) current.showAllSteps else false,
        observedStatus = nextStatus,
    )
}

internal fun traceExpansionAfterHeaderToggle(current: TraceExpansionSnapshot): TraceExpansionSnapshot {
    val nextExpanded = !current.expanded
    return current.copy(
        expanded = nextExpanded,
        userTouchedExpansion = true,
        showAllSteps = if (nextExpanded) current.showAllSteps else false,
    )
}

internal fun traceExpansionAfterShowAllToggle(current: TraceExpansionSnapshot): TraceExpansionSnapshot =
    current.copy(showAllSteps = !current.showAllSteps)

private fun traceRunStatusFromName(name: String): TraceRunStatus? =
    TraceRunStatus.entries.firstOrNull { it.name == name }

internal fun traceDisplayState(trace: AssistantTrace, displayContext: String? = null): TraceDisplayState {
    val status = effectiveTraceRunStatus(trace)
    val stepCount = trace.steps.count { it.visibleToUser }
    val statusDetail = when (status) {
        TraceRunStatus.RUNNING -> traceHeaderActivityText(trace.steps, traceDisplayContext(trace, displayContext))
        TraceRunStatus.SUCCEEDED -> "$stepCount 个步骤"
        TraceRunStatus.FAILED -> trace.summary
            ?.let(::compactTraceSummary)
            ?.takeIf { it.isNotBlank() && it != INTERNAL_TRACE_DATA_MESSAGE }
            ?: nonUserCancellationDetail(trace)
            ?: "某一步未能完成"
        TraceRunStatus.CANCELLED -> "任务已由你停止"
        TraceRunStatus.WAITING_FOR_USER -> "需要你确认后继续"
        TraceRunStatus.INTERRUPTED -> "当前任务未确认完成"
    }
    return TraceDisplayState(
        status = status,
        title = "${traceDisplayTitle(status)} · $statusDetail",
        subtitle = "",
        defaultExpanded = status in setOf(
            TraceRunStatus.RUNNING,
            TraceRunStatus.FAILED,
            TraceRunStatus.INTERRUPTED,
            TraceRunStatus.WAITING_FOR_USER,
        ),
        severity = when (status) {
            TraceRunStatus.RUNNING -> TraceSeverity.ACTIVE
            TraceRunStatus.SUCCEEDED -> TraceSeverity.SUCCESS
            TraceRunStatus.FAILED,
            TraceRunStatus.INTERRUPTED -> TraceSeverity.ERROR
            TraceRunStatus.WAITING_FOR_USER -> TraceSeverity.WARNING
            TraceRunStatus.CANCELLED -> TraceSeverity.NEUTRAL
        },
        isTerminal = trace.hasTerminal || status in setOf(
            TraceRunStatus.SUCCEEDED,
            TraceRunStatus.FAILED,
            TraceRunStatus.CANCELLED,
            TraceRunStatus.WAITING_FOR_USER,
        ),
    )
}

internal fun traceDisplayContext(trace: AssistantTrace, displayContext: String? = null): String? =
    listOfNotNull(trace.displayContext, displayContext)
        .joinToString(" ")
        .trim()
        .takeIf { it.isNotBlank() }

private fun traceDisplayTitle(status: TraceRunStatus): String = when (status) {
    TraceRunStatus.RUNNING -> "正在处理"
    TraceRunStatus.SUCCEEDED -> "已完成"
    TraceRunStatus.FAILED -> "执行失败"
    TraceRunStatus.CANCELLED -> "已停止"
    TraceRunStatus.WAITING_FOR_USER -> "等待确认"
    TraceRunStatus.INTERRUPTED -> "连接中断"
}

internal fun visibleTraceStepLayouts(steps: List<TraceStep>): List<TraceStepLayout> =
    traceStepLayouts(steps)

internal fun visibleTraceStepLayouts(
    steps: List<TraceStep>,
    runStatus: TraceRunStatus,
    expanded: Boolean,
    showAll: Boolean,
): List<TraceStepLayout> {
    if (!expanded) return emptyList()
    val layouts = traceStepLayouts(steps)
    if (showAll || layouts.size <= TRACE_PREVIEW_STEP_COUNT) return layouts
    return when (runStatus) {
        TraceRunStatus.RUNNING,
        TraceRunStatus.SUCCEEDED,
        TraceRunStatus.FAILED,
        TraceRunStatus.CANCELLED,
        TraceRunStatus.WAITING_FOR_USER,
        TraceRunStatus.INTERRUPTED -> layouts.takeLast(TRACE_PREVIEW_STEP_COUNT)
    }
}

internal fun traceHasMoreSteps(
    steps: List<TraceStep>,
    visibleLayouts: List<TraceStepLayout>,
): Boolean =
    traceStepLayouts(steps).size > visibleLayouts.size

private const val TRACE_PREVIEW_STEP_COUNT = 5

private fun traceStepLayouts(steps: List<TraceStep>): List<TraceStepLayout> {
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

@Composable
private fun TraceCompactStatusLine(displayState: TraceDisplayState) {
    Text(
        text = displayState.subtitle.ifBlank { displayState.title },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 33.dp),
    )
}

internal fun effectiveTraceRunStatus(trace: AssistantTrace): TraceRunStatus {
    val stepStatuses = trace.steps.filter { it.visibleToUser }.map { it.status }
    return when {
        trace.runStatus == TraceRunStatus.FAILED || TraceStepStatus.FAILED in stepStatuses -> TraceRunStatus.FAILED
        trace.runStatus == TraceRunStatus.WAITING_FOR_USER ||
            TraceStepStatus.WAITING_FOR_USER in stepStatuses -> TraceRunStatus.WAITING_FOR_USER
        trace.runStatus == TraceRunStatus.CANCELLED -> {
            if (trace.isUserCancelled()) TraceRunStatus.CANCELLED else nonUserCancelledStatus(trace)
        }
        TraceStepStatus.CANCELLED in stepStatuses -> TraceRunStatus.CANCELLED
        trace.runStatus == TraceRunStatus.INTERRUPTED -> TraceRunStatus.INTERRUPTED
        trace.runStatus == TraceRunStatus.RUNNING || TraceStepStatus.RUNNING in stepStatuses -> TraceRunStatus.RUNNING
        else -> TraceRunStatus.SUCCEEDED
    }
}

private fun AssistantTrace.isUserCancelled(): Boolean {
    val source = cancelSource ?: terminalReason
    return source == null || source in setOf("user", "user_stop")
}

private fun nonUserCancelledStatus(trace: AssistantTrace): TraceRunStatus =
    when (trace.cancelSource ?: trace.terminalReason) {
        "client_disconnected", "session_deleted", "cleanup" -> TraceRunStatus.INTERRUPTED
        else -> TraceRunStatus.FAILED
    }

private fun nonUserCancellationDetail(trace: AssistantTrace): String? =
    if (trace.runStatus != TraceRunStatus.CANCELLED || trace.isUserCancelled()) {
        null
    } else {
        when (trace.cancelSource ?: trace.terminalReason) {
            "client_disconnected", "session_deleted", "cleanup" -> "连接中断，任务未确认完成"
            "stream_error", "provider_exception" -> "服务异常，任务未确认完成"
            "run_rejected", "thread_busy" -> "当前会话仍有任务在执行"
            else -> "任务未确认完成"
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
    TraceRunStatus.CANCELLED -> "已停止"
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
