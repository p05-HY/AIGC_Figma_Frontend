package com.example.blueheartv.floating

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.edit
import com.example.blueheartv.R
import kotlin.math.abs

class FloatingBallView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onClick: () -> Unit,
) {
    companion object {
        private const val PREFS_NAME = "floating_ball_prefs"
        private const val KEY_X = "ball_x"
        private const val KEY_Y = "ball_y"
        private const val BALL_SIZE_DP = 52
        private const val CLICK_THRESHOLD_DP = 8
    }

    private val density = context.resources.displayMetrics.density
    private val ballSizePx = (BALL_SIZE_DP * density).toInt()
    private val clickThresholdPx = (CLICK_THRESHOLD_DP * density).toInt()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val screenWidth: Int
        get() = context.resources.displayMetrics.widthPixels
    private val screenHeight: Int
        get() = context.resources.displayMetrics.heightPixels

    private val ballView: FrameLayout = FrameLayout(context).apply {
        val imageView = ImageView(context).apply {
            setImageResource(R.drawable.ic_echo_face)
            scaleType = ImageView.ScaleType.CENTER_CROP
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        addView(imageView, FrameLayout.LayoutParams(ballSizePx, ballSizePx))
        setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        background = createBallDrawable()
    }

    private val layoutParams = WindowManager.LayoutParams(
        ballSizePx,
        ballSizePx,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt(KEY_X, screenWidth - ballSizePx)
        y = prefs.getInt(KEY_Y, (screenHeight * 0.4f).toInt())
    }

    private var isAdded = false

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        if (isAdded) return

        var startX = 0f
        var startY = 0f
        var startParamX = 0
        var startParamY = 0
        var totalMovement = 0f

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startParamX = layoutParams.x
                    startParamY = layoutParams.y
                    totalMovement = 0f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    totalMovement = maxOf(totalMovement, abs(dx) + abs(dy))
                    layoutParams.x = (startParamX + dx).toInt()
                    layoutParams.y = (startParamY + dy).toInt()
                    windowManager.updateViewLayout(ballView, layoutParams)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (totalMovement < clickThresholdPx) {
                        onClick()
                    } else {
                        snapToEdge()
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(ballView, layoutParams)
        isAdded = true
    }

    fun detach() {
        if (!isAdded) return
        runCatching { windowManager.removeView(ballView) }
        isAdded = false
    }

    fun setVisible(visible: Boolean) {
        ballView.visibility = if (visible) FrameLayout.VISIBLE else FrameLayout.GONE
    }

    private fun snapToEdge() {
        val centerX = layoutParams.x + ballSizePx / 2
        val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - ballSizePx

        ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { anim ->
                layoutParams.x = anim.animatedValue as Int
                runCatching { windowManager.updateViewLayout(ballView, layoutParams) }
            }
            start()
        }

        prefs.edit {
            putInt(KEY_X, targetX)
                .putInt(KEY_Y, layoutParams.y)
        }
    }

    private fun createBallDrawable(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0xFFFFFFFF.toInt())
            setStroke((1 * density).toInt(), 0xFFE2E8F0.toInt())
        }
    }
}
