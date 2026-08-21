package com.maktaba.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Maktaba pairs a serif display face for anything that carries the brand (titles, book
 * names, numerals on the lending code) with a sans for running copy.
 *
 * To bundle a real display serif instead of the platform default, drop the font files in
 * `res/font/` and change [SerifDisplay] to `FontFamily(Font(R.font.<name>, FontWeight.Bold), ...)`.
 * That is the only line that needs to change - nothing else references a family directly.
 */
val SerifDisplay = FontFamily.Serif
val SansBody = FontFamily.SansSerif

private fun serif(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = SerifDisplay,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp
)

private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = SansBody,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp
)

/**
 * The complete scale. Screens read these through `MaterialTheme.typography` - a literal
 * `fontSize = n.sp` in a screen is a bug, because it is how the app drifted onto seven
 * different title sizes in the first place.
 */
val MaktabaTypography = Typography(
    displayLarge = serif(34, 40, FontWeight.Bold),
    displayMedium = serif(30, 36, FontWeight.Bold),
    displaySmall = serif(28, 34, FontWeight.Bold),
    headlineLarge = serif(26, 32, FontWeight.Bold),
    headlineMedium = serif(24, 30, FontWeight.Bold),
    headlineSmall = serif(22, 28, FontWeight.Bold),
    titleLarge = serif(20, 26, FontWeight.Bold),
    titleMedium = serif(18, 24, FontWeight.SemiBold),
    titleSmall = serif(16, 22, FontWeight.SemiBold),
    bodyLarge = sans(16, 22),
    bodyMedium = sans(14, 20),
    bodySmall = sans(12, 16),
    labelLarge = sans(15, 20, FontWeight.Medium),
    labelMedium = sans(13, 18, FontWeight.Medium),
    labelSmall = sans(11, 15, FontWeight.Medium)
)
