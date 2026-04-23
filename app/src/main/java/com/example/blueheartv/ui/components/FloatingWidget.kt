package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.BlueAccent
import com.example.blueheartv.ui.theme.SurfaceWhite
import kotlin.math.roundToInt

@Composable
fun FloatingWidget(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val widgetSizePx = with(density) { 56.dp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }

    var offset by remember {
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
                    painter = painterResource(R.drawable.robot_mascot),
                    contentDescription = "AI Assistant",
                    modifier = Modifier.size(28.dp, 42.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
