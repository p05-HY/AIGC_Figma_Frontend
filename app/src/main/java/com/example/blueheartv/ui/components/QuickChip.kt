package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.theme.ChipFill
import com.example.blueheartv.ui.theme.ChipStroke
import com.example.blueheartv.ui.theme.GradientWhite00
import com.example.blueheartv.ui.theme.Radius
import com.example.blueheartv.ui.theme.SurfaceWhite
import com.example.blueheartv.ui.theme.MutedText

@Composable
fun QuickChip(
    label: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.small.dp)
    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .widthIn(min = 64.dp)
            .clip(shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(ChipFill, shape),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(listOf(GradientWhite00, SurfaceWhite)),
                    shape,
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, ChipStroke, shape),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MutedText,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}