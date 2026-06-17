package com.example.blueheartv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.blueheartv.MainActivity
import com.example.blueheartv.ui.screens.CoordinateCalibrationScreen
import com.example.blueheartv.ui.screens.HomeScreen
import com.example.blueheartv.ui.screens.SettingsDetailScreen
import com.example.blueheartv.ui.screens.SettingsScreen
import com.example.blueheartv.viewmodel.ChatViewModel
import com.example.blueheartv.viewmodel.SettingsDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SETTINGS_DETAIL = "settings_detail/{key}"
    const val CALIBRATION = "calibration"

    fun settingsDetail(key: String) = "settings_detail/$key"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = koinInject()

    val pendingSessionId by MainActivity.pendingSessionId.collectAsState()
    LaunchedEffect(pendingSessionId) {
        MainActivity.consumePendingSessionId()?.let { sessionId ->
            chatViewModel.selectHistory(sessionId)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = chatViewModel,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { key ->
                    navController.navigate(Routes.settingsDetail(key))
                },
                onNavigateToCalibration = {
                    navController.navigate(Routes.CALIBRATION)
                },
            )
        }

        composable(Routes.CALIBRATION) {
            CoordinateCalibrationScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS_DETAIL) { backStackEntry ->
            val key = backStackEntry.arguments?.getString("key") ?: ""
            val settingsDetailViewModel: SettingsDetailViewModel = koinViewModel()
            SettingsDetailScreen(
                settingKey = key,
                onBack = { navController.popBackStack() },
                viewModel = settingsDetailViewModel,
                onClearHistory = { chatViewModel.clearAllHistory() },
                onLogout = {
                    chatViewModel.clearAllHistory()
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
