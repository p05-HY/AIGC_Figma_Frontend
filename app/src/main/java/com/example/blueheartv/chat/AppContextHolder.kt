package com.example.blueheartv.chat

import android.content.Context

object AppContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun getOrNull(): Context? = appContext
}
