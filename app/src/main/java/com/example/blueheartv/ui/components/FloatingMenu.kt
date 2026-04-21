package com.example.blueheartv.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.theme.*

data class FloatingMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit = {},
)

@Composable
fun FloatingMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    items: List<FloatingMenuItem> = defaultMenuItems(),
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        Surface(
            modifier = modifier
                .width(167.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                item.onClick()
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = TextBlack,
                        )
                        Text(
                            text = item.label,
                            fontSize = 14.sp,
                            color = TextBlack,
                        )
                    }
                    if (item != items.last()) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = DividerColor,
                        )
                    }
                }
            }
        }
    }
}

private fun defaultMenuItems() = listOf(
    FloatingMenuItem(Icons.Outlined.ChatBubbleOutline, "新对话"),
    FloatingMenuItem(Icons.Outlined.Screenshot, "截图识别"),
    FloatingMenuItem(Icons.Outlined.Translate, "快速翻译"),
    FloatingMenuItem(Icons.Outlined.Summarize, "内容总结"),
    FloatingMenuItem(Icons.Outlined.Settings, "设置"),
)
