package com.example.blueheartv.util

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Duration.Companion.milliseconds

// ============================================================
// 顶部消息弹窗 — 毛玻璃卡片样式（来源：Figma node-id=462-647）
//
// Usage:
//   Box(modifier = Modifier.fillMaxSize()) {
//       // ... screen content ...
//       AppToastHost()
//   }
//
//   ToastUtil.show("您下午3点有产品评审会议", ToastType.INFO)
//   ToastUtil.show("配置已保存", ToastType.SUCCESS, durationMs = 3000)
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
            /* 来源：Figma node-id=462-647，顶部间距 40dp 原样复刻 */
            .padding(top = 40.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { -it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { -it },
        ) {
            currentEvent?.let { event ->
                ToastCard(event)
            }
        }
    }
}

@Composable
private fun ToastCard(event: ToastUtil.ToastEvent) {
    val cardShape = RoundedCornerShape(16.dp) /* 来源：Figma node-id=462-647，圆角 16dp */

    /* 来源：Figma node-id=462-647，卡片整体尺寸与外边距 */
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(84.dp)
            .shadow(
                elevation = 12.dp,
                shape = cardShape,
                ambientColor = BlueAccentLight.copy(alpha = 0.3f),
                spotColor = BlueAccentLight.copy(alpha = 0.4f),
            )
            .clip(cardShape)
    ) {
        /* 第1层：彩色渐变底色，70% 透明度（来源：Figma node-id=462-660） */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFD0E4FF),
                            Color(0xFFE8D0FF),
                            Color(0xFFD0F0FF),
                            Color(0xFFD0E4FF),
                        )
                    ),
                    alpha = 0.7f,
                )
        )

        /* 第2层：从透明到白色的垂直渐变覆盖（来源：Figma node-id=462-655） */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x00FFFFFF),
                            Color(0xFFFFFFFF),
                        )
                    ),
                    shape = cardShape,
                )
        )

        /* 第3层：左侧高光模糊条（来源：Figma node-id=462-667） */
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x66FFFFFF),
                            Color(0x00FFFFFF),
                        )
                    )
                )
        )

        /* 第4层：外描边 0.1px rgba(71,81,90,0.4)（来源：Figma node-id=462-653） */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 0.5.dp,
                    color = Slate700Stroke,
                    shape = cardShape,
                )
        )

        /* 第5层：蓝色内描边 1px rgba(63,133,255,0.2)（来源：Figma node-id=462-656） */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = ChipStroke,
                    shape = cardShape,
                )
        )

        /* 内容层：头像 + 文字 */
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            /* 头像：44dp，蓝色阴影（来源：Figma node-id=462-671，shadow -10px 10px 20px #80a3e5） */
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = BlueAccentLight,
                        spotColor = BlueAccentLight,
                    )
                    .size(44.dp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_echo_face),
                    contentDescription = "Echo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            /* 文字区域（来源：Figma node-id=462-668，gap 4dp） */
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                /* 标题：Bold 14sp 黑色（来源：Figma node-id=462-669） */
                Text(
                    text = "Echo",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextBlack,
                    lineHeight = 20.sp,
                    maxLines = 1,
                )
                /* 正文：Regular 12sp rgba(60,62,64,0.98)（来源：Figma node-id=462-670） */
                Text(
                    text = event.message,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFA3C3E40),
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
