package com.example.blueheartv.util

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.theme.BlueAccent
import com.example.blueheartv.ui.theme.SurfaceWhite
import com.example.blueheartv.ui.theme.TextBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Duration.Companion.milliseconds

// ============================================================
// Usage:
//   // 1. Place AppToastHost at the top level of your screen:
//   Box(modifier = Modifier.fillMaxSize()) {
//       // ... screen content ...
//       AppToastHost()
//   }
//
//   // 2. Show a toast from anywhere with a coroutine scope:
//   ToastUtil.show("操作成功", ToastType.SUCCESS)
//   ToastUtil.show("网络异常", ToastType.ERROR, durationMs = 3000)
// ============================================================

enum class ToastType {
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
}

object ToastUtil {
    internal data class ToastEvent(
        val message: String,
        val type: ToastType,
        val durationMs: Long,
    )

    private val _events = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 5)
    internal val events = _events.asSharedFlow()

    fun show(
        message: String,
        type: ToastType = ToastType.INFO,
        durationMs: Long = 2500,
    ) {
        _events.tryEmit(ToastEvent(message, type, durationMs))
    }
}

@Composable
fun AppToastHost(
    modifier: Modifier = Modifier,
) {
    var currentEvent by remember { mutableStateOf<ToastUtil.ToastEvent?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ToastUtil.events.collect { event ->
            currentEvent = event
            visible = true
            delay(event.durationMs.milliseconds)
            visible = false
            delay(300.milliseconds)
            currentEvent = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { -it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { -it },
        ) {
            currentEvent?.let { event ->
                val (icon, tintColor) = event.type.iconAndColor()

                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = tintColor,
                    )
                    Text(
                        text = event.message,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextBlack,
                    )
                }
            }
        }
    }
}

private fun ToastType.iconAndColor(): Pair<ImageVector, Color> {
    return when (this) {
        ToastType.SUCCESS -> Icons.Outlined.CheckCircle to Color(0xFF22C55E)
        ToastType.WARNING -> Icons.Outlined.Warning to Color(0xFFF59E0B)
        ToastType.ERROR -> Icons.Outlined.Error to Color(0xFFEF4444)
        ToastType.INFO -> Icons.Outlined.Info to BlueAccent
    }
}
