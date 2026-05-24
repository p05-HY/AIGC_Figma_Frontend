package com.example.blueheartv.control

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object AccessibilityAutoEnabler {

    private const val TAG = "A11yAutoEnabler"
    private const val PREFS_NAME = "accessibility_prefs"
    private const val KEY_AUTO_ENABLE = "auto_enable_accessibility"

    fun isAutoEnableOn(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ENABLE, false)
    }

    fun setAutoEnable(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_AUTO_ENABLE, enabled) }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val enabledServices = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == AdbAccessibilityService::class.java.name
        }
    }

    private fun isShizukuAvailable(): Boolean {
        return runCatching {
            !Shizuku.isPreV11() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    suspend fun enableAccessibilityService(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            if (!isShizukuAvailable()) {
                Log.w(TAG, "Shizuku not available, cannot auto-enable accessibility")
                return@withContext false
            }

            val packageName = context.packageName
            val serviceName = AdbAccessibilityService::class.java.name
            val componentFlat = "$packageName/$serviceName"

            try {
                val executor = ShizukuAdbExecutor(packageName)
                try {
                    val current = executor.execute(
                        "settings get secure enabled_accessibility_services"
                    )
                    val currentValue = current.stdout.trim()

                    if (componentFlat in currentValue) {
                        Log.d(TAG, "Accessibility service already in enabled list")
                        return@withContext true
                    }

                    val newValue = if (currentValue.isBlank() || currentValue == "null") {
                        componentFlat
                    } else {
                        "$currentValue:$componentFlat"
                    }

                    val putResult = executor.execute(
                        "settings put secure enabled_accessibility_services $newValue"
                    )
                    val enableResult = executor.execute(
                        "settings put secure accessibility_enabled 1"
                    )

                    val success = putResult.isSuccess && enableResult.isSuccess
                    if (success) {
                        Log.i(TAG, "Accessibility service auto-enabled successfully")
                    } else {
                        Log.e(TAG, "Failed: put=${putResult.stderr}, enable=${enableResult.stderr}")
                    }
                    success
                } finally {
                    executor.destroy()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-enable accessibility service", e)
                false
            }
        }

    suspend fun tryAutoEnable(context: Context): Boolean {
        if (!isAutoEnableOn(context)) return false
        if (isAccessibilityServiceEnabled(context)) return true
        return enableAccessibilityService(context)
    }
}
