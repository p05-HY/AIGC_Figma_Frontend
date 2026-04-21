package com.example.blueheartv.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.blueheartv.ui.theme.IconGray

data class ActionItem(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit = {},
)

@Composable
fun ActionToolbar(
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        ActionItem(Icons.Outlined.ContentCopy, "Copy"),
        ActionItem(Icons.Outlined.Share, "Share"),
        ActionItem(Icons.Outlined.ThumbUp, "Like"),
        ActionItem(Icons.Outlined.ThumbDown, "Dislike"),
        ActionItem(Icons.Outlined.VolumeUp, "Speaker"),
        ActionItem(Icons.Outlined.Refresh, "Refresh"),
        ActionItem(Icons.Outlined.MoreHoriz, "More"),
    )

    Row(
        modifier = modifier.padding(start = 74.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            Icon(
                imageVector = action.icon,
                contentDescription = action.contentDescription,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { action.onClick() },
                tint = IconGray,
            )
        }
    }
}
