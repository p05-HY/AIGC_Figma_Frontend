package com.example.blueheartv.control

import android.content.pm.PackageManager

enum class ShizukuPermissionRequestResult {
    AlreadyGranted,
    Requested,
    Unsupported,
    BinderUnavailable,
}

class ShizukuPermissionChecker(
    private val isPreV11: () -> Boolean,
    private val checkSelfPermission: () -> Int,
    private val requestPermission: (Int) -> Unit,
) {
    fun needsPermission(): Boolean {
        return runCatching {
            !isPreV11() && checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        }.getOrDefault(true)
    }

    fun requestIfNeeded(requestCode: Int): ShizukuPermissionRequestResult {
        return runCatching {
            if (isPreV11()) return ShizukuPermissionRequestResult.Unsupported
            if (checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                return ShizukuPermissionRequestResult.AlreadyGranted
            }
            requestPermission(requestCode)
            ShizukuPermissionRequestResult.Requested
        }.getOrDefault(ShizukuPermissionRequestResult.BinderUnavailable)
    }
}
