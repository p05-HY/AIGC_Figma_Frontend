package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.blueheartv.ui.theme.GlassFill
import com.example.blueheartv.ui.theme.GlassUpperHighlight
import com.example.blueheartv.ui.theme.GradientWhite00
import com.example.blueheartv.ui.theme.GradientWhite40
import com.example.blueheartv.ui.theme.Slate700Stroke
import com.example.blueheartv.ui.theme.SurfaceWhite

@Composable
fun GlassmorphismCardBackground(
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GlassFill, shape),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(GradientWhite00, SurfaceWhite)),
                    shape,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(21.dp)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to GradientWhite40,
                            1f to GradientWhite00,
                        ),
                    ),
                    shape,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Slate700Stroke, shape),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, GlassUpperHighlight, shape),
        )
    }
}
