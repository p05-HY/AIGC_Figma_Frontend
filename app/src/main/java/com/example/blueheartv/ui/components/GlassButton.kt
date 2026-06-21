package com.example.blueheartv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
) {
    EchoPromptChip(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun GlassButtonRow(
    buttons: List<String>,
    icons: List<ImageVector?> = emptyList(),
    onButtonClick: (Int) -> Unit = {},
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(buttons) { index, label ->
            GlassButton(
                text = label,
                icon = icons.getOrNull(index),
                onClick = { onButtonClick(index) },
            )
        }
    }
}
