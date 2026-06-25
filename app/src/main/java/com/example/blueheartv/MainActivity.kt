package com.example.blueheartv

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.chat.AppContextHolder
import com.example.blueheartv.chat.DeviceIdStore
import com.example.blueheartv.control.AccessibilityAutoEnabler
import com.example.blueheartv.control.AdbAccessibilityService
import com.example.blueheartv.control.AdbWebSocketService
import com.example.blueheartv.control.ShizukuPermissionChecker
import com.example.blueheartv.control.ShizukuPermissionRequestResult
import com.example.blueheartv.floating.FloatingBallService
import com.example.blueheartv.navigation.AppNavGraph
import com.example.blueheartv.system.SystemService
import com.example.blueheartv.ui.components.DeviceCapabilityPanel
import com.example.blueheartv.ui.theme.BlueHeartVTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var permissionCheckTrigger = mutableIntStateOf(0)
    private val shizukuPermissionChecker = ShizukuPermissionChecker(
        isPreV11 = Shizuku::isPreV11,
        checkSelfPermission = Shizuku::checkSelfPermission,
        requestPermission = Shizuku::requestPermission,
    )

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                permissionCheckTrigger.intValue++
            }
        }

    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionCheckTrigger.intValue++
    }

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionCheckTrigger.intValue++
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java) ?: return false
        val enabledServices = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == AdbAccessibilityService::class.java.name
        }
    }

    private fun requestShizukuPermissionIfNeeded() {
        when (shizukuPermissionChecker.requestIfNeeded(SHIZUKU_REQUEST_CODE)) {
            ShizukuPermissionRequestResult.BinderUnavailable -> {
                Log.w(TAG, "Shizuku binder unavailable; ask user to start Shizuku first")
                permissionCheckTrigger.intValue++
            }
            ShizukuPermissionRequestResult.Requested,
            ShizukuPermissionRequestResult.AlreadyGranted,
            ShizukuPermissionRequestResult.Unsupported -> Unit
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val SHIZUKU_REQUEST_CODE = 7001
        private val _pendingSessionId = MutableStateFlow<String?>(null)
        val pendingSessionId: StateFlow<String?> = _pendingSessionId.asStateFlow()

        fun consumePendingSessionId(): String? {
            val id = _pendingSessionId.value
            _pendingSessionId.value = null
            return id
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentServerConfigStore.init(
            context = applicationContext,
            defaultBaseUrl = BuildConfig.AGENT_SERVER_BASE_URL,
            defaultApiKey = BuildConfig.AGENT_SERVER_API_KEY,
        )
        AppContextHolder.install(applicationContext)
        DeviceIdStore.deviceId(applicationContext)
        if (AgentServerConfigStore.snapshot().isConfigured) {
            AdbWebSocketService.start(applicationContext)
            SystemService.start(applicationContext, AgentServerConfigStore.snapshot().baseUrl)
        }

        handleSessionIdIntent(intent)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        lifecycleScope.launch {
            if (AccessibilityAutoEnabler.tryAutoEnable(applicationContext)) {
                permissionCheckTrigger.intValue++
            }
        }

        enableEdgeToEdge()
        setContent {
            BlueHeartVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // 用 remember + mutableStateOf 包裹，确保 Compose 能追踪变化
                    val triggerCount by permissionCheckTrigger
                    val needsOverlay = remember(triggerCount) {
                        !Settings.canDrawOverlays(this@MainActivity)
                    }
                    val needsAccessibility = remember(triggerCount) {
                        !isAccessibilityServiceEnabled()
                    }
                    val needsShizuku = remember(triggerCount) {
                        shizukuPermissionChecker.needsPermission()
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        DeviceCapabilityPanel(
                            needsShizuku = needsShizuku,
                            needsAccessibility = needsAccessibility,
                            needsOverlay = needsOverlay,
                            onRequestShizuku = { requestShizukuPermissionIfNeeded() },
                            onRequestAccessibility = {
                                accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onRequestOverlay = {
                                overlaySettingsLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        "package:$packageName".toUri(),
                                    ),
                                )
                            },
                        )
                        AppNavGraph()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSessionIdIntent(intent)
    }

    private fun handleSessionIdIntent(intent: Intent?) {
        intent?.getStringExtra(FloatingBallService.EXTRA_SESSION_ID)?.let { sessionId ->
            _pendingSessionId.value = sessionId
        }
    }
}
