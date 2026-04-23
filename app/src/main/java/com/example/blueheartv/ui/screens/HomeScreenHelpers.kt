package com.example.blueheartv.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.blueheartv.R
import com.example.blueheartv.util.*
import com.example.blueheartv.viewmodel.ChatViewModel
import java.util.Locale

class HomeScreenActions(
    val copyToClipboard: (String) -> Unit,
    val speak: (String) -> Unit,
    val stopSpeaking: () -> Unit,
    val requestAttach: () -> Unit,
    val requestMic: () -> Unit,
    val requestQuickAction: (index: Int) -> Unit,
)

@Composable
fun rememberHomeScreenActions(
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
): HomeScreenActions {
    val context = LocalContext.current

    // TTS
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.CHINESE)
            }
        }
        tts = engine
        onDispose { engine.shutdown() }
    }

    // File picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            ToastUtil.show("已选择文件: $uri", ToastType.INFO)
        }
    }

    // Permission handlers
    val attachPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        filePickerLauncher.launch("*/*")
    }
    val micPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        ToastUtil.show(context.getString(R.string.feature_in_dev_voice_input), ToastType.INFO)
    }
    val calendarPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        viewModel.sendQuickAction(context.getString(R.string.prompt_today_schedule))
    }
    val locationPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        viewModel.sendQuickAction(context.getString(R.string.prompt_today_weather))
    }
    val notificationPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        viewModel.sendQuickAction(context.getString(R.string.prompt_today_delivery))
    }

    return remember(viewModel, snackbarHostState) {
        HomeScreenActions(
            copyToClipboard = { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
                ToastUtil.show("已复制到剪贴板", ToastType.SUCCESS)
            },
            speak = { text ->
                tts?.stop()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            },
            stopSpeaking = { tts?.stop() },
            requestAttach = {
                attachPermissionHandler(
                    PermissionRequest(
                        permissions = imagePermissions(),
                        rationaleMessage = context.getString(R.string.rationale_media_attach),
                    ),
                )
            },
            requestMic = {
                micPermissionHandler(
                    PermissionRequest(
                        permissions = audioPermissions(),
                        rationaleMessage = context.getString(R.string.rationale_audio_input),
                    ),
                )
            },
            requestQuickAction = { index ->
                val prompts = listOf(
                    context.getString(R.string.prompt_read_screen),
                    context.getString(R.string.prompt_today_schedule),
                    context.getString(R.string.prompt_today_delivery),
                    context.getString(R.string.prompt_today_weather),
                )
                when (index) {
                    0 -> viewModel.sendQuickAction(prompts[index])
                    1 -> calendarPermissionHandler(
                        PermissionRequest(
                            permissions = calendarPermissions(),
                            rationaleMessage = context.getString(R.string.rationale_calendar_schedule),
                        ),
                    )
                    2 -> {
                        val perms = notificationPermissions()
                        if (perms.isEmpty()) {
                            viewModel.sendQuickAction(prompts[index])
                        } else {
                            notificationPermissionHandler(
                                PermissionRequest(
                                    permissions = perms,
                                    rationaleMessage = context.getString(R.string.rationale_notification_delivery),
                                ),
                            )
                        }
                    }
                    3 -> locationPermissionHandler(
                        PermissionRequest(
                            permissions = locationPermissions(),
                            rationaleMessage = context.getString(R.string.rationale_location_weather),
                        ),
                    )
                }
            },
        )
    }
}
