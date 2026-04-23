package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
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

        Box(
            modifier = Modifier
                .matchParentSize()
                .border(0.5.dp, ButtonBorderDark, shape)
        )

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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun GlassButtonRow(
    buttons: List<String>,
    onButtonClick: (Int) -> Unit = {},
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val buttonWidth = (screenWidth - 13.dp * 2 - 8.dp * 3) / 3.5f

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(buttons) { index, label ->
            GlassButton(
                text = label,
                onClick = { onButtonClick(index) },
                modifier = Modifier.width(buttonWidth),
            )
        }
    }
}
