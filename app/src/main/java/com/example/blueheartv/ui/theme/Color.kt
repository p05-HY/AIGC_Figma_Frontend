package com.example.blueheartv.ui.theme

import androidx.compose.ui.graphics.Color

val DarkPrimary = Color(0xFF050A11)
val BlueAccent = Color(0xFF80A4E5)
val BlueAccentLight = Color(0xFF80A3E5)
val MutedText = Color(0xFF64748B)
val LightGray = Color(0xFFFAFAFA)
val SurfaceWhite = Color(0xFFFFFFFF)
val TextBlack = Color(0xFF000000)
val TextDark = Color(0xFF0E0E0E)
val TextDarkAlt = Color(0xFF111111)
val GrayText = Color(0xFF6D6D6D)
val IconGray = Color(0xFF8D8D8D)
val DividerColor = Color(0xFFE2E8F0)
val CardBackground = Color(0xFFF8FAFC)
val PrivacyBlue = Color(0xFF2986AA)
val SubtitleGray = Color(0xFF4B4B4B)
val BorderGray = Color(0x7DCECECE) // rgba(206,206,206,0.49)
val OverlayBlack = Color(0x52000000) // rgba(0,0,0,0.32)
val StrokeMuted = Color(0x8094A3B8) // rgba(148,163,184,0.5)

val GlowCyan = Color(0x803FFFEF) // rgba(63,255,239,0.5)
val GlowCyanShadow = Color(0xFF92F2EA)
val GlowBlue = Color(0x803F85FF) // rgba(63,133,255,0.5)
val GlowBlueShadow = Color(0x803F85FF)
val GlowWhite = Color(0x80FFFFFF) // rgba(255,255,255,0.5)

val GradientBlueStart = Color(0xFFD0E4FF) // rgba(208,228,255,1)
val GradientBlueEnd = Color(0xFFFFFFFF)

val ButtonBorderDark = Color(0x6647515A) // rgba(71,81,90,0.4)

// Glassmorphism & Echo-style tokens (from echo_figma design)
val GlassFill = Color(0xFFF8FAFC)
val GlassFillTranslucent = Color(0x99F8FAFC)
val GlassUpperHighlight = Color(0x8FFFFFFF)
val GradientWhite00 = Color(0x00FFFFFF) // Fully transparent white
val GradientWhite40 = Color(0x66FFFFFF) // 40% opacity white
val Slate700Stroke = Color(0x6647515A) // 40% opacity dark slate (for outer border)
val ChipFill = Color(0x99F8FAFC) // Chip background (60% opacity)
val ChipStroke = Color(0x333F85FF) // Chip border (20% opacity blue)

// Echo brand tokens for high-exposure controls
val BrandPrimary = Color(0xFF7F77DD)
val BrandPrimarySoft = Color(0x1A7F77DD)
val BrandPrimaryPressed = Color(0xFF6F67CA)
val BrandStroke = Color(0x667F77DD)
val BrandRipple = Color(0x337F77DD)
val DangerSoft = Color(0xFFFCEBEB)
val DangerStroke = Color(0x66A32D2D)
val DangerText = Color(0xFFA32D2D)

// Semantic status colors
val ErrorRed = Color(0xFFE53935)
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFFF9800)

// Surface backgrounds
val CodeBlockBackground = Color(0xFF111827)
val CodeBlockText = Color(0xFFF9FAFB)
val CodeBlockLabel = Color(0xFF9CA3AF)
val ThinkingCardBackground = Color(0xFFF3F4F6)
val ToolDetailBackground = Color(0xFFF7F8FA)
val OverlayHeavy = Color(0xCC1A2030) // For calibration top card
val AuthCheckGreenStart = Color(0xFF9BF763)
val AuthCheckGreenEnd = Color(0xFF26AB5B)
val AuthUncheckedGray = Color(0xFFE0E0E0)

// Toast card gradient colors (from Figma node-id=462-660)
val ToastGradient1 = Color(0xFFD0E4FF)
val ToastGradient2 = Color(0xFFE8D0FF)
val ToastGradient3 = Color(0xFFD0F0FF)
val ToastBodyText = Color(0xFA3C3E40) // rgba(60,62,64,0.98)

// Corner radius tokens
object Radius {
    /** 8dp — chips, small buttons */
    val small = 8
    /** 12dp — cards, smart cards, holders */
    val medium = 12
    /** 16dp — input bars, card groups, surface cards */
    val large = 16
    /** 24dp — chat bubbles, auth card */
    val xlarge = 24
    /** 40dp — drawers, top-level containers */
    val xxlarge = 40
}
