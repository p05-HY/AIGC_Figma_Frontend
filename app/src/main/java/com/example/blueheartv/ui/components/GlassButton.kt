package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.theme.*

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // Background gradient layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 1f),
                        )
                    ),
                    shape,
                )
        )

        // Border with glass effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(0.5.dp, ButtonBorderDark, shape)
        )

        // Top highlight stroke
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    0.5.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.56f),
                            Color.White,
                        ),
                        startY = 0f,
                        endY = 100f,
                    ),
                    shape,
                )
        )

        Text(
            text = text,
            fontSize = 12.sp,
            color = MutedText,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun GlassButtonRow(
    buttons: List<String>,
    onButtonClick: (Int) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        buttons.forEach { label ->
            GlassButton(
                text = label,
                onClick = { onButtonClick(buttons.indexOf(label)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
