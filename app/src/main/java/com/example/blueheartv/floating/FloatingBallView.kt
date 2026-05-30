package com.example.blueheartv.floating

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.blueheartv.R
import com.example.blueheartv.voice.VoiceRecordingState
import kotlin.math.abs

class FloatingBallView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onSingleClick: () -> Unit,
    private val onDoubleClick: (() -> Unit)? = null,
    private val onTripleClick: (() -> Unit)? = null,
    private val onLongPress: (() -> Unit)? = null,
    private val onLongPressRelease: (() -> Unit)? = null,
    private val onLongPressDragCancel: (() -> Unit)? = null,
) {
    companion object {
        private const val TAG = "FloatingBallView"
        private const val PREFS_NAME = "floating_ball_prefs"
        private const val KEY_X = "ball_x"
        private const val KEY_Y = "ball_y"
        private const val BALL_SIZE_DP = 52
        private const val CLICK_THRESHOLD_DP = 8
        private const val MULTI_TAP_TIMEOUT_MS = 300L
        private const val MAX_TAP_COUNT = 3
        private const val VISUAL_RESET_DELAY_MS = 1200L
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
        contentDescription = context.getString(R.string.floating_ball_desc)
        val imageView = ImageView(context).apply {
            setImageResource(R.drawable.ic_echo_face)
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        addView(imageView, FrameLayout.LayoutParams(ballSizePx, ballSizePx))
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
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val tapHandler = Handler(Looper.getMainLooper())
    private var tapCount = 0
    private var pendingTapRunnable: Runnable? = null
    private var recordingAnimator: ValueAnimator? = null
    private var visualResetRunnable: Runnable? = null
    var onPositionChanged: ((x: Int, y: Int) -> Unit)? = null

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        if (isAdded) return

        var startX = 0f
        var startY = 0f
        var startParamX = 0
        var startParamY = 0
        var totalMovement = 0f
        var longPressTriggered = false
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        val longPressRunnable = Runnable {
            if (totalMovement < clickThresholdPx) {
                longPressTriggered = true
                ballView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onLongPress?.invoke()
            }
        }

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startParamX = layoutParams.x
                    startParamY = layoutParams.y
                    totalMovement = 0f
                    longPressTriggered = false
                    longPressHandler.postDelayed(longPressRunnable, longPressTimeout)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    totalMovement = maxOf(totalMovement, abs(dx) + abs(dy))
                    if (totalMovement >= clickThresholdPx) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        resetTaps()
                        if (longPressTriggered) {
                            longPressTriggered = false
                            onLongPressDragCancel?.invoke()
                        }
                    }
                    layoutParams.x = (startParamX + dx).toInt()
                    layoutParams.y = (startParamY + dy).toInt()
                        .coerceIn(0, screenHeight - ballSizePx)
                    windowManager.updateViewLayout(ballView, layoutParams)
                    onPositionChanged?.invoke(layoutParams.x, layoutParams.y)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (longPressTriggered) {
                        onLongPressRelease?.invoke()
                    } else if (totalMovement < clickThresholdPx) {
                        registerTap()
                    } else {
                        snapToEdge()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    resetTaps()
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
        resetTaps()
        resetBallVisual()
        longPressHandler.removeCallbacksAndMessages(null)
        try {
            windowManager.removeView(ballView)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove ball view", e)
        }
        isAdded = false
    }

    fun setVisible(visible: Boolean) {
        ballView.visibility = if (visible) FrameLayout.VISIBLE else FrameLayout.GONE
    }

    fun getLayoutParams(): WindowManager.LayoutParams = layoutParams

    fun setRecordingState(state: VoiceRecordingState) {
        recordingAnimator?.cancel()
        recordingAnimator = null
        visualResetRunnable?.let { longPressHandler.removeCallbacks(it) }
        visualResetRunnable = null

        when (state) {
            VoiceRecordingState.RECORDING -> {
                val bg = ballView.background as? GradientDrawable ?: return
                bg.setStroke(
                    (2 * density).toInt(),
                    ContextCompat.getColor(context, R.color.blue_accent),
                )
                recordingAnimator = ValueAnimator.ofFloat(1f, 1.15f).apply {
                    duration = 600
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { anim ->
                        val scale = anim.animatedValue as Float
                        ballView.scaleX = scale
                        ballView.scaleY = scale
                    }
                    start()
                }
            }

            VoiceRecordingState.RECOGNIZING -> {
                ballView.scaleX = 1f
                ballView.scaleY = 1f
                val bg = ballView.background as? GradientDrawable ?: return
                bg.setStroke(
                    (2 * density).toInt(),
                    ContextCompat.getColor(context, R.color.blue_accent),
                )
                bg.setColor(AndroidColor.parseColor("#F0F4FF"))
            }

            VoiceRecordingState.SUCCESS -> {
                ballView.scaleX = 1f
                ballView.scaleY = 1f
                val bg = ballView.background as? GradientDrawable ?: return
                bg.setStroke((2 * density).toInt(), AndroidColor.parseColor("#4CAF50"))
                bg.setColor(ContextCompat.getColor(context, R.color.surface_white))
                scheduleVisualReset()
            }

            VoiceRecordingState.FAILED -> {
                ballView.scaleX = 1f
                ballView.scaleY = 1f
                val bg = ballView.background as? GradientDrawable ?: return
                bg.setStroke((2 * density).toInt(), AndroidColor.parseColor("#FF6B6B"))
                bg.setColor(ContextCompat.getColor(context, R.color.surface_white))
                scheduleVisualReset()
            }

            else -> resetBallVisual()
        }
    }

    private fun scheduleVisualReset() {
        val runnable = Runnable { resetBallVisual() }
        visualResetRunnable = runnable
        longPressHandler.postDelayed(runnable, VISUAL_RESET_DELAY_MS)
    }

    private fun resetBallVisual() {
        recordingAnimator?.cancel()
        recordingAnimator = null
        ballView.scaleX = 1f
        ballView.scaleY = 1f
        ballView.background = createBallDrawable()
    }

    private fun snapToEdge() {
        val centerX = layoutParams.x + ballSizePx / 2
        val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - ballSizePx
        val clampedY = layoutParams.y.coerceIn(0, screenHeight - ballSizePx)
        layoutParams.y = clampedY

        ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { anim ->
                layoutParams.x = anim.animatedValue as Int
                try {
                    windowManager.updateViewLayout(ballView, layoutParams)
                    onPositionChanged?.invoke(layoutParams.x, layoutParams.y)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update layout during snap", e)
                }
            }
            start()
        }

        prefs.edit {
            putInt(KEY_X, targetX)
                .putInt(KEY_Y, clampedY)
        }
    }

    private fun registerTap() {
        pendingTapRunnable?.let { tapHandler.removeCallbacks(it) }
        pendingTapRunnable = null
        tapCount++
        if (tapCount >= MAX_TAP_COUNT) {
            commitTaps()
            return
        }
        val runnable = Runnable { commitTaps() }
        pendingTapRunnable = runnable
        tapHandler.postDelayed(runnable, MULTI_TAP_TIMEOUT_MS)
    }

    private fun commitTaps() {
        val count = tapCount
        tapCount = 0
        pendingTapRunnable = null
        when (count) {
            1 -> onSingleClick()
            2 -> onDoubleClick?.invoke() ?: onSingleClick()
            3 -> onTripleClick?.invoke()
        }
    }

    private fun resetTaps() {
        pendingTapRunnable?.let { tapHandler.removeCallbacks(it) }
        pendingTapRunnable = null
        tapCount = 0
    }

    private fun createBallDrawable(): android.graphics.drawable.Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.surface_white))
            setStroke((1 * density).toInt(), ContextCompat.getColor(context, R.color.divider))
        }
    }
}
