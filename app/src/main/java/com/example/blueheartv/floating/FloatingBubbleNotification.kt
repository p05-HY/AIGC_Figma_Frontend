package com.example.blueheartv.floating

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.blueheartv.R

class FloatingBubbleNotification(
    private val context: Context,
    private val windowManager: WindowManager,
    private val ballLayoutParams: WindowManager.LayoutParams,
    private val onDismiss: () -> Unit,
) {
    companion object {
        private const val TAG = "FloatingBubbleNotif"
        private const val WIDTH_DP = 120
        private const val HEIGHT_DP = 36
        private const val CORNER_RADIUS_DP = 16
        private const val AUTO_DISMISS_MS = 3000L
    }

    private val density = context.resources.displayMetrics.density
    private val widthPx = (WIDTH_DP * density).toInt()
    private val heightPx = (HEIGHT_DP * density).toInt()
    private val ballSizePx = (52 * density).toInt()
    private val handler = Handler(Looper.getMainLooper())

    private var containerView: FrameLayout? = null
    private var isAdded = false
    private var currentAnimator: ValueAnimator? = null

    private val layoutParams = WindowManager.LayoutParams(
        widthPx,
        heightPx,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private val autoDismissRunnable = Runnable { dismiss() }

    fun show() {
        if (isAdded) return

        val container = FrameLayout(context)
        container.background = createGlassBackground()

        val innerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val leftPad = (12 * density).toInt()
            val rightPad = (6 * density).toInt()
            setPadding(leftPad, 0, rightPad, 0)
        }

        val textView = TextView(context).apply {
            text = context.getString(R.string.floating_task_completed)
            setTextColor(0xFF3C3E40.toInt())
            textSize = 14f
            isSingleLine = true
        }
        innerLayout.addView(textView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val spacer = android.view.View(context)
        innerLayout.addView(spacer, LinearLayout.LayoutParams((6 * density).toInt(), 0))

        val echoLogo = ImageView(context).apply {
            setImageResource(R.drawable.ic_echo_face)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        val logoSize = (27 * density).toInt()
        innerLayout.addView(echoLogo, LinearLayout.LayoutParams(logoSize, logoSize))

        container.addView(innerLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        containerView = container
        updatePosition()
        layoutParams.alpha = 0f

        try {
            windowManager.addView(container, layoutParams)
            isAdded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add notification bubble", e)
            return
        }

        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            addUpdateListener { anim ->
                if (!isAdded) return@addUpdateListener
                layoutParams.alpha = anim.animatedValue as Float
                try {
                    windowManager.updateViewLayout(container, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update layout during fade-in", e)
                }
            }
            start()
        }

        handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
    }

    fun syncPosition() {
        if (!isAdded) return
        updatePosition()
        try {
            containerView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync position", e)
        }
    }

    fun dismiss() {
        if (!isAdded) return
        isAdded = false
        handler.removeCallbacks(autoDismissRunnable)
        currentAnimator?.cancel()

        val view = containerView ?: run {
            onDismiss()
            return
        }

        currentAnimator = ValueAnimator.ofFloat(layoutParams.alpha, 0f).apply {
            duration = 150
            addUpdateListener { anim ->
                layoutParams.alpha = anim.animatedValue as Float
                try {
                    windowManager.updateViewLayout(view, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update layout during fade-out", e)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    removeView()
                }
            })
            start()
        }
    }

    private fun removeView() {
        try {
            containerView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove notification view", e)
        }
        containerView = null
        currentAnimator = null
        onDismiss()
    }

    private fun updatePosition() {
        val ballCenterY = ballLayoutParams.y + ballSizePx / 2
        layoutParams.y = ballCenterY - heightPx / 2
        val gap = (8 * density).toInt()
        layoutParams.x = ballLayoutParams.x - widthPx - gap
        if (layoutParams.x < 0) {
            layoutParams.x = ballLayoutParams.x + ballSizePx + gap
        }
    }

    private fun createGlassBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = CORNER_RADIUS_DP * density
            setColor(0x99F8FAFC.toInt())
            setStroke((1 * density).toInt(), 0x333F85FF)
        }
    }

    val isShowing get() = isAdded
}
