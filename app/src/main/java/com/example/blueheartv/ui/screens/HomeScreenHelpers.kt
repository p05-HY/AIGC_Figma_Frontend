package com.example.blueheartv.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.blueheartv.R
import com.example.blueheartv.model.ChatAttachment
import com.example.blueheartv.util.*
import com.example.blueheartv.viewmodel.ChatViewModel
import java.util.*

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
    val voiceInputInDevText = stringResource(R.string.feature_in_dev_voice_input)
    val promptReadScreen = stringResource(R.string.prompt_read_screen)
    val promptTodaySchedule = stringResource(R.string.prompt_today_schedule)
    val promptTodayDelivery = stringResource(R.string.prompt_today_delivery)
    val promptTodayWeather = stringResource(R.string.prompt_today_weather)
    val rationaleMediaAttach = stringResource(R.string.rationale_media_attach)
    val rationaleAudioInput = stringResource(R.string.rationale_audio_input)
    val rationaleCalendarSchedule = stringResource(R.string.rationale_calendar_schedule)
    val rationaleNotificationDelivery = stringResource(R.string.rationale_notification_delivery)
    val rationaleLocationWeather = stringResource(R.string.rationale_location_weather)

    // TTS
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
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
            val attachment = runCatching { uri.toImageAttachment(context) }.getOrNull()
            if (attachment != null) {
                viewModel.addImageAttachment(attachment)
                ToastUtil.show("已添加图片: ${attachment.displayName}", ToastType.SUCCESS)
            } else {
                ToastUtil.show("无法读取图片", ToastType.ERROR)
            }
        }
    }

    // Permission handlers
    val attachPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        filePickerLauncher.launch("image/*")
    }
    val micPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        ToastUtil.show(voiceInputInDevText, ToastType.INFO)
    }
    val calendarPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        viewModel.sendQuickAction(promptTodaySchedule)
    }
    val locationPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        viewModel.sendQuickAction(promptTodayWeather)
    }
    val notificationPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        viewModel.sendQuickAction(promptTodayDelivery)
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
                        rationaleMessage = rationaleMediaAttach,
                    ),
                )
            },
            requestMic = {
                micPermissionHandler(
                    PermissionRequest(
                        permissions = audioPermissions(),
                        rationaleMessage = rationaleAudioInput,
                    ),
                )
            },
            requestQuickAction = { index ->
                val prompts = listOf(
                    promptReadScreen,
                    promptTodaySchedule,
                    promptTodayDelivery,
                    promptTodayWeather,
                )
                when (index) {
                    0 -> viewModel.sendQuickAction(prompts[index])
                    1 -> calendarPermissionHandler(
                        PermissionRequest(
                            permissions = calendarPermissions(),
                            rationaleMessage = rationaleCalendarSchedule,
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
                                    rationaleMessage = rationaleNotificationDelivery,
                                ),
                            )
                        }
                    }

                    3 -> locationPermissionHandler(
                        PermissionRequest(
                            permissions = locationPermissions(),
                            rationaleMessage = rationaleLocationWeather,
                        ),
                    )
                }
            },
        )
    }
}

private fun Uri.toImageAttachment(context: Context): ChatAttachment? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(this)?.takeIf { it.startsWith("image/") } ?: return null
    val bytes = resolver.openInputStream(this)?.use { it.readBytes() } ?: return null
    val displayName = resolver.query(this, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: lastPathSegment ?: "image"
    return ChatAttachment(
        id = UUID.randomUUID().toString(),
        displayName = displayName,
        mimeType = mimeType,
        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}
