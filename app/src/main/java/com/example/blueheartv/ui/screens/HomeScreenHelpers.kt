package com.example.blueheartv.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
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
import com.example.blueheartv.voice.SpeechRecognizerCallback
import com.example.blueheartv.voice.SpeechRecognizerManager
import com.example.blueheartv.voice.VoiceRecognitionDiagnostics
import com.example.blueheartv.voice.VoiceRecognitionFallbackPolicy
import com.example.blueheartv.voice.VoiceRecordingState
import kotlinx.coroutines.delay
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
    var systemFallbackInProgress by remember { mutableStateOf(false) }

    val speechManager = remember(context) { SpeechRecognizerManager(context) }

    DisposableEffect(Unit) {
        onDispose { speechManager.destroy() }
    }

    fun systemSpeechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.CHINA.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        systemFallbackInProgress = false
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
                .trim()
        } else {
            ""
        }
        amplitudeDb = 0f
        if (text.isNotBlank()) {
            resultText = text
            voiceRecordingState = VoiceRecordingState.SUCCESS
            AppEventLogger.info("VoiceRecognition.system_result", "textLength=${text.length}")
            viewModel.sendVoiceText(text)
        } else {
            resultText = "系统语音识别未返回文本"
            voiceRecordingState = VoiceRecordingState.FAILED
            AppEventLogger.warning("VoiceRecognition.system_empty", "resultCode=${result.resultCode}")
        }
    }

    fun launchSystemSpeechFallback(reason: String, diagnostics: VoiceRecognitionDiagnostics) {
        if (systemFallbackInProgress) return
        val intent = systemSpeechIntent()
        val canResolve = intent.resolveActivity(context.packageManager) != null
        AppEventLogger.warning(
            "VoiceRecognition.fallback",
            "reason=$reason canResolve=$canResolve ${diagnostics.summary()}",
        )
        if (!canResolve) {
            resultText = "设备未安装可用的系统语音识别界面"
            voiceRecordingState = VoiceRecordingState.FAILED
            return
        }
        systemFallbackInProgress = true
        resultText = "正在打开系统语音识别..."
        voiceRecordingState = VoiceRecordingState.RECOGNIZING
        ToastUtil.show("正在改用系统语音识别", ToastType.INFO)
        runCatching { systemSpeechLauncher.launch(intent) }
            .onFailure { error ->
                systemFallbackInProgress = false
                val message = if (error is ActivityNotFoundException) {
                    "设备未安装可用的系统语音识别界面"
                } else {
                    "系统语音识别启动失败"
                }
                resultText = message
                voiceRecordingState = VoiceRecordingState.FAILED
                AppEventLogger.error("VoiceRecognition.fallback_launch_failed", message, error)
            }
    }

    val speechCallback = remember {
        object : SpeechRecognizerCallback {
            override fun onReadyForSpeech() {
                voiceRecordingState = VoiceRecordingState.RECORDING
            }

            override fun onPartialResult(text: String) {
                partialText = text
            }

            override fun onFinalResult(text: String) {
                amplitudeDb = 0f
                if (text.isNotBlank()) {
                    resultText = text
                    voiceRecordingState = VoiceRecordingState.SUCCESS
                    viewModel.sendVoiceText(text)
                } else {
                    val diagnostics = speechManager.diagnosticsSnapshot()
                    launchSystemSpeechFallback("blank_final_result", diagnostics)
                }
            }

            override fun onError(errorCode: Int, message: String) {
                amplitudeDb = 0f
                val diagnostics = VoiceRecognitionFallbackPolicy.withCallbackError(
                    callbackErrorCode = errorCode,
                    callbackMessage = message,
                    diagnostics = speechManager.diagnosticsSnapshot(),
                )
                if (VoiceRecognitionFallbackPolicy.shouldUseSystemRecognizer(errorCode, diagnostics)) {
                    launchSystemSpeechFallback("direct_error_$errorCode", diagnostics)
                } else {
                    resultText = "$message（${diagnostics.summary()}）"
                    voiceRecordingState = VoiceRecordingState.FAILED
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                amplitudeDb = rmsdB
            }
        }
    }

    // Permission handlers
    val attachPermissionHandler = rememberPermissionHandler(snackbarHostState) {
        filePickerLauncher.launch("image/*")
    }
    val voicePermissionHandler = rememberPermissionHandler(snackbarHostState) {
        if (!speechManager.isAvailable()) {
            launchSystemSpeechFallback(
                reason = "direct_recognizer_unavailable",
                diagnostics = VoiceRecognitionDiagnostics(errorMessage = "SpeechRecognizer.isRecognitionAvailable=false"),
            )
            return@rememberPermissionHandler
        }
        speechManager.setCallback(speechCallback)
        speechManager.startListening()
        voiceRecordingState = VoiceRecordingState.RECORDING
        partialText = ""
        resultText = ""
        systemFallbackInProgress = false
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
                speechManager.stopListening()
                voiceRecordingState = VoiceRecordingState.RECOGNIZING
            },
            cancelVoiceRecording = {
                speechManager.cancel()
                voiceRecordingState = VoiceRecordingState.IDLE
                partialText = ""
                amplitudeDb = 0f
            },
            setVoiceCancelling = {
                voiceRecordingState = VoiceRecordingState.CANCELLING
            },
            setVoiceRecording = {
                voiceRecordingState = VoiceRecordingState.RECORDING
            },
            voiceRecordingState = voiceRecordingStateState,
            partialText = partialTextState,
            amplitudeDb = amplitudeDbState,
            resultText = resultTextState,
        )
    }
}
