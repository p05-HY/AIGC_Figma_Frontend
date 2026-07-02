package com.example.blueheartv.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.theme.DarkPrimary
import com.example.blueheartv.ui.theme.DividerColor
import com.example.blueheartv.ui.theme.MutedText
import com.example.blueheartv.ui.theme.TextBlack
import com.example.blueheartv.viewmodel.TaskProgressState

internal enum class FloatingTaskAction {
    CONFIRM,
    REJECT,
    CANCEL_TASK,
    TAKE_OVER,
}

internal fun floatingTaskActions(task: TaskProgressState): List<FloatingTaskAction> {
    if (task.isTerminal) return emptyList()
    val waitingConfirmation = task.isWaitingConfirmation()
    return buildList {
        if (waitingConfirmation) {
            add(FloatingTaskAction.CONFIRM)
            add(FloatingTaskAction.REJECT)
        }
        if (task.canCancel) add(FloatingTaskAction.CANCEL_TASK)
        if (task.canTakeOver) add(FloatingTaskAction.TAKE_OVER)
    }
}

@Composable
internal fun FloatingTaskCard(
    task: TaskProgressState,
    metrics: FloatingChatMetrics,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onTakeOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusLabel = task.status.toDisplayStatus()
    val waitingConfirmation = task.isWaitingConfirmation()
    val stepLabel = task.stepCounterLabel()
    val actions = floatingTaskActions(task)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = metrics.listHorizontalPadding,
                vertical = 8.dp,
            )
            .clip(RoundedCornerShape(8.dp))
            .background(FloatingDesignTokens.GlassFillColor)
            .border(0.5.dp, DividerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = task.taskTitle,
                color = TextBlack,
                fontSize = metrics.bodyFontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusLabel,
                color = task.status.statusColor(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = listOfNotNull(stepLabel, task.stepTitle.takeIf { it.isNotBlank() })
                .joinToString("  "),
            color = TextBlack,
            fontSize = metrics.inputFontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        task.toolName?.takeIf { it.isNotBlank() }?.let { toolName ->
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "调用工具：$toolName",
                color = MutedText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        task.message?.takeIf { waitingConfirmation && it.isNotBlank() }?.let { message ->
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = message,
                color = TextBlack,
                fontSize = metrics.inputFontSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (actions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(7.dp))
            if (waitingConfirmation) {
                FloatingTaskActionRow(
                    actions = actions.filter {
                        it == FloatingTaskAction.CONFIRM || it == FloatingTaskAction.REJECT
                    },
                    onConfirm = onConfirm,
                    onReject = onReject,
                    onCancel = onCancel,
                    onTakeOver = onTakeOver,
                )
                val secondaryActions = actions.filter {
                    it == FloatingTaskAction.CANCEL_TASK || it == FloatingTaskAction.TAKE_OVER
                }
                if (secondaryActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FloatingTaskActionRow(
                        actions = secondaryActions,
                        onConfirm = onConfirm,
                        onReject = onReject,
                        onCancel = onCancel,
                        onTakeOver = onTakeOver,
                    )
                }
            } else {
                FloatingTaskActionRow(
                    actions = actions,
                    onConfirm = onConfirm,
                    onReject = onReject,
                    onCancel = onCancel,
                    onTakeOver = onTakeOver,
                )
            }
        }
    }
}

@Composable
private fun FloatingTaskActionRow(
    actions: List<FloatingTaskAction>,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onTakeOver: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            when (action) {
                FloatingTaskAction.CONFIRM -> {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                    ) {
                        Text("确认", fontSize = 12.sp)
                    }
                }

                FloatingTaskAction.REJECT -> {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedText),
                    ) {
                        Text("拒绝", fontSize = 12.sp)
                    }
                }

                FloatingTaskAction.CANCEL_TASK -> {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("取消任务", fontSize = 12.sp)
                    }
                }

                FloatingTaskAction.TAKE_OVER -> {
                    TextButton(
                        onClick = onTakeOver,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("接管", fontSize = 12.sp, color = DarkPrimary)
                    }
                }
            }
        }
    }
}

private fun TaskProgressState.isWaitingConfirmation(): Boolean =
    status == "waiting_confirmation" &&
        requiresConfirmation &&
        !confirmationId.isNullOrBlank()

private fun TaskProgressState.stepCounterLabel(): String? {
    val current = currentStep
    val total = totalSteps
    return if (current != null && total != null && total > 0) {
        "第 $current/$total 步"
    } else {
        null
    }
}

private fun String.toDisplayStatus(): String = when (this) {
    "pending" -> "待执行"
    "running", "started" -> "执行中"
    "waiting_confirmation" -> "等待确认"
    "completed" -> "已完成"
    "failed" -> "已失败"
    "cancelled" -> "已取消"
    "taken_over" -> "已接管"
    else -> this
}

@Composable
private fun String.statusColor() = when (this) {
    "completed" -> DarkPrimary
    "failed" -> MaterialTheme.colorScheme.error
    "cancelled", "taken_over" -> MutedText
    "waiting_confirmation" -> DarkPrimary
    else -> TextBlack
}
