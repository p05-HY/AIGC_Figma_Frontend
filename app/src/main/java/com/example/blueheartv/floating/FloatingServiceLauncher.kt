package com.example.blueheartv.floating

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import com.example.blueheartv.R
import com.example.blueheartv.util.DialogUtil

object FloatingServiceLauncher {

    fun launch(context: Context, permissionLauncher: ActivityResultLauncher<Intent>? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            DialogUtil.showAlert(
                title = context.getString(R.string.floating_perm_title),
                message = context.getString(R.string.floating_perm_message),
                confirmText = context.getString(R.string.floating_perm_confirm),
                cancelText = context.getString(R.string.cancel),
                onConfirm = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri(),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (permissionLauncher != null) {
                        permissionLauncher.launch(intent)
                    } else {
                        context.startActivity(intent)
                    }
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
