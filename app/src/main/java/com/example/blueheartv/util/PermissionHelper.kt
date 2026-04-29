package com.example.blueheartv.util

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

data class PermissionRequest(
    val permissions: List<String>,
    val rationaleMessage: String,
)

@Composable
fun rememberPermissionHandler(
    snackbarHostState: SnackbarHostState,
    onGranted: () -> Unit,
): (PermissionRequest) -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity

    var pendingOnGranted by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsDialogMessage by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val allGranted = results.isNotEmpty() && results.values.all { it }
        if (allGranted) {
            pendingOnGranted?.invoke()
            pendingOnGranted = null
        } else {
            val permanentlyDenied = activity != null && results.keys.any { perm ->
                val granted = results[perm] == true
                !granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            }
            if (permanentlyDenied) {
                settingsDialogMessage = settingsDialogMessage.ifBlank { "需要在系统设置中手动开启权限" }
                showSettingsDialog = true
            } else {
                pendingOnGranted = null
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("权限被拒绝") },
            text = { Text(settingsDialogMessage + "，请前往系统设置开启。") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("取消") }
            },
        )
    }

    val scope = rememberCoroutineScope()

    return remember(snackbarHostState, onGranted) {
        { request: PermissionRequest ->
            if (request.permissions.isEmpty()) {
                onGranted()
            } else {
                val alreadyGranted = request.permissions.all { perm ->
                    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                }
                if (alreadyGranted) {
                    onGranted()
                } else {
                    pendingOnGranted = onGranted
                    settingsDialogMessage = request.rationaleMessage

                    val shouldRationale = activity != null && request.permissions.any { perm ->
                        ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
                    }
                    if (shouldRationale) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = request.rationaleMessage,
                                actionLabel = "授权",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                launcher.launch(request.permissions.toTypedArray())
                            }
                        }
                    } else {
                        launcher.launch(request.permissions.toTypedArray())
                    }
                }
            }
        }
    }
}

fun imagePermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        listOf(android.Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

fun cameraPermissions(): List<String> {
    return listOf(android.Manifest.permission.CAMERA)
}

fun audioPermissions(): List<String> {
    return listOf(android.Manifest.permission.RECORD_AUDIO)
}

fun locationPermissions(): List<String> {
    return listOf(
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
    )
}

fun calendarPermissions(): List<String> {
    return listOf(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR,
    )
}

fun notificationPermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        listOf(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }
}
