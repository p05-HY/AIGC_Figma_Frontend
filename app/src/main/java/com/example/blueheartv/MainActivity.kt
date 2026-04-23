package com.example.blueheartv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
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

class MainActivity : ComponentActivity() {

    companion object {
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

        enableEdgeToEdge()
        setContent {
            val themePreference by ThemeRepository.preference.collectAsState()
            BlueHeartVTheme(themePreference = themePreference) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavGraph()
                }
            }
        }
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
