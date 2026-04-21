package com.example.blueheartv.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.blueheartv.ui.screens.AuthScreen
import com.example.blueheartv.ui.screens.HomeScreen
import com.example.blueheartv.ui.screens.SettingsScreen
import com.example.blueheartv.viewmodel.AuthViewModel
import com.example.blueheartv.viewmodel.ChatViewModel

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.AUTH,
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                viewModel = authViewModel,
                onAuthorized = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
            )
        }

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
            )
        }
    }
}
