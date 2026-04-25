package com.example.blueheartv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.blueheartv.chat.AppContextHolder
import com.example.blueheartv.chat.SiliconFlowConfigStore
import com.example.blueheartv.floating.FloatingBallService
import com.example.blueheartv.navigation.AppNavGraph
import com.example.blueheartv.ui.theme.BlueHeartVTheme
import com.example.blueheartv.ui.theme.ThemeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.example.blueheartv.control.AdbAccessibilityService
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private var permissionCheckTrigger = mutableStateOf(0)

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                permissionCheckTrigger.value++
            }
        }

    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionCheckTrigger.value++
    }

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionCheckTrigger.value++
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
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
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
        SiliconFlowConfigStore.configure(
            apiKey = BuildConfig.SILICONFLOW_API_KEY,
            model = BuildConfig.SILICONFLOW_MODEL,
        )
        AppContextHolder.install(applicationContext)
        ThemeRepository.init(applicationContext)

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
                            Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }.getOrDefault(true)
                    }

                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (needsOverlay || needsAccessibility || needsShizuku) {
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                androidx.compose.foundation.layout.Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                                ) {
                                    androidx.compose.material3.Text(
                                        text = "AI 控制功能需要以下权限",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    if (needsShizuku) {
                                        androidx.compose.material3.TextButton(
                                            onClick = { requestShizukuPermissionIfNeeded() }
                                        ) {
                                            androidx.compose.material3.Text("授权 Shizuku（执行 shell 命令）")
                                        }
                                    }
                                    if (needsAccessibility) {
                                        androidx.compose.material3.TextButton(onClick = {
                                            accessibilitySettingsLauncher.launch(
                                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            )
                                        }) {
                                            androidx.compose.material3.Text("开启无障碍服务（AI 控制手机）")
                                        }
                                    }
                                    if (needsOverlay) {
                                        androidx.compose.material3.TextButton(onClick = {
                                            overlaySettingsLauncher.launch(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    "package:$packageName".toUri()
                                                )
                                            )
                                        }) {
                                            androidx.compose.material3.Text("授权悬浮窗（状态提示）")
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
