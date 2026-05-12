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

@Composable
fun DecorativeBackground(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
    ) {
        val w = maxWidth
        val h = maxHeight
        val designRatio = 402f / 790f
        val actualRatio = w / h
        val widthScale = (actualRatio / designRatio).coerceIn(0.8f, 1.5f)

        Image(
            painter = painterResource(R.drawable.bg_abstract),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(h),
            contentScale = ContentScale.Crop,
        )

        Image(
            painter = painterResource(R.drawable.bg_glow_cyan),
            contentDescription = null,
            modifier = Modifier
                .offset(x = w * (-179f / 402f * widthScale), y = h * (121.2f / 790f))
                .size(width = w * (448f / 402f * widthScale), height = h * (447f / 790f)),
            contentScale = ContentScale.FillBounds,
        )

        Image(
            painter = painterResource(R.drawable.bg_glow_blue),
            contentDescription = null,
            modifier = Modifier
                .offset(x = w * (-21f / 402f * widthScale), y = h * (-188.3f / 790f))
                .size(width = w * (516f / 402f * widthScale), height = h * (513.3f / 790f)),
            contentScale = ContentScale.FillBounds,
        )

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

        Image(
            painter = painterResource(R.drawable.bg_glow_white_wide),
            contentDescription = null,
            modifier = Modifier
                .offset(x = w * (-200f / 402f * widthScale), y = h * (478.4f / 790f))
                .size(width = w * (802f / 402f * widthScale), height = h * (525.7f / 790f)),
            contentScale = ContentScale.FillBounds,
        )

        Image(
            painter = painterResource(R.drawable.bg_glow_white_right),
            contentDescription = null,
            modifier = Modifier
                .offset(x = w * (-87f / 402f * widthScale), y = h * (485.4f / 790f))
                .size(width = w * (576f / 402f * widthScale), height = h * (563.6f / 790f)),
            contentScale = ContentScale.FillBounds,
        )
    }
}
