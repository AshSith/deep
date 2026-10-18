package com.dsh.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CompanionColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF07131F),
    primaryContainer = Color(0xFF1E3554),
    onPrimaryContainer = Color(0xFFD8E4FF),
    secondary = Color(0xFF9FC7FF),
    onSecondary = Color(0xFF0A1B2C),
    secondaryContainer = Color(0xFF25384F),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = Color(0xFFB8C8E8),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE6ECF8),
    surface = Color(0xFF101828),
    onSurface = Color(0xFFE6ECF8),
    surfaceVariant = Color(0xFF1A2438),
    onSurfaceVariant = Color(0xFFB6C2D9),
    outline = Color(0xFF39486A),
    outlineVariant = Color(0xFF26324A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5A1A18),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CompanionColors,
        typography = Typography(),
        content = content,
    )
}
