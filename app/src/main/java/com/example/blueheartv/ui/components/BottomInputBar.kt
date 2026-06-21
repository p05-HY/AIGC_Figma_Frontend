package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
    val submit = { if (sendEnabled) onSend() }
    val inputShape = RoundedCornerShape(28.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDividerLine()

        Row(
            modifier = Modifier
                .padding(horizontal = 13.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .shadow(4.dp, inputShape)
                .background(SurfaceWhite, inputShape)
                .border(0.8.dp, BrandStroke, inputShape)
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconButton(
                onClick = onAttachClick,
                contentDescription = stringResource(R.string.action_attach_file),
                size = 48.dp,
                containerColor = Color.Transparent,
                contentColor = IconGray,
            ) { tint ->
                Icon(
                    painter = painterResource(R.drawable.ic_attachment),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }

            when (inputMode) {
                InputMode.TEXT -> {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(R.string.input_placeholder),
                                fontSize = 14.sp,
                                color = GrayText,
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = { if (it.length <= 2000) onValueChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 15.sp, color = TextBlack, lineHeight = 22.sp),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(BrandPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit() }),
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

            EchoIconButton(
                onClick = onMicClick,
                contentDescription = stringResource(
                    if (inputMode == InputMode.TEXT) R.string.action_voice_input else R.string.action_keyboard_input,
                ),
                size = 48.dp,
                containerColor = Color.Transparent,
                contentColor = IconGray,
            ) { tint ->
                when (inputMode) {
                    InputMode.TEXT -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    InputMode.VOICE -> {
                        Icon(
                            imageVector = Icons.Outlined.Keyboard,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            EchoIconButton(
                onClick = submit,
                contentDescription = stringResource(R.string.action_send),
                enabled = sendEnabled,
                size = 40.dp,
                containerColor = BrandPrimary,
                disabledContainerColor = Color(0xFFE5E7EB),
                contentColor = Color.White,
                disabledContentColor = IconGray,
            ) { tint ->
                Icon(
                    painter = painterResource(R.drawable.ic_send_arrow),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
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
