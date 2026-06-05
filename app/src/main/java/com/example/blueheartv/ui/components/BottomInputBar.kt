package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.voice.InputMode
import com.example.blueheartv.voice.VoiceRecordingState

@Composable
fun BottomInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    sendEnabled: Boolean = true,
    onAttachClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    inputMode: InputMode = InputMode.TEXT,
    voiceRecordingState: VoiceRecordingState = VoiceRecordingState.IDLE,
    onVoiceStart: () -> Unit = {},
    onVoiceEnd: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    onVoiceModeTap: () -> Unit = {},
    onSwipeToCancelling: () -> Unit = {},
    onSwipeBackToRecording: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDividerLine()

        Row(
            modifier = Modifier
                .padding(horizontal = 13.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .background(SurfaceWhite, RoundedCornerShape(16.dp))
                .border(0.5.dp, StrokeMuted, RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onAttachClick() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_attachment),
                    contentDescription = "Attach file",
                    modifier = Modifier.size(24.dp),
                )
            }

            when (inputMode) {
                InputMode.TEXT -> {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = "发消息...",
                                fontSize = 14.sp,
                                color = GrayText,
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = { if (it.length <= 2000) onValueChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 14.sp, color = TextBlack),
                            singleLine = true,
                        )
                    }
                }

                InputMode.VOICE -> {
                    HoldToSpeakButton(
                        recordingState = voiceRecordingState,
                        onLongPressStart = onVoiceStart,
                        onSwipeToCancelling = onSwipeToCancelling,
                        onSwipeBackToRecording = onSwipeBackToRecording,
                        onRelease = { cancelled ->
                            if (cancelled) onVoiceCancel() else onVoiceEnd()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onMicClick() },
                contentAlignment = Alignment.Center,
            ) {
                when (inputMode) {
                    InputMode.TEXT -> {
                        Image(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = "Voice input",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    InputMode.VOICE -> {
                        Icon(
                            imageVector = Icons.Outlined.Keyboard,
                            contentDescription = "Keyboard",
                            tint = GrayText,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { if (sendEnabled) onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_send_arrow),
                    contentDescription = "Send",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun IconButton24(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun HorizontalDividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(DividerColor)
    )
}
