package com.example.blueheartv.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.blueheartv.telemetry.AppEventLogger
import java.util.Locale

interface SpeechRecognizerCallback {
    fun onReadyForSpeech()
    fun onBeginningOfSpeech() {}
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(errorCode: Int, message: String)
    fun onRmsChanged(rmsdB: Float)
}

class SpeechRecognizerManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var callback: SpeechRecognizerCallback? = null
    private var listening = false
    private var lastPartialText = ""
    private var readyForSpeech = false
    private var beganSpeech = false
    private var endedSpeech = false
    private var rmsSampleCount = 0
    private var maxRmsDb = Float.NEGATIVE_INFINITY
    private var partialResultCount = 0
    private var finalText = ""
    private var errorCode: Int? = null
    private var errorMessage = ""
    private var sessionId = 0
    private var startedAtMillis = 0L
    private var readyAtMillis = 0L
    private var finishedAtMillis = 0L
    private var deferredStopRunnable: Runnable? = null

    private val handler = Handler(Looper.getMainLooper())

    private val maxDurationRunnable = Runnable {
        if (listening) {
            stopListening()
        }
    }

    private val stopSafetyRunnable = Runnable {
        if (listening) {
            finishWithError(
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                "等待语音识别结果超时",
            )
        }
    }

    fun isAvailable(): Boolean = platformRecognizerAvailable() || configuredRecognitionService() != null

    fun setCallback(callback: SpeechRecognizerCallback) {
        this.callback = callback
    }

    fun diagnosticsSnapshot(): VoiceRecognitionDiagnostics = VoiceRecognitionDiagnostics(
        sessionId = sessionId,
        started = listening || startedAtMillis > 0L,
        readyForSpeech = readyForSpeech,
        beganSpeech = beganSpeech,
        endedSpeech = endedSpeech,
        rmsSampleCount = rmsSampleCount,
        maxRmsDb = maxRmsDb,
        partialResultCount = partialResultCount,
        lastPartialText = lastPartialText,
        finalText = finalText,
        errorCode = errorCode,
        errorMessage = errorMessage,
        startedAtMillis = startedAtMillis,
        readyAtMillis = readyAtMillis,
        finishedAtMillis = finishedAtMillis,
    )

    fun diagnosticSummary(): String = diagnosticsSnapshot().summary()

    fun startListening(locale: Locale = Locale.CHINA) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { startListening(locale) }
            return
        }
        if (listening) cancel()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callback?.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, errorCodeToMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
            return
        }

        if (!isAvailable()) {
            logDebug("unavailable", recognitionEnvironmentSummary(locale))
            callback?.onError(SpeechRecognizer.ERROR_CLIENT, "设备未安装可用的语音识别服务")
            return
        }

        val sr = runCatching { createRecognizer() }
            .getOrElse {
                callback?.onError(SpeechRecognizer.ERROR_CLIENT, "语音识别服务启动失败")
                return
            }
        sr.setRecognitionListener(listener)
        recognizer = sr
        listening = true
        readyForSpeech = false
        beganSpeech = false
        endedSpeech = false
        rmsSampleCount = 0
        maxRmsDb = Float.NEGATIVE_INFINITY
        partialResultCount = 0
        lastPartialText = ""
        finalText = ""
        errorCode = null
        errorMessage = ""
        sessionId += 1
        startedAtMillis = SystemClock.elapsedRealtime()
        readyAtMillis = 0L
        finishedAtMillis = 0L
        logDebug("start", recognitionEnvironmentSummary(locale))

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3_500L)
        }
        runCatching {
            sr.startListening(intent)
            handler.postDelayed(maxDurationRunnable, MAX_RECORDING_DURATION_MS)
        }.onFailure {
            clearTimeouts()
            listening = false
            recognizer?.destroy()
            recognizer = null
            callback?.onError(SpeechRecognizer.ERROR_CLIENT, "语音识别服务启动失败")
        }
    }

    fun stopListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { stopListening() }
            return
        }
        if (!listening) return
        handler.removeCallbacks(maxDurationRunnable)
        val stopDelayMs = stopDelayMillis()
        if (stopDelayMs > 0L) {
            logDebug("defer_stop", "delayMs=$stopDelayMs ${diagnosticSummary()}")
            deferredStopRunnable?.let(handler::removeCallbacks)
            val runnable = Runnable { stopListening() }
            deferredStopRunnable = runnable
            handler.postDelayed(runnable, stopDelayMs)
            return
        }
        deferredStopRunnable = null
        logDebug("stop", diagnosticSummary())
        runCatching { recognizer?.stopListening() }
            .onSuccess { handler.postDelayed(stopSafetyRunnable, STOP_SAFETY_TIMEOUT_MS) }
            .onFailure {
                finishWithError(SpeechRecognizer.ERROR_CLIENT, "语音识别停止失败")
            }
    }

    fun cancel() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { cancel() }
            return
        }
        handler.removeCallbacks(maxDurationRunnable)
        handler.removeCallbacks(stopSafetyRunnable)
        deferredStopRunnable?.let(handler::removeCallbacks)
        deferredStopRunnable = null
        listening = false
        lastPartialText = ""
        readyForSpeech = false
        beganSpeech = false
        endedSpeech = false
        rmsSampleCount = 0
        maxRmsDb = Float.NEGATIVE_INFINITY
        partialResultCount = 0
        finalText = ""
        errorCode = null
        errorMessage = ""
        startedAtMillis = 0L
        readyAtMillis = 0L
        finishedAtMillis = 0L
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        logDebug("cancel", "session=$sessionId")
    }

    fun destroy() {
        callback = null
        cancel()
    }

    private fun clearTimeouts() {
        handler.removeCallbacks(maxDurationRunnable)
        handler.removeCallbacks(stopSafetyRunnable)
        deferredStopRunnable?.let(handler::removeCallbacks)
        deferredStopRunnable = null
    }

    private fun stopDelayMillis(): Long {
        val now = SystemClock.elapsedRealtime()
        val totalRemaining = MIN_TOTAL_LISTENING_MS - (now - startedAtMillis)
        if (!readyForSpeech || readyAtMillis <= 0L) {
            return maxOf(totalRemaining, 0L)
        }
        val readyRemaining = MIN_READY_LISTENING_MS - (now - readyAtMillis)
        return maxOf(totalRemaining, readyRemaining, 0L)
    }

    private fun platformRecognizerAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(context)
    }.getOrDefault(false)

    private fun configuredRecognitionService(): ComponentName? {
        val flattened = runCatching {
            Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        }.getOrNull()
        return flattened
            ?.takeUnless { it.isBlank() || it == "null" }
            ?.let(ComponentName::unflattenFromString)
    }

    private fun createRecognizer(): SpeechRecognizer {
        val component = configuredRecognitionService()
        return if (!platformRecognizerAvailable() && component != null) {
            SpeechRecognizer.createSpeechRecognizer(context, component)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    private fun recognitionEnvironmentSummary(locale: Locale): String {
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val serviceIntent = Intent(RecognitionService.SERVICE_INTERFACE)
        val activityCount = runCatching {
            context.packageManager.queryIntentActivities(recognizerIntent, 0).size
        }.getOrDefault(-1)
        val serviceCount = runCatching {
            context.packageManager.queryIntentServices(serviceIntent, 0).size
        }.getOrDefault(-1)
        val defaultService = configuredRecognitionService()?.flattenToShortString() ?: "none"
        return "session=$sessionId locale=${locale.toLanguageTag()} platformAvailable=${platformRecognizerAvailable()} isAvailable=${isAvailable()} recognizerActivities=$activityCount recognitionServices=$serviceCount defaultService=$defaultService"
    }

    private fun logDebug(event: String, message: String) {
        AppEventLogger.voice(event, message)
    }

    private fun finishWithError(error: Int, message: String = errorCodeToMessage(error)) {
        clearTimeouts()
        listening = false
        readyForSpeech = false
        finishedAtMillis = SystemClock.elapsedRealtime()
        errorCode = error
        errorMessage = message
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        logDebug("error", diagnosticSummary())
        callback?.onError(error, message)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            readyForSpeech = true
            readyAtMillis = SystemClock.elapsedRealtime()
            logDebug("ready", diagnosticSummary())
            callback?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            beganSpeech = true
            logDebug("begin", diagnosticSummary())
            callback?.onBeginningOfSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) {
            rmsSampleCount += 1
            if (rmsdB > maxRmsDb) maxRmsDb = rmsdB
            callback?.onRmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            endedSpeech = true
            logDebug("end", diagnosticSummary())
        }

        override fun onError(error: Int) {
            val partial = lastPartialText.trim()
            if (partial.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                clearTimeouts()
                listening = false
                readyForSpeech = false
                finishedAtMillis = SystemClock.elapsedRealtime()
                finalText = partial
                lastPartialText = ""
                runCatching { recognizer?.destroy() }
                recognizer = null
                logDebug("partial_as_final", diagnosticSummary())
                callback?.onFinalResult(partial)
                return
            }
            finishWithError(error)
        }

        override fun onResults(results: Bundle?) {
            clearTimeouts()
            listening = false
            readyForSpeech = false
            finishedAtMillis = SystemClock.elapsedRealtime()
            runCatching { recognizer?.destroy() }
            recognizer = null
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: lastPartialText
            finalText = text
            lastPartialText = ""
            logDebug("result", diagnosticSummary())
            callback?.onFinalResult(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                partialResultCount += 1
                lastPartialText = text
                logDebug("partial", "textLength=${text.length} ${diagnosticSummary()}")
                callback?.onPartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val MAX_RECORDING_DURATION_MS = 60_000L
        private const val STOP_SAFETY_TIMEOUT_MS = 5_000L
        private const val MIN_TOTAL_LISTENING_MS = 1_200L
        private const val MIN_READY_LISTENING_MS = 900L

        fun errorCodeToMessage(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH -> "未检测到语音"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音"
            SpeechRecognizer.ERROR_NETWORK -> "语音识别网络错误"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络超时"
            SpeechRecognizer.ERROR_AUDIO -> "录音错误"
            SpeechRecognizer.ERROR_SERVER -> "语音识别服务错误"
            SpeechRecognizer.ERROR_CLIENT -> "语音识别客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别服务繁忙"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "请求过于频繁"
            else -> "语音识别失败"
        }
    }
}
