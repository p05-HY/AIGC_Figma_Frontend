package com.example.blueheartv.control

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.blueheartv.R

class AdbOverlayController(
    context: Context
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val container = LinearLayout(appContext).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xCC111827.toInt())
        setPadding(24, 20, 24, 20)
        alpha = 0.96f
    }

    private val titleView = TextView(appContext).apply {
        text = "ADB"
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 16f
    }

    private val statusView = TextView(appContext).apply {
        text = appContext.getString(R.string.overlay_waiting)
        setTextColor(0xFF93C5FD.toInt())
        textSize = 13f
    }

    private val detailView = TextView(appContext).apply {
        setTextColor(0xFFE5E7EB.toInt())
        textSize = 12f
    }

    private val interactionButton = Button(appContext).apply {
        text = "完成交互"
        visibility = View.GONE
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 24
        y = 120
    }

    private var attached = false

    init {
        container.addView(titleView)
        container.addView(statusView)
        container.addView(detailView)
        container.addView(interactionButton)
    }

    fun canShow(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(appContext)
    }

    fun show(): Boolean {
        if (!canShow()) return false
        runOnMain {
            if (attached) return@runOnMain
            runCatching {
                windowManager.addView(container, params)
                attached = true
            }.onFailure {
                attached = false
            }
        }
        return true
    }

    fun update(status: String, detail: String? = null) {
        runOnMain {
            statusView.text = status
            detailView.text = detail.orEmpty()
            detailView.visibility = if (detail.isNullOrBlank()) View.GONE else View.VISIBLE
            interactionButton.visibility = View.GONE
            interactionButton.setOnClickListener(null)
        }
    }

    fun waitForInteraction(message: String?, onDone: () -> Unit) {
        runOnMain {
            statusView.text = "等待用户交互"
            detailView.text = message.orEmpty()
            detailView.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
            interactionButton.visibility = View.VISIBLE
            interactionButton.setOnClickListener {
                interactionButton.visibility = View.GONE
                interactionButton.setOnClickListener(null)
                onDone()
            }
        }
    }

    fun hide() {
        runOnMain {
            if (!attached) return@runOnMain
            runCatching { windowManager.removeView(container) }
            attached = false
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
