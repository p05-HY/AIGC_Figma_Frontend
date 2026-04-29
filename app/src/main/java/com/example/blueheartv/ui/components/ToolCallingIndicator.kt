package com.example.blueheartv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.model.ToolCall
import com.example.blueheartv.ui.theme.BlueAccent

@Composable
fun ToolCallingIndicators(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier,
) {
    val icons = listOf(
        Icons.Outlined.MyLocation,
        Icons.Outlined.Map,
        Icons.Outlined.Route,
    )

    Column(
        modifier = modifier.padding(start = 33.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        toolCalls.forEachIndexed { index, toolCall ->
            ToolCallRow(
                icon = icons.getOrElse(index) { Icons.Outlined.Settings },
                label = toolCall.label,
                isComplete = toolCall.isComplete,
            )
        }
    }
}

@Composable
private fun ToolCallRow(
    icon: ImageVector,
    label: String,
    isComplete: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tool_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (!isComplete) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .then(if (!isComplete) Modifier.rotate(rotation) else Modifier),
            tint = BlueAccent,
        )
        Text(
            text = label,
            fontSize = 16.sp,
            color = BlueAccent,
            lineHeight = 24.sp,
        )
    }
}
