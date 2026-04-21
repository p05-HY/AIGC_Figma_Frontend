package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.*

@Composable
fun DecorativeBackground(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background abstract image
        Image(
            painter = painterResource(R.drawable.bg_abstract),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(790.dp),
            contentScale = ContentScale.Crop,
        )

        // Semi-transparent white overlay at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(790.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.7f),
                            Color.White.copy(alpha = 0.8f),
                        )
                    )
                )
        )

        // Cyan glow ellipse (top-left area)
        Box(
            modifier = Modifier
                .offset(x = 21.dp, y = 322.dp)
                .size(48.dp, 47.dp)
                .blur(100.dp)
                .background(GlowCyanShadow, CircleShape),
        )

        // Blue glow ellipse (top-right area)
        Box(
            modifier = Modifier
                .offset(x = 179.dp, y = 12.dp)
                .size(116.dp, 114.dp)
                .blur(100.dp)
                .background(GlowBlueShadow, CircleShape),
        )

        // White glow at bottom
        Box(
            modifier = Modifier
                .offset(x = 0.dp, y = 680.dp)
                .size(402.dp, 126.dp)
                .blur(100.dp)
                .background(GlowWhite, CircleShape),
        )

        // Second white glow
        Box(
            modifier = Modifier
                .offset(x = 113.dp, y = 687.dp)
                .size(176.dp, 164.dp)
                .blur(100.dp)
                .background(GlowWhite, CircleShape),
        )
    }
}
