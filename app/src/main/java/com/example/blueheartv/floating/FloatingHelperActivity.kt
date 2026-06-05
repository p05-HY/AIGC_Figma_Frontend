package com.example.blueheartv.floating

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.blueheartv.voice.SpeechRecognizerCallback
import com.example.blueheartv.voice.SpeechRecognizerManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FloatingHelperActivity : ComponentActivity() {

    companion object {
        private const val ACTION_PICK_IMAGE = "com.example.blueheartv.PICK_IMAGE"
        private const val ACTION_SPEECH = "com.example.blueheartv.SPEECH"

        private val _imageResult = MutableSharedFlow<Uri?>(extraBufferCapacity = 1)
        val imageResult: SharedFlow<Uri?> = _imageResult.asSharedFlow()

        private val _speechResult = MutableSharedFlow<String?>(extraBufferCapacity = 1)
        val speechResult: SharedFlow<String?> = _speechResult.asSharedFlow()

        private val _speechError = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val speechError: SharedFlow<String> = _speechError.asSharedFlow()

        fun launchImagePick(context: Context) {
            val intent = Intent(context, FloatingHelperActivity::class.java).apply {
                action = ACTION_PICK_IMAGE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun launchSpeech(context: Context) {
            val intent = Intent(context, FloatingHelperActivity::class.java).apply {
                action = ACTION_SPEECH
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        _imageResult.tryEmit(uri)
        finish()
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                _speechError.tryEmit("未检测到语音")
            } else {
                _speechResult.tryEmit(text)
            }
        } else {
            _speechError.tryEmit("语音识别已取消或未检测到语音")
        }
        finish()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechRecognizerInternal()
        } else {
            _speechError.tryEmit("需要麦克风权限")
            finish()
        }
    }

    private var directSpeechManager: SpeechRecognizerManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            ACTION_PICK_IMAGE -> imagePickerLauncher.launch("image/*")
            ACTION_SPEECH -> launchSpeechRecognizer()
            else -> finish()
        }
    }

    private fun launchSpeechRecognizer() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        launchSpeechRecognizerInternal()
    }

    private fun launchSpeechRecognizerInternal() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
        }
        if (intent.resolveActivity(packageManager) != null) {
            try {
                speechLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                launchDirectSpeechRecognizer()
            }
        } else {
            launchDirectSpeechRecognizer()
        }
    }

    private fun launchDirectSpeechRecognizer() {
        val manager = SpeechRecognizerManager(this)
        directSpeechManager = manager
        if (!manager.isAvailable()) {
            _speechError.tryEmit("系统未暴露可用的第三方语音识别服务")
            finish()
            return
        }
        manager.setCallback(object : SpeechRecognizerCallback {
            override fun onReadyForSpeech() = Unit

            override fun onPartialResult(text: String) = Unit

            override fun onFinalResult(text: String) {
                if (text.isBlank()) {
                    _speechError.tryEmit("未检测到语音")
                } else {
                    _speechResult.tryEmit(text)
                }
                finish()
            }

            override fun onError(errorCode: Int, message: String) {
                _speechError.tryEmit(message)
                finish()
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
        })
        Toast.makeText(this, "请说话...", Toast.LENGTH_SHORT).show()
        manager.startListening()
    }

    override fun onDestroy() {
        directSpeechManager?.destroy()
        directSpeechManager = null
        super.onDestroy()
    }
}
