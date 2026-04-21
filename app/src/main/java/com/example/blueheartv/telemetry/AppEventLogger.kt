package com.example.blueheartv.telemetry

import android.util.Log

object AppEventLogger {
    private const val TAG = "BlueHeartV"

    fun info(event: String, message: String) {
        Log.i(TAG, "[$event] $message")
    }

    fun warning(event: String, message: String) {
        Log.w(TAG, "[$event] $message")
    }

    fun error(
        event: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        Log.e(TAG, "[$event] $message", throwable)
    }
}
