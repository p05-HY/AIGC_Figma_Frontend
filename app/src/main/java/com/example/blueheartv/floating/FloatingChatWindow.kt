package com.example.blueheartv.floating

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.blueheartv.MainActivity
import com.example.blueheartv.R
import com.example.blueheartv.model.Message
import com.example.blueheartv.ui.components.AiBubble
import com.example.blueheartv.ui.components.LoadingDots
import com.example.blueheartv.ui.components.UserBubble
import com.example.blueheartv.ui.theme.*
import com.example.blueheartv.viewmodel.ChatSessionState
import com.example.blueheartv.viewmodel.ChatViewModel
import com.example.blueheartv.viewmodel.HomeUiState
import org.koin.java.KoinJavaComponent.get

class FloatingChatWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onClose: () -> Unit,
    private val onExpandToFull: (sessionId: String?) -> Unit,
) {
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private val screenHeight = context.resources.displayMetrics.heightPixels
    private val windowWidth = (screenWidth * 0.85f).toInt()
    private val windowHeight = (screenHeight * 0.50f).toInt()

    private var composeView: ComposeView? = null
    private var lifecycleOwner: WindowLifecycleOwner? = null
    private var isAdded = false

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

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)

            setContent {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    FloatingChatContent(
                        viewModel = chatViewModel,
                        onClose = { dismiss() },
                        onExpand = {
                            val sessionId = chatViewModel.uiState.value.let { state ->
                                state.histories.firstOrNull { it.isCurrent }?.id
                            }
                            dismiss()
                            onExpandToFull(sessionId)
                        },
                    )
                }
            }
        }

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e1.y - e2.y > 150 && Math.abs(velocityY) > 300) {
                    val sessionId = chatViewModel.uiState.value.let { state ->
                        state.histories.firstOrNull { it.isCurrent }?.id
                    }
                    dismiss()
                    onExpandToFull(sessionId)
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
        windowManager.addView(view, layoutParams)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        isAdded = true
    }

    fun dismiss() {
        if (!isAdded) return
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        runCatching { composeView?.let { windowManager.removeView(it) } }
        composeView = null
        lifecycleOwner = null
        isAdded = false
        onClose()
    }

    val isShowing get() = isAdded
}

@Composable
private fun FloatingChatContent(
    viewModel: ChatViewModel,
    onClose: () -> Unit,
    onExpand: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content?.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.scrollToItem(uiState.messages.lastIndex)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        shadowElevation = 16.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BlueAccent),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "超级蓝心小V",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onExpand, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "Expand", tint = MutedText, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = MutedText, modifier = Modifier.size(18.dp))
                }
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("有什么可以帮你的？", fontSize = 14.sp, color = MutedText)
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

            // Input
            MiniInputBar(
                value = uiState.inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                onSend = { viewModel.sendMessage() },
                sendEnabled = uiState.sessionState != ChatSessionState.RESPONDING,
            )
        }
    }
}

@Composable
private fun MiniInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .background(SurfaceWhite, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text("发消息...", fontSize = 13.sp, color = GrayText)
            }
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 2000) onValueChange(it) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp, color = TextBlack),
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_send_arrow),
            contentDescription = "Send",
            modifier = Modifier
                .size(20.dp)
                .then(if (sendEnabled) Modifier.clickable { onSend() } else Modifier),
        )
    }
}

private class WindowLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStore0 = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    init {
        savedStateController.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStore0
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
        if (event == Lifecycle.Event.ON_DESTROY) {
            viewModelStore0.clear()
        }
    }
}
