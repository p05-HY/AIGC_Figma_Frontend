package com.example.blueheartv

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.chat.AppContextHolder
import com.example.blueheartv.control.AdbAccessibilityService
import com.example.blueheartv.control.AdbWebSocketService
import com.example.blueheartv.floating.FloatingBallService
import com.example.blueheartv.navigation.AppNavGraph
import com.example.blueheartv.system.SystemService
import com.example.blueheartv.ui.theme.BlueHeartVTheme
import com.example.blueheartv.ui.theme.ThemeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var permissionCheckTrigger = mutableIntStateOf(0)

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
        if (Shizuku.isPreV11()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    companion object {
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
        ThemeRepository.init(applicationContext)
        if (AgentServerConfigStore.snapshot().isConfigured) {
            AdbWebSocketService.start(applicationContext)
            SystemService.start(applicationContext, AgentServerConfigStore.snapshot().baseUrl)
        }

        handleSessionIdIntent(intent)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        enableEdgeToEdge()
        setContent {
            val themePreference by ThemeRepository.preference.collectAsState()
            BlueHeartVTheme(themePreference = themePreference) {
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
                        runCatching {
                            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
                        }.getOrDefault(true)
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (needsOverlay || needsAccessibility || needsShizuku) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "AI 控制功能需要以下权限",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    if (needsShizuku) {
                                        TextButton(
                                            onClick = { requestShizukuPermissionIfNeeded() }
                                        ) {
                                            Text("授权 Shizuku（执行 shell 命令）")
                                        }
                                    }
                                    if (needsAccessibility) {
                                        TextButton(onClick = {
                                            accessibilitySettingsLauncher.launch(
                                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            )
                                        }) {
                                            Text("开启无障碍服务（AI 控制手机）")
                                        }
                                    }
                                    if (needsOverlay) {
                                        TextButton(onClick = {
                                            overlaySettingsLauncher.launch(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    "package:$packageName".toUri()
                                                )
                                            )
                                        }) {
                                            Text("授权悬浮窗（状态提示）")
                                        }
                                    }
                                }
                            }
                        }
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
