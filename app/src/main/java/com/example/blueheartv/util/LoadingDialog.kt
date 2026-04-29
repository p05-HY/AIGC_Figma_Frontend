package com.example.blueheartv.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.theme.BlueAccent
import com.example.blueheartv.ui.theme.MutedText
import com.example.blueheartv.ui.theme.SurfaceWhite

// ============================================================
// Usage:
//   // 1. Place AppLoadingHost at the top level of your screen:
//   Box(modifier = Modifier.fillMaxSize()) {
//       // ... screen content ...
//       AppLoadingHost()
//   }
//
//   // 2. Show / dismiss:
//   LoadingDialog.show()               // default message "加载中..."
//   LoadingDialog.show("正在提交...")   // custom message
//   LoadingDialog.dismiss()
// ============================================================

object LoadingDialog {
    internal val _visible = mutableStateOf(false)
    internal val _message = mutableStateOf("加载中...")

    fun show(message: String = "加载中...") {
        _message.value = message
        _visible.value = true
    }

    fun dismiss() {
        _visible.value = false
    }
}

@Composable
fun AppLoadingHost() {
    val visible by LoadingDialog._visible
    val message by LoadingDialog._message

    DisposableEffect(Unit) {
        onDispose { LoadingDialog.dismiss() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LoadingSpinner()
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = MutedText,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingSpinner() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_spinner")

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "spinner_$index",
            )
            Box(
                modifier = Modifier
                    .size((10 * scale).dp)
                    .background(BlueAccent.copy(alpha = 0.4f + scale * 0.4f), CircleShape),
            )
        }
    }
}
