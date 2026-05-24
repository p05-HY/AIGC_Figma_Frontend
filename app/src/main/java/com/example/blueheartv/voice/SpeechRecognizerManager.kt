package com.example.blueheartv.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

interface SpeechRecognizerCallback {
    fun onReadyForSpeech()
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(errorCode: Int, message: String)
    fun onRmsChanged(rmsdB: Float)
}

class SpeechRecognizerManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var callback: SpeechRecognizerCallback? = null
    private var listening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun setCallback(callback: SpeechRecognizerCallback) {
        this.callback = callback
    }

    fun startListening(locale: Locale = Locale.CHINA) {
        if (listening) cancel()

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        sr.setRecognitionListener(listener)
        recognizer = sr
        listening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        sr.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun cancel() {
        listening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() {
        callback = null
        cancel()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            callback?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            callback?.onRmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            listening = false
            recognizer?.destroy()
            recognizer = null
            callback?.onError(error, errorCodeToMessage(error))
        }

        override fun onResults(results: Bundle?) {
            listening = false
            recognizer?.destroy()
            recognizer = null
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            callback?.onFinalResult(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                callback?.onPartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
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
