package com.example.blueheartv.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val AppLightColorScheme = lightColorScheme(
    primary = BlueAccent,
    onPrimary = SurfaceWhite,
    secondary = MutedText,
    onSecondary = SurfaceWhite,
    background = SurfaceWhite,
    onBackground = DarkPrimary,
    surface = SurfaceWhite,
    onSurface = DarkPrimary,
    surfaceVariant = LightGray,
    onSurfaceVariant = MutedText,
    outline = DividerColor,
)

@Composable
fun BlueHeartVTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = AppLightColorScheme,
        typography = AppTypography,
        content = content,
    )
}
