package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat()
    val screenHeightPx = windowInfo.containerSize.height.toFloat()
    val widgetSizePx = with(density) { 56.dp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }

    var offset by remember(screenWidthPx, screenHeightPx) {
        mutableStateOf(Offset(screenWidthPx - widgetSizePx - marginPx, screenHeightPx * 0.7f))
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset = Offset(
                        x = (offset.x + dragAmount.x).coerceIn(0f, screenWidthPx - widgetSizePx),
                        y = (offset.y + dragAmount.y).coerceIn(0f, screenHeightPx - widgetSizePx),
                    )
                }
            },
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            onClick = onClick,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        SurfaceWhite,
                        CircleShape,
                    ),
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
