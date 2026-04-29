package com.example.blueheartv.ui.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.GlowBlueShadow
import com.example.blueheartv.ui.theme.GlowCyanShadow
import com.example.blueheartv.ui.theme.GlowWhite

@Composable
fun DecorativeBackground(
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        Image(
            painter = painterResource(R.drawable.bg_abstract),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(h),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.7f),
                            Color.White.copy(alpha = 0.8f),
                        )
                    )
                )
        )

        val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(100.dp)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .offset(x = w * 0.05f, y = h * 0.4f)
                .size(w * 0.12f, w * 0.12f)
                .then(blurMod)
                .background(GlowCyanShadow, CircleShape),
        )

        Box(
            modifier = Modifier
                .offset(x = w * 0.46f, y = h * 0.015f)
                .size(w * 0.3f, w * 0.29f)
                .then(blurMod)
                .background(GlowBlueShadow, CircleShape),
        )

        Box(
            modifier = Modifier
                .offset(x = 0.dp, y = h * 0.85f)
                .size(w, w * 0.32f)
                .then(blurMod)
                .background(GlowWhite, CircleShape),
        )

        Box(
            modifier = Modifier
                .offset(x = w * 0.29f, y = h * 0.86f)
                .size(w * 0.45f, w * 0.42f)
                .then(blurMod)
                .background(GlowWhite, CircleShape),
        )
    }
}
