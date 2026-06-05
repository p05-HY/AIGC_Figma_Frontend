package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.voice.VoiceRecordingState

private val CANCEL_THRESHOLD_DP = 50.dp

@Composable
fun HoldToSpeakButton(
    recordingState: VoiceRecordingState,
    onLongPressStart: () -> Unit,
    onSwipeToCancelling: () -> Unit,
    onSwipeBackToRecording: () -> Unit,
    onRelease: (cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cancelThresholdPx = with(density) { CANCEL_THRESHOLD_DP.toPx() }

    val backgroundColor = when (recordingState) {
        VoiceRecordingState.RECORDING -> BlueAccent.copy(alpha = 0.12f)
        VoiceRecordingState.CANCELLING -> BlueAccentLight.copy(alpha = 0.12f)
        else -> ChipFill
    }
    val text = when (recordingState) {
        VoiceRecordingState.RECORDING -> "松开 结束"
        VoiceRecordingState.CANCELLING -> "松开 取消"
        else -> "按住 说话"
    }
    val textColor = when (recordingState) {
        VoiceRecordingState.RECORDING -> BlueAccent
        VoiceRecordingState.CANCELLING -> BlueAccentLight
        else -> GrayText
    }

    var isPressed by remember { mutableStateOf(false) }
    var longPressActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(0.5.dp, ChipStroke, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startY = down.position.y
                    isPressed = true
                    longPressActive = true
                    onLongPressStart()
                    var cancelMode = false

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (longPressActive && change.pressed) {
                            val dragUp = startY - change.position.y
                            if (dragUp > cancelThresholdPx && !cancelMode) {
                                cancelMode = true
                                onSwipeToCancelling()
                            } else if (dragUp <= cancelThresholdPx && cancelMode) {
                                cancelMode = false
                                onSwipeBackToRecording()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    isPressed = false
                    if (longPressActive) {
                        onRelease(cancelMode)
                    }
                    longPressActive = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = textColor,
        )
    }
}
