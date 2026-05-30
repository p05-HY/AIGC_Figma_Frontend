package com.example.blueheartv.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.blueheartv.R
import com.example.blueheartv.ui.components.AiBubble
import com.example.blueheartv.ui.components.UserBubble
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.viewmodel.ChatSessionState
import com.example.blueheartv.viewmodel.ChatViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.java.KoinJavaComponent.get
import kotlin.math.abs

/* ─── Figma Design Tokens (node-id=462:458) ─── */
private val GlassFillColor = Color(0x99F8FAFC)           // rgba(248,250,252,0.6)
private val GlassStrokeOuter = Color(0x6647515A)         // rgba(71,81,90,0.4) 外描边
private val GlassStrokeBlue = Color(0x333F85FF)          // rgba(63,133,255,0.2) 蓝色高光描边
private val GlassGradientTop = Color(0x00FFFFFF)         // 渐变起始：透明白
private val GlassGradientBottom = Color(0xFFFFFFFF)      // 渐变终止：纯白
private val GlassWhiteOverlay70 = Color(0xB3FFFFFF)      // rgba(255,255,255,0.7)
private val GlassWhiteOverlay80 = Color(0xCCFFFFFF)      // rgba(255,255,255,0.8)
private val TitleBlue = Color(0xFF80A3E5)                // #80A3E5 Echo标题色
private val PlaceholderGray = Color(0xFF6D6D6D)          // #6D6D6D 输入框占位文字
private val GlassCardRadius = 16.dp

class FloatingChatWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onClose: () -> Unit,
    private val onExpandToFull: (sessionId: String?) -> Unit,
    private val onAttachClick: (() -> Unit)? = null,
    private val onMicClick: (() -> Unit)? = null,
) {
    companion object {
        private const val TAG = "FloatingChatWindow"
    }

    private val density = context.resources.displayMetrics.density
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private val screenHeight = context.resources.displayMetrics.heightPixels
    // Figma: 245×375 aspect ratio → scale to 75% of screen width
    private val windowWidth = (screenWidth * 0.68f).toInt()
    private val windowHeight = (windowWidth * (375f / 245f)).toInt()

    private var composeView: android.view.View? = null
    private var lifecycleOwner: WindowLifecycleOwner? = null
    private var isAdded = false
    private var currentAnimator: android.animation.ValueAnimator? = null
    private var cleanedUp = false

    private val layoutParams = WindowManager.LayoutParams(
        windowWidth,
        windowHeight,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.CENTER
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isAdded) return

        val owner = WindowLifecycleOwner()
        lifecycleOwner = owner
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val chatViewModel: ChatViewModel = get(ChatViewModel::class.java)

        fun expandToFullScreen() {
            val sessionId = chatViewModel.uiState.value.histories
                .firstOrNull { it.isCurrent }?.id
            dismiss()
            onExpandToFull(sessionId)
        }

        val wrapper = object : android.widget.FrameLayout(context) {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                if (super.dispatchKeyEvent(event)) return true
                if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                    dismiss()
                    return true
                }
                return false
            }
        }

        wrapper.setViewTreeLifecycleOwner(owner)
        wrapper.setViewTreeViewModelStoreOwner(owner)
        wrapper.setViewTreeSavedStateRegistryOwner(owner)

        val composeContent = ComposeView(context).apply {
            setContent {
                MaterialTheme(colorScheme = AppLightColorScheme) {
                    FloatingChatContent(
                        viewModel = chatViewModel,
                        onClose = { dismiss() },
                        onExpand = { expandToFullScreen() },
                        onAttachClick = { onAttachClick?.invoke() },
                        onMicClick = { onMicClick?.invoke() },
                    )
                }
            }
        }

        wrapper.addView(composeContent, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        val view = wrapper

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e1.y - e2.y > 150 && abs(velocityY) > 300) {
                    expandToFullScreen()
                    return true
                }
                return false
            }
        })

        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        composeView = view
        layoutParams.alpha = 0f
        windowManager.addView(view, layoutParams)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        isAdded = true
        cleanedUp = false

        currentAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            addUpdateListener { anim ->
                if (!isAdded) return@addUpdateListener
                layoutParams.alpha = anim.animatedValue as Float
                try {
                    windowManager.updateViewLayout(view, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update layout during fade-in", e)
                }
            }
            start()
        }
    }

    fun dismiss() {
        if (!isAdded) return
        isAdded = false
        currentAnimator?.cancel()
        currentAnimator = null

        val view = composeView
        if (view != null) {
            currentAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
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
                        cleanupViews()
                    }
                })
                start()
            }
        } else {
            cleanupViews()
        }
    }

    private fun cleanupViews() {
        if (cleanedUp) return
        cleanedUp = true
        try {
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        } catch (e: Exception) {
            Log.e(TAG, "Error during lifecycle teardown", e)
        }
        try {
            composeView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove chat window view", e)
        }
        composeView = null
        lifecycleOwner = null
        currentAnimator = null
        onClose()
    }

    val isShowing get() = isAdded
}

