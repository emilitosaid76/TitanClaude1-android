package com.titan.agent.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0F1720)
val Card = Color(0xFF16212C)
val Card2 = Color(0xFF1C2B38)
val Border = Color(0xFF253544)
val TextPrimary = Color(0xFFE6EDF3)
val Muted = Color(0xFF8A9BB0)
val Yellow = Color(0xFFFFD100)
val YellowDark = Color(0xFFE6BC00)
val Ok = Color(0xFF2ECC71)
val Danger = Color(0xFFEF4444)
val Warn = Color(0xFFF5A623)
val Accent = Color(0xFF3FA9F5)
val CodeBg = Color(0xFF111B25)
val UserBubble = Color(0xFF1A2A3A)

private val TitanColorScheme = darkColorScheme(
    primary = Yellow,
    onPrimary = Color.Black,
    secondary = Accent,
    onSecondary = Color.Black,
    background = Bg,
    surface = Card,
    surfaceVariant = Card2,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = Muted,
    outline = Border,
    error = Danger,
)

val Mono = FontFamily.Monospace

@Composable
fun TitanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TitanColorScheme,
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(
                color = Yellow, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp
            ),
        ),
        content = content
    )
}
