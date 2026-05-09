package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.blueheartv.R

/**
 * Figma source: Echo / 节点 462:1372 "背景渐变色"
 * 设计基准: 402×790 dp，圆角 40dp（顶部）
 *
 * 光晕 PNG 通过 Figma Images API 以 1x 导出，存放于 res/drawable-nodpi/。
 * 每张包含元素 + 完整阴影 (radius=100 + spread=100 = 各边 200dp)，
 * 需按导出全尺寸放置，居中对齐到元素在画布中的中心点。
 */
@Composable
fun DecorativeBackground(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
    ) {
        val w = maxWidth
        val h = maxHeight

        // Layer 1: 底层纹理图 (Figma: IMAGE fill)
        Image(
            painter = painterResource(R.drawable.bg_abstract),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(h),
            contentScale = ContentScale.Crop,
        )

        // Layer 2: 青色光晕 Ellipse 43 (462:1374)
        Image(
            painter = painterResource(R.drawable.bg_glow_cyan),
            contentDescription = null,
            modifier = Modifier
                .offset(x = w * (-179f / 402f), y = h * (121.2f / 790f))
                .size(width = w * (448f / 402f), height = h * (447f / 790f)),
            contentScale = ContentScale.FillBounds,
        )

        // Layer 3: 蓝色光晕 Ellipse 42 (462:1375)
        Image(
            painter = painterResource(R.drawable.bg_glow_blue),
            contentDescription = null,
            modifier = Modifier
                .offset(x = w * (-21f / 402f), y = h * (-188.3f / 790f))
                .size(width = w * (516f / 402f), height = h * (513.3f / 790f)),
            contentScale = ContentScale.FillBounds,
        )

        // Layer 4: 渐变蒙版 Rectangle 42
        // GRADIENT_LINEAR 垂直: 0%→transparent, 40.9%→white@0.70, 95.6%→white@0.80
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = 0.00f),
                        0.409f to Color.White.copy(alpha = 0.70f),
                        0.956f to Color.White.copy(alpha = 0.80f),
                        1.0f to Color.White.copy(alpha = 0.80f),
                    )
                )
            )
        )

        // Layer 5: 底部全宽白色光晕 Ellipse 44 (462:1377)
        Image(
            painter = painterResource(R.drawable.bg_glow_white_wide),
            contentDescription = null,
            modifier = Modifier
                .offset(x = w * (-200f / 402f), y = h * (478.4f / 790f))
                .size(width = w * (802f / 402f), height = h * (525.7f / 790f)),
            contentScale = ContentScale.FillBounds,
        )

        // Layer 6: 底部右侧白色光晕 Ellipse 45 (462:1378)
        Image(
            painter = painterResource(R.drawable.bg_glow_white_right),
            contentDescription = null,
            modifier = Modifier
                .offset(x = w * (-87f / 402f), y = h * (485.4f / 790f))
                .size(width = w * (576f / 402f), height = h * (563.6f / 790f)),
            contentScale = ContentScale.FillBounds,
        )
    }
}
