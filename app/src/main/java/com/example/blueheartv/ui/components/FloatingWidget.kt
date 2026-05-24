package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.SurfaceWhite
import kotlin.math.roundToInt

@Composable
fun FloatingWidget(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val widgetSizePx = with(density) { 56.dp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        var offset by remember(containerWidthPx, containerHeightPx) {
            val maxX = (containerWidthPx - widgetSizePx).coerceAtLeast(0f)
            val maxY = (containerHeightPx - widgetSizePx).coerceAtLeast(0f)
            mutableStateOf(
                Offset(
                    x = (maxX - marginPx).coerceIn(0f, maxX),
                    y = (containerHeightPx * 0.7f).coerceIn(0f, maxY),
                )
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .pointerInput(containerWidthPx, containerHeightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset = Offset(
                            x = (offset.x + dragAmount.x).coerceIn(0f, (containerWidthPx - widgetSizePx).coerceAtLeast(0f)),
                            y = (offset.y + dragAmount.y).coerceIn(0f, (containerHeightPx - widgetSizePx).coerceAtLeast(0f)),
                        )
                    }
                },
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = Color.Red, // 强制红色
                shadowElevation = 8.dp,
                onClick = onClick,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceWhite, CircleShape),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_echo_face),
                        contentDescription = "AI Assistant",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}
