package com.dsh.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable
fun CompanionTheme(
    primary: Color = Color(0xFF8AB4FF),
    content: @Composable () -> Unit,
) {
    val isLight = primary.luminance() > 0.58f
    val accent = when {
        isLight -> lerp(primary, Color.Black, 0.45f)
        primary.luminance() < 0.22f -> lerp(primary, Color.White, 0.35f)
        else -> primary
    }
    val onPrimary = if (accent.luminance() > 0.55f) {
        lerp(accent, Color.Black, 0.82f)
    } else {
        Color.White
    }

    val colors = if (isLight) {
        val background = lerp(Color.White, primary, 0.22f)
        val surface = lerp(Color.White, primary, 0.12f)
        val variant = lerp(Color.White, primary, 0.30f)
        val onBackground = lerp(primary, Color.Black, 0.82f)
        val onVariant = lerp(primary, Color.Black, 0.52f)
        val outline = lerp(primary, Color.Black, 0.32f)
        lightColorScheme(
            primary = accent,
            onPrimary = onPrimary,
            primaryContainer = lerp(Color.White, primary, 0.42f),
            onPrimaryContainer = onBackground,
            secondary = lerp(accent, Color.Black, 0.18f),
            onSecondary = Color.White,
            secondaryContainer = lerp(Color.White, primary, 0.34f),
            onSecondaryContainer = onBackground,
            tertiary = lerp(accent, Color.Black, 0.30f),
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onBackground,
            surfaceVariant = variant,
            onSurfaceVariant = onVariant,
            outline = outline,
            outlineVariant = lerp(primary, Color.White, 0.55f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(Color.White, primary, 0.08f),
            surfaceContainer = lerp(Color.White, primary, 0.14f),
            surfaceContainerHigh = lerp(Color.White, primary, 0.20f),
            surfaceContainerHighest = lerp(Color.White, primary, 0.26f),
            surfaceDim = lerp(Color.White, primary, 0.30f),
            surfaceBright = Color.White,
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        )
    } else {
        val background = lerp(Color(0xFF04060B), primary, 0.18f)
        val surface = lerp(background, primary, 0.10f)
        val variant = lerp(background, primary, 0.22f)
        val onBackground = Color(0xFFEAF0FB)
        darkColorScheme(
            primary = accent,
            onPrimary = onPrimary,
            primaryContainer = lerp(background, primary, 0.34f),
            onPrimaryContainer = Color(0xFFEAF1FF),
            secondary = lerp(accent, Color.White, 0.18f),
            onSecondary = onPrimary,
            secondaryContainer = lerp(background, primary, 0.24f),
            onSecondaryContainer = Color(0xFFEAF1FF),
            tertiary = lerp(accent, Color.White, 0.35f),
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onBackground,
            surfaceVariant = variant,
            onSurfaceVariant = Color(0xFFC7D2E6),
            outline = lerp(background, primary, 0.38f),
            outlineVariant = lerp(background, primary, 0.24f),
            surfaceContainerLowest = lerp(background, Color.Black, 0.10f),
            surfaceContainerLow = lerp(background, primary, 0.08f),
            surfaceContainer = lerp(background, primary, 0.14f),
            surfaceContainerHigh = lerp(background, primary, 0.20f),
            surfaceContainerHighest = lerp(background, primary, 0.26f),
            surfaceDim = lerp(background, Color.Black, 0.20f),
            surfaceBright = lerp(background, primary, 0.34f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF3B0907),
            errorContainer = Color(0xFF5A1A18),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
