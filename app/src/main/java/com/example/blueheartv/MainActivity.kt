package com.example.blueheartv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.blueheartv.chat.AppContextHolder
import com.example.blueheartv.chat.SiliconFlowConfigStore
import com.example.blueheartv.navigation.AppNavGraph
import com.example.blueheartv.ui.theme.BlueHeartVTheme
import com.example.blueheartv.ui.theme.SurfaceWhite

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SiliconFlowConfigStore.configure(
            apiKey = BuildConfig.SILICONFLOW_API_KEY,
            model = BuildConfig.SILICONFLOW_MODEL,
        )
        AppContextHolder.install(applicationContext)
        enableEdgeToEdge()
        setContent {
            BlueHeartVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SurfaceWhite,
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
