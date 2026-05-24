package com.example.blueheartv.floating

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.blueheartv.R

class FloatingBubbleInput(
    private val context: Context,
    private val windowManager: WindowManager,
    private val ballLayoutParams: WindowManager.LayoutParams,
    private val onSend: (String) -> Unit,
    private val onBackPressed: (() -> Unit)? = null,
    private val onAttachClick: (() -> Unit)? = null,
    private val onMicClick: (() -> Unit)? = null,
) {
    companion object {
        private const val TAG = "FloatingBubbleInput"
        private const val WIDTH_DP = 232
        private const val HEIGHT_DP = 39
        private const val CORNER_RADIUS_DP = 16
        private const val MAX_INPUT_LENGTH = 2000
    }

    private val density = context.resources.displayMetrics.density
    private val widthPx = (WIDTH_DP * density).toInt()
    private val heightPx = (HEIGHT_DP * density).toInt()
    private val ballSizePx = (52 * density).toInt()

    private var containerView: FrameLayout? = null
    private var editText: EditText? = null
    private var sendButton: ImageView? = null
    private var isAdded = false
    private var currentAnimator: ValueAnimator? = null
    private var inputEnabled = true

    private val layoutParams = WindowManager.LayoutParams(
        widthPx,
        heightPx,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isAdded) return

        val container = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (super.dispatchKeyEvent(event)) return true
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onBackPressed?.invoke()
                    return true
                }
                return false
            }
        }
        container.background = createGlassBackground()

        val innerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = (10 * density).toInt()
            setPadding(hPad, 0, hPad, 0)
        }

        val attachIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_attachment)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(0xFF6D6D6D.toInt())
            setOnClickListener { onAttachClick?.invoke() }
        }
        val attachSize = (14 * density).toInt()
        innerLayout.addView(attachIcon, LinearLayout.LayoutParams(attachSize, attachSize))

        val spacer1 = android.view.View(context)
        innerLayout.addView(spacer1, LinearLayout.LayoutParams((8 * density).toInt(), 0))

        val input = EditText(context).apply {
            hint = context.getString(R.string.floating_chat_input_hint)
            setHintTextColor(0xFF6D6D6D.toInt())
            setTextColor(0xFF000000.toInt())
            textSize = 14f
            background = null
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(MAX_INPUT_LENGTH))
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND && inputEnabled) {
                    val text = text?.toString()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        onSend(text)
                    }
                    true
                } else false
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonAlpha()
            }
        })
        editText = input
        innerLayout.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val spacer2 = android.view.View(context)
        innerLayout.addView(spacer2, LinearLayout.LayoutParams((8 * density).toInt(), 0))

        val micIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_mic)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(0xFF6D6D6D.toInt())
            setOnClickListener { onMicClick?.invoke() }
        }
        val micSize = (14 * density).toInt()
        innerLayout.addView(micIcon, LinearLayout.LayoutParams(micSize, micSize))

        val spacerMic = android.view.View(context)
        innerLayout.addView(spacerMic, LinearLayout.LayoutParams((8 * density).toInt(), 0))

        val sendIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_send_arrow)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0.5f
            setOnClickListener {
                if (!inputEnabled) return@setOnClickListener
                val text = editText?.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    onSend(text)
                }
            }
        }
        sendButton = sendIcon
        val sendSize = (20 * density).toInt()
        innerLayout.addView(sendIcon, LinearLayout.LayoutParams(sendSize, sendSize))

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
            Log.e(TAG, "Failed to add bubble input", e)
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
        currentAnimator?.cancel()

        val view = containerView ?: return
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
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    removeView()
                }
            })
            start()
        }
    }

    fun setInputEnabled(enabled: Boolean) {
        inputEnabled = enabled
        editText?.isEnabled = enabled
        updateSendButtonAlpha()
    }

    fun getInputText(): String = editText?.text?.toString()?.trim().orEmpty()

    private fun removeView() {
        try {
            containerView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove bubble input view", e)
        }
        containerView = null
        editText = null
        sendButton = null
        currentAnimator = null
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

    private fun updateSendButtonAlpha() {
        val hasText = editText?.text?.toString()?.trim()?.isNotEmpty() == true
        sendButton?.alpha = if (hasText && inputEnabled) 1f else 0.5f
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
