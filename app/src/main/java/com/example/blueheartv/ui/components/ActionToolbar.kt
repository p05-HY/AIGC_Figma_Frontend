package com.example.blueheartv.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.blueheartv.ui.theme.IconGray
import com.example.blueheartv.util.ToastType
import com.example.blueheartv.util.ToastUtil

data class ActionItem(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit = {},
)

@Composable
fun ActionToolbar(
    modifier: Modifier = Modifier,
    messageContent: String = "",
    onSpeak: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val actions = listOf(
        ActionItem(Icons.Outlined.ContentCopy, "Copy") {
            clipboardManager.setText(AnnotatedString(messageContent))
            ToastUtil.show("已复制", ToastType.SUCCESS)
        },
        ActionItem(Icons.Outlined.Share, "Share") {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, messageContent)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "分享消息"))
        },
        ActionItem(Icons.Outlined.VolumeUp, "Speaker") {
            onSpeak(messageContent)
        },
        ActionItem(Icons.Outlined.Refresh, "Refresh") { onRefresh() },
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
