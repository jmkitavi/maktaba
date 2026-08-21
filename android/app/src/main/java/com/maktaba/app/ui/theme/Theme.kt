package com.maktaba.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import android.app.Activity

/**
 * Entry point for the design system. `MaktabaTheme.colors` and `MaktabaTheme.spacing` are
 * the only sanctioned sources for colour and spacing; type comes from
 * `MaterialTheme.typography`.
 */
object MaktabaTheme {
    val colors: MaktabaColors
        @Composable @ReadOnlyComposable get() = LocalMaktabaColors.current

    val spacing: MaktabaSpacing
        @Composable @ReadOnlyComposable get() = LocalMaktabaSpacing.current
}

private fun materialSchemeFrom(colors: MaktabaColors) = if (colors.isDark) {
    darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.surfaceAlt,
        onPrimaryContainer = colors.ink,
        secondary = colors.accent,
        onSecondary = colors.onAccent,
        background = colors.background,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.surfaceAlt,
        onSurfaceVariant = colors.inkSoft,
        outline = colors.divider,
        outlineVariant = colors.divider,
        error = colors.danger,
        onError = colors.onDanger,
        errorContainer = colors.dangerContainer,
        onErrorContainer = colors.danger,
        scrim = colors.scrim
    )
} else {
    lightColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.surfaceAlt,
        onPrimaryContainer = colors.ink,
        secondary = colors.accent,
        onSecondary = colors.onAccent,
        background = colors.background,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.surfaceAlt,
        onSurfaceVariant = colors.inkSoft,
        outline = colors.divider,
        outlineVariant = colors.divider,
        error = colors.danger,
        onError = colors.onDanger,
        errorContainer = colors.dangerContainer,
        onErrorContainer = colors.danger,
        scrim = colors.scrim
    )
}

@Composable
fun MaktabaAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) MaktabaDarkColors else MaktabaLightColors
    val view = LocalView.current
    val inspecting = LocalInspectionMode.current

    if (!inspecting) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalMaktabaColors provides colors,
        LocalMaktabaSpacing provides MaktabaSpacing()
    ) {
        MaterialTheme(
            colorScheme = materialSchemeFrom(colors),
            typography = MaktabaTypography,
            shapes = MaktabaShapes.material,
            content = content
        )
    }
}
