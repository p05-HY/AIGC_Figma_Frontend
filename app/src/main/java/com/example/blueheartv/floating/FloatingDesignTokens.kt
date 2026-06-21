package com.example.blueheartv.floating

import androidx.compose.ui.graphics.Color

object FloatingDesignTokens {
    const val BallSizeDp = 60
    const val ClickThresholdDp = 8
    const val MultiTapTimeoutMs = 300L
    const val MaxTapCount = 3
    const val VisualResetDelayMs = 1200L

    val SuccessStrokeColor: Int = 0xFF4CAF50.toInt()
    val FailedStrokeColor: Int = 0xFFFF6B6B.toInt()

    val GlassFillColor = Color(0x99F8FAFC)
    val GlassStrokeOuter = Color(0x6647515A)
    val GlassStrokeBlue = Color(0x333F85FF)
    val GlassGradientTop = Color(0x00FFFFFF)
    val GlassGradientBottom = Color(0xFFFFFFFF)
    val GlassWhiteOverlay70 = Color(0xB3FFFFFF)
    val GlassWhiteOverlay80 = Color(0xCCFFFFFF)
    val TitleBlue = Color(0xFF80A3E5)
    val PlaceholderGray = Color(0xFF6D6D6D)
}
