package com.example.blueheartv.util

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.blueheartv.ui.theme.BlueAccent
import com.example.blueheartv.ui.theme.TextBlack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// ============================================================
// Usage:
//   // 1. Place AppDialogHost at the top level of your screen:
//   Box(modifier = Modifier.fillMaxSize()) {
//       // ... screen content ...
//       AppDialogHost()
//   }
//
//   // 2. Single-button alert (info only):
//   DialogUtil.showAlert(
//       title = "提示",
//       message = "操作已完成",
//   )
//
//   // 3. Two-button confirm:
//   DialogUtil.showAlert(
//       title = "确认删除",
//       message = "删除后不可恢复，确定继续吗？",
//       confirmText = "删除",
//       cancelText = "取消",
//       onConfirm = { /* do delete */ },
//       onCancel = { /* dismissed */ },
//   )
// ============================================================

object DialogUtil {
    internal data class DialogEvent(
        val title: String,
        val message: String,
        val confirmText: String,
        val cancelText: String?,
        val onConfirm: (() -> Unit)?,
        val onCancel: (() -> Unit)?,
    )

    private val _events = MutableSharedFlow<DialogEvent>(extraBufferCapacity = 3)
    internal val events = _events.asSharedFlow()

    fun showAlert(
        title: String,
        message: String,
        confirmText: String = "确定",
        cancelText: String? = null,
        onConfirm: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
    ) {
        _events.tryEmit(
            DialogEvent(title, message, confirmText, cancelText, onConfirm, onCancel)
        )
    }
}

@Composable
fun AppDialogHost() {
    var currentEvent by remember { mutableStateOf<DialogUtil.DialogEvent?>(null) }

    LaunchedEffect(Unit) {
        DialogUtil.events.collect { event ->
            currentEvent = event
        }
    }

    val event = currentEvent ?: return

    AlertDialog(
        onDismissRequest = {
            event.onCancel?.invoke()
            currentEvent = null
        },
        title = {
            Text(
                text = event.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextBlack,
            )
        },
        text = {
            Text(
                text = event.message,
                fontSize = 14.sp,
                color = TextBlack.copy(alpha = 0.7f),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                event.onConfirm?.invoke()
                currentEvent = null
            }) {
                Text(event.confirmText, color = BlueAccent)
            }
        },
        dismissButton = if (event.cancelText != null) {
            {
                TextButton(onClick = {
                    event.onCancel?.invoke()
                    currentEvent = null
                }) {
                    Text(event.cancelText, color = TextBlack.copy(alpha = 0.6f))
                }
            }
        } else {
            null
        },
    )
}
