package com.maktaba.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BookHavenColorScheme = lightColorScheme(
    primary = WoodBrown,
    onPrimary = CreamBackgroundLight,
    secondary = OliveGreen,
    onSecondary = CreamBackgroundLight,
    background = CreamBackground,
    onBackground = InkBrown,
    surface = SurfaceCard,
    onSurface = InkBrown,
    surfaceVariant = SurfaceCardAlt,
    onSurfaceVariant = InkBrownSoft
)

private val BookHavenTypography = Typography(
    displayLarge = BookHavenType.displayLarge,
    titleLarge = BookHavenType.titleLarge,
    titleMedium = BookHavenType.titleMedium,
    bodyLarge = BookHavenType.bodyLarge,
    bodyMedium = BookHavenType.bodyMedium,
    labelMedium = BookHavenType.label
)

@Composable
fun BookHavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BookHavenColorScheme,
        typography = BookHavenTypography,
        content = content
    )
}
