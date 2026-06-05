package com.example.blueheartv.telemetry

import android.util.Log

object AppEventLogger {
    private const val TAG = "BlueHeartV"
    private const val VOICE_TAG = "VoiceRecognition"

    fun info(event: String, message: String) {
        safeLog { Log.i(TAG, "[$event] $message") }
    }

    fun warning(event: String, message: String) {
        safeLog { Log.w(TAG, "[$event] $message") }
    }

    fun error(
        event: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        safeLog { Log.e(TAG, "[$event] $message", throwable) }
    }

    fun voice(event: String, message: String) {
        safeLog { Log.i(VOICE_TAG, "[$event] $message") }
        info(event, message)
    }

    private fun safeLog(block: () -> Unit) {
        runCatching(block)
    }
}
