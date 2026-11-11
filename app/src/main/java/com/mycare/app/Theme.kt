package com.mycare.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TealDeep = Color(0xFF0F6B70)
val TealMain = Color(0xFF167D83)
val TealSoft = Color(0xFFD7F0EE)
val ScreenBg = Color(0xFFF4F8F8)
val InkText = Color(0xFF1B2B2C)
val MutedText = Color(0xFF5E7273)

private val CareColors = lightColorScheme(
    primary = TealMain,
    onPrimary = Color.White,
    primaryContainer = TealSoft,
    onPrimaryContainer = TealDeep,
    secondary = Color(0xFF62A9A2),
    background = ScreenBg,
    surface = Color.White,
    onBackground = InkText,
    onSurface = InkText,
    surfaceVariant = Color(0xFFEAF2F2),
    onSurfaceVariant = MutedText,
    error = Color(0xFFB3261E)
)

@Composable
fun MyCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CareColors, content = content)
}

fun typeColor(type: String): Color = when (type) {
    "موعد" -> Color(0xFF167D83)
    "جلسة" -> Color(0xFF5B6EE1)
    "شكوى" -> Color(0xFFD9822B)
    "دواء" -> Color(0xFF3E9B5B)
    else -> Color(0xFF8A6BC1)
}
