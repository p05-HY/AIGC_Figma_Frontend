package com.example.blueheartv.floating

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.example.blueheartv.util.DialogUtil

object FloatingServiceLauncher {

    fun launch(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            DialogUtil.showAlert(
                title = "开启悬浮窗权限",
                message = "悬浮球功能需要「显示在其他应用上层」权限，请在设置中开启。",
                confirmText = "去开启",
                cancelText = "取消",
                onConfirm = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri(),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
            )
            return
        }

        FloatingBallService.start(context)
    }

    fun stop(context: Context) {
        FloatingBallService.stop(context)
    }

    fun isOverlayGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
}
