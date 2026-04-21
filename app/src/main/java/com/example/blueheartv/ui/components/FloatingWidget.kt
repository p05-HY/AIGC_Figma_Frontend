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
    var offset by remember { mutableStateOf(Offset(300f, 600f)) }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset = Offset(
                        x = offset.x + dragAmount.x,
                        y = offset.y + dragAmount.y,
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
