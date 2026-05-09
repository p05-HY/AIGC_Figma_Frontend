package com.example.blueheartv.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.preference.PreferenceManager
import java.net.HttpURLConnection
import java.net.URL

class RuntimeModeProvider(context: Context) {

    private val appContext = context.applicationContext

    fun getStatus(): RuntimeModeStatus {
        val connected = checkNetworkConnected()
        val localRunning = checkLocalServerRunning()
        val modelPath = getLocalModelPath()
        val mode = resolveMode(connected, localRunning)

        return RuntimeModeStatus(
            networkConnected = connected,
            mode = mode,
            localServerRunning = localRunning,
            localModelPath = modelPath
        )
    }

    private fun checkNetworkConnected(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun checkLocalServerRunning(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        val port = prefs.getInt(PREF_LOCAL_SERVER_PORT, DEFAULT_LOCAL_PORT)
        return try {
            val url = URL("http://127.0.0.1:$port/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun getLocalModelPath(): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        val path = prefs.getString(PREF_LOCAL_MODEL_PATH, null)
        return path?.takeIf { it.isNotBlank() }
    }

    private fun resolveMode(networkConnected: Boolean, localServerRunning: Boolean): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        val preferred = prefs.getString(PREF_PREFERRED_MODE, null)

        return when {
            preferred == MODE_LOCAL && localServerRunning -> MODE_LOCAL
            preferred == MODE_CLOUD && networkConnected -> MODE_CLOUD
            localServerRunning && !networkConnected -> MODE_LOCAL
            networkConnected -> MODE_CLOUD
            localServerRunning -> MODE_LOCAL
            else -> MODE_LOCAL
        }
    }

    companion object {
        const val MODE_CLOUD = "cloud"
        const val MODE_LOCAL = "local"
        private const val PREF_PREFERRED_MODE = "ai_preferred_mode"
        private const val PREF_LOCAL_SERVER_PORT = "ai_local_server_port"
        private const val PREF_LOCAL_MODEL_PATH = "ai_local_model_path"
        private const val DEFAULT_LOCAL_PORT = 8080
    }
}
