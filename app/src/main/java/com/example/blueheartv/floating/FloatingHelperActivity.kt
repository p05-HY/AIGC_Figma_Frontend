package com.example.blueheartv.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        _speechResult.tryEmit(text)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            ACTION_PICK_IMAGE -> imagePickerLauncher.launch("image/*")
            ACTION_SPEECH -> launchSpeechRecognizer()
            else -> finish()
        }
    }

    private fun launchSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
        }
        if (intent.resolveActivity(packageManager) != null) {
            speechLauncher.launch(intent)
        } else {
            _speechResult.tryEmit(null)
            finish()
        }
    }
}
