package com.example.blueheartv.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.voice.VoiceRecordingState

@Composable
fun VoiceRecordingOverlay(
    visible: Boolean,
    recordingState: VoiceRecordingState,
    partialText: String,
    amplitudeDb: Float,
    resultText: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.9f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OverlayBlack),
            contentAlignment = Alignment.Center,
        ) {
            when (recordingState) {
                VoiceRecordingState.RECORDING -> RecordingContent(partialText, amplitudeDb, onCancel)
                VoiceRecordingState.CANCELLING -> CancellingContent()
                VoiceRecordingState.RECOGNIZING -> RecognizingContent()
                VoiceRecordingState.SUCCESS -> SuccessContent(resultText)
                VoiceRecordingState.FAILED -> FailedContent(resultText)
                VoiceRecordingState.IDLE -> {}
            }
        }
    }
}

@Composable
private fun RecordingContent(partialText: String, amplitudeDb: Float, onCancel: () -> Unit) {
    val normalizedAmplitude = ((amplitudeDb + 2f) / 12f).coerceIn(0f, 1f)
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.15f + normalizedAmplitude * 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .background(BlueAccent.copy(alpha = 0.15f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(GlassFill, CircleShape)
                    .border(1.dp, ChipStroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = BlueAccent,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        if (partialText.isNotBlank()) {
            Text(
                text = partialText,
                fontSize = 16.sp,
                color = SurfaceWhite,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        Text(
            text = "↑ 上滑取消",
            fontSize = 13.sp,
            color = MutedText,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .background(GlassFill, CircleShape)
                .border(1.dp, ChipStroke, CircleShape)
                .clickable { onCancel() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "取消录音",
                tint = MutedText,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CancellingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(BlueAccentLight.copy(alpha = 0.2f), CircleShape)
                .border(1.dp, BlueAccentLight.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                tint = BlueAccentLight,
                modifier = Modifier.size(32.dp),
            )
        }

        Text(
            text = "松开取消",
            fontSize = 16.sp,
            color = BlueAccentLight,
        )
    }
}

@Composable
private fun RecognizingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = BlueAccent,
            strokeWidth = 3.dp,
        )

        Text(
            text = "识别中...",
            fontSize = 14.sp,
            color = MutedText,
        )
    }
}

@Composable
private fun SuccessContent(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(SuccessGreen.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, SuccessGreen.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(32.dp),
            )
        }

        if (text.isNotBlank()) {
            Text(
                text = text,
                fontSize = 16.sp,
                color = SurfaceWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        Text(
            text = "已填入输入框",
            fontSize = 13.sp,
            color = MutedText,
        )
    }
}

@Composable
private fun FailedContent(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(ErrorRed.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, ErrorRed.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(32.dp),
            )
        }

        if (message.isNotBlank()) {
            Text(
                text = message,
                fontSize = 14.sp,
                color = SurfaceWhite,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