/* ─── Glassmorphism Card Background (来源：Figma node-id=462:460, 毛玻璃卡片) ─── */
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(GlassCardRadius))
            // Layer 1: 半透明填充 (Figma node-id=462:462)
            .background(GlassFillColor)
            // Layer 2: 内部渐变 (Figma node-id=462:463)
            .background(
                Brush.verticalGradient(
                    colors = listOf(GlassGradientTop, GlassGradientBottom),
                )
            )
            // Layer 3: 白色覆盖渐变模拟光泽 (Figma node-id=462:471)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.41f to GlassWhiteOverlay70,
                        0.96f to GlassWhiteOverlay80,
                    ),
                )
            )
            // Layer 4: 蓝色高光描边 (Figma node-id=462:464)
            .border(1.dp, GlassStrokeBlue, RoundedCornerShape(GlassCardRadius))
            // Layer 5: 外部描边 (Figma node-id=462:461)
            .border(0.5.dp, GlassStrokeOuter, RoundedCornerShape(GlassCardRadius)),
    ) {
        content()
    }
}

/* ─── Main Floating Chat Content (来源：Figma node-id=462:458) ─── */
@Composable
private fun FloatingChatContent(
    viewModel: ChatViewModel,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    onAttachClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        snapshotFlow { uiState.messages.size to (uiState.messages.lastOrNull()?.content?.length ?: 0) }
            .debounce(100)
            .distinctUntilChanged()
            .collectLatest {
                if (uiState.messages.isNotEmpty()) {
                    listState.animateScrollToItem(uiState.messages.lastIndex)
                }
            }
    }

    GlassCard(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            /* ─── 标题栏 (来源：Figma node-id=462:487, 462:481, 462:486) ─── */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp, start = 21.dp, end = 16.dp),
            ) {
                // 左侧全屏展开按钮 (Figma node-id=462:481, 16dp)
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = stringResource(R.string.floating_expand),
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.CenterStart)
                        .clickable { onExpand() },
                    tint = DarkPrimary,
                )
                // 居中标题 "Echo" (Figma node-id=462:488, 16sp Medium #80A3E5)
                Text(
                    text = "Echo",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TitleBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
                // 右侧关闭/展开 (Figma node-id=462:486, 12dp)
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.floating_close),
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.CenterEnd)
                        .clickable { onClose() },
                    tint = MutedText,
                )
            }

            /* ─── 顶部分割线 (来源：Figma node-id=462:474) ─── */
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DividerColor),
            )

            /* ─── 消息列表区域 ─── */
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.floating_chat_empty),
                                fontSize = 14.sp,
                                color = MutedText,
                            )
                        }
                    }
                }
                items(uiState.messages, key = { it.id }) { message ->
                    if (message.isUser) {
                        UserBubble(message = message)
                    } else {
                        AiBubble(message = message)
                    }
                }
            }

            /* ─── 底部输入栏 (来源：Figma node-id=462:489) ─── */
            GlassInputBar(
                value = uiState.inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                onSend = { viewModel.sendMessage() },
                onExpand = onExpand,
                onAttachClick = onAttachClick,
                onMicClick = onMicClick,
                sendEnabled = uiState.sessionState != ChatSessionState.RESPONDING
                        && uiState.inputText.isNotBlank(),
            )
        }
    }
}

/* ─── 毛玻璃输入栏 (来源：Figma node-id=462:489~462:503) ─── */
@Composable
private fun GlassInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onExpand: () -> Unit,
    onAttachClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    sendEnabled: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 8.dp),
    ) {
        // 输入栏毛玻璃背景 (Figma: 232×38.8dp, 16dp radius, 同样的毛玻璃效果)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(39.dp)
                .clip(RoundedCornerShape(GlassCardRadius))
                .background(GlassFillColor)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GlassGradientTop, GlassGradientBottom),
                    )
                )
                .border(1.dp, GlassStrokeBlue, RoundedCornerShape(GlassCardRadius))
                .border(0.5.dp, GlassStrokeOuter, RoundedCornerShape(GlassCardRadius))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 附件图标 (Figma node-id=462:502, ~14dp)
                Icon(
                    painter = painterResource(R.drawable.ic_attachment),
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onAttachClick() },
                    tint = MutedText,
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 输入框
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.floating_chat_input_hint),
                            fontSize = 14.sp,
                            color = PlaceholderGray,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { if (it.length <= 2000) onValueChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 14.sp, color = TextBlack),
                        singleLine = true,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 麦克风图标
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = "Voice input",
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onMicClick() },
                    tint = MutedText,
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 右侧发送/AI头像 (Figma node-id=462:503, 27dp 圆形)
                Image(
                    painter = painterResource(R.drawable.ic_echo_face),
                    contentDescription = stringResource(R.string.floating_send),
                    modifier = Modifier
                        .size(27.dp)
                        .clip(CircleShape)
                        .alpha(if (sendEnabled) 1f else 0.5f)
                        .then(if (sendEnabled) Modifier.clickable { onSend() } else Modifier),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

// Required by ComposeView — provides lifecycle, ViewModelStore, and SavedStateRegistry in a non-Activity context.
// Note: ChatViewModel is Koin-managed (global singleton), so it is NOT scoped to this store.
private class WindowLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    init {
        savedStateController.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
        if (event == Lifecycle.Event.ON_DESTROY) {
            store.clear()
        }
    }
}
