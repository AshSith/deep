package com.dsh.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable
fun CompanionTheme(
    primary: Color = Color(0xFF8AB4FF),
    content: @Composable () -> Unit,
) {
    val base = Color(0xFF070A12)
    val background = lerp(base, primary, 0.10f)
    val surface = lerp(background, primary, 0.06f)
    val surfaceVariant = lerp(background, primary, 0.14f)
    val outline = lerp(background, primary, 0.32f)
    val onPrimary = if (primary.luminance() > 0.55f) Color(0xFF07131F) else Color.White

    val colors = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = lerp(background, primary, 0.30f),
        onPrimaryContainer = Color(0xFFEAF1FF),
        secondary = lerp(primary, Color.White, 0.18f),
        onSecondary = onPrimary,
        secondaryContainer = lerp(background, primary, 0.20f),
        onSecondaryContainer = Color(0xFFEAF1FF),
        tertiary = lerp(primary, Color.White, 0.35f),
        background = background,
        onBackground = Color(0xFFE6ECF8),
        surface = surface,
        onSurface = Color(0xFFE6ECF8),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFFC5D0E4),
        outline = outline,
        outlineVariant = lerp(background, primary, 0.20f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF3B0907),
        errorContainer = Color(0xFF5A1A18),
        onErrorContainer = Color(0xFFFFDAD6),
    )

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
