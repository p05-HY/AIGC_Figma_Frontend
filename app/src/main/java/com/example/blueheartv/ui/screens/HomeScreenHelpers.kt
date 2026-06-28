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
import androidx.compose.ui.res.stringResource
import com.example.blueheartv.R
import com.example.blueheartv.util.*
import com.example.blueheartv.viewmodel.ChatViewModel
import com.example.blueheartv.telemetry.AppEventLogger
import com.example.blueheartv.voice.MIN_VOICE_PCM_BYTES
import com.example.blueheartv.voice.VOICE_PCM_FORMAT
import com.example.blueheartv.voice.VOICE_PCM_SAMPLE_RATE
import com.example.blueheartv.voice.VoiceAudioRecorder
import com.example.blueheartv.voice.VoiceRecordingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class HomeScreenActions(
    val copyToClipboard: (String) -> Unit,
    val speak: (String) -> Unit,
    val stopSpeaking: () -> Unit,
    val requestAttach: () -> Unit,
    val requestMic: () -> Unit,
    val requestQuickAction: (index: Int) -> Unit,
    val startVoiceRecording: () -> Unit,
    val stopVoiceRecording: () -> Unit,
    val cancelVoiceRecording: () -> Unit,
    val setVoiceCancelling: () -> Unit,
    val setVoiceRecording: () -> Unit,
    val voiceRecordingState: State<VoiceRecordingState>,
    val partialText: State<String>,
    val amplitudeDb: State<Float>,
    val resultText: State<String>,
)

@Composable
fun rememberHomeScreenActions(
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
): HomeScreenActions {
    val context = LocalContext.current
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

    // Voice recording state
    var voiceRecordingState by remember { mutableStateOf(VoiceRecordingState.IDLE) }
    var partialText by remember { mutableStateOf("") }
    var amplitudeDb by remember { mutableFloatStateOf(0f) }
    var resultText by remember { mutableStateOf("") }
    var voiceUploadInProgress by remember { mutableStateOf(false) }
    val voiceRecorder = remember { VoiceAudioRecorder() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { voiceRecorder.cancel() }
    }

    fun beginVoiceRecording() {
        if (voiceUploadInProgress || voiceRecorder.isRecording) return
        partialText = ""
        resultText = ""
        amplitudeDb = 0f
        val started = voiceRecorder.start(scope) { amplitude ->
            amplitudeDb = amplitude
        }
        if (started) {
            voiceRecordingState = VoiceRecordingState.RECORDING
            AppEventLogger.info("VoiceRecognition.recording_started", "sampleRate=$VOICE_PCM_SAMPLE_RATE format=$VOICE_PCM_FORMAT")
        } else {
            resultText = "录音启动失败"
            voiceRecordingState = VoiceRecordingState.FAILED
            ToastUtil.show("录音启动失败", ToastType.ERROR)
            AppEventLogger.warning("VoiceRecognition.recording_start_failed", "AudioRecord initialization failed")
        }
    }

    fun finishVoiceRecording() {
        if (!voiceRecorder.isRecording) return
        voiceRecordingState = VoiceRecordingState.RECOGNIZING
        voiceUploadInProgress = true
        scope.launch {
            val audio = runCatching { voiceRecorder.stop() }
                .getOrElse { error ->
                    voiceUploadInProgress = false
                    amplitudeDb = 0f
                    resultText = "录音停止失败"
                    voiceRecordingState = VoiceRecordingState.FAILED
                    AppEventLogger.error("VoiceRecognition.recording_stop_failed", error.message ?: "unknown", error)
                    return@launch
                }
            amplitudeDb = 0f
            if (audio.size < MIN_VOICE_PCM_BYTES) {
                voiceUploadInProgress = false
                resultText = "录音时间过短"
                voiceRecordingState = VoiceRecordingState.FAILED
                ToastUtil.show("录音时间过短", ToastType.ERROR)
                AppEventLogger.warning("VoiceRecognition.audio_too_short", "bytes=${audio.size}")
                return@launch
            }

            runCatching {
                viewModel.transcribeVoiceAudio(
                    audio = audio,
                    audioFormat = VOICE_PCM_FORMAT,
                    sampleRate = VOICE_PCM_SAMPLE_RATE,
                    language = Locale.CHINA.toLanguageTag(),
                )
            }.onSuccess { transcription ->
                val text = transcription.text.trim()
                if (text.isBlank()) {
                    resultText = "语音识别未返回文本"
                    voiceRecordingState = VoiceRecordingState.FAILED
                    ToastUtil.show("语音识别未返回文本", ToastType.ERROR)
                } else {
                    viewModel.sendVoiceText(text)
                    resultText = text
                    voiceRecordingState = VoiceRecordingState.SUCCESS
                    ToastUtil.show("已填入输入框", ToastType.SUCCESS)
                    AppEventLogger.info(
                        "VoiceRecognition.aliyun_result",
                        "textLength=${text.length} provider=${transcription.provider} requestId=${transcription.requestId.orEmpty()}",
                    )
                }
            }.onFailure { error ->
                val message = error.message?.takeIf { it.isNotBlank() } ?: "语音识别失败"
                resultText = message
                voiceRecordingState = VoiceRecordingState.FAILED
                ToastUtil.show(message, ToastType.ERROR)
                AppEventLogger.error("VoiceRecognition.aliyun_failed", message, error)
            }
            voiceUploadInProgress = false
        }
    }

    fun cancelVoiceRecordingInternal() {
        voiceRecorder.cancel()
        voiceUploadInProgress = false
        voiceRecordingState = VoiceRecordingState.IDLE
        partialText = ""
        amplitudeDb = 0f
        resultText = ""
    }

    // Permission handlers
    val attachPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        filePickerLauncher.launch("image/*")
    }
    val voicePermissionHandler = rememberPermissionHandler(snackbarHostState) {
        beginVoiceRecording()
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

    LaunchedEffect(voiceRecordingState) {
        when (voiceRecordingState) {
            VoiceRecordingState.SUCCESS -> {
                delay(1200)
                voiceRecordingState = VoiceRecordingState.IDLE
                partialText = ""
                resultText = ""
            }
            VoiceRecordingState.FAILED -> {
                delay(1500)
                voiceRecordingState = VoiceRecordingState.IDLE
                partialText = ""
                resultText = ""
            }
            else -> {}
        }
    }

    val voiceRecordingStateState = rememberUpdatedState(voiceRecordingState)
    val partialTextState = rememberUpdatedState(partialText)
    val amplitudeDbState = rememberUpdatedState(amplitudeDb)
    val resultTextState = rememberUpdatedState(resultText)

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
                viewModel.toggleInputMode()
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
            startVoiceRecording = {
                voicePermissionHandler(
                    PermissionRequest(
                        permissions = audioPermissions(),
                        rationaleMessage = rationaleAudioInput,
                    ),
                )
            },
            stopVoiceRecording = {
                finishVoiceRecording()
            },
            cancelVoiceRecording = {
                cancelVoiceRecordingInternal()
            },
            setVoiceCancelling = {
                voiceRecordingState = VoiceRecordingState.CANCELLING
            },
            setVoiceRecording = {
                if (voiceRecorder.isRecording) {
                    voiceRecordingState = VoiceRecordingState.RECORDING
                }
            },
            voiceRecordingState = voiceRecordingStateState,
            partialText = partialTextState,
            amplitudeDb = amplitudeDbState,
            resultText = resultTextState,
        )
    }
}
