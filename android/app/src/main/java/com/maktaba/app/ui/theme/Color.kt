package com.maktaba.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour in the app resolves through [MaktabaColors] so that a single palette swap
 * produces the dark theme. Screens must not declare `Color(0x...)` literals - add a role
 * here instead, otherwise the value only works in one theme.
 */
@Immutable
data class MaktabaColors(
    val isDark: Boolean,
    // Grounds and surfaces
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val divider: Color,
    val scrim: Color,
    // Text
    val ink: Color,
    val inkSoft: Color,
    val inkMuted: Color,
    // Brand
    val primary: Color,
    val primaryDark: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val gold: Color,
    val goldSoft: Color,
    // Semantic state - deliberately separate from the brand accents so that "overdue"
    // can never be confused with "this is a green button".
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDanger: Color,
    // Components
    val chipSelected: Color,
    val onChipSelected: Color,
    val chipUnselected: Color,
    val onChipUnselected: Color,
    val navActive: Color,
    val navInactive: Color,
    val skeleton: Color,
    val jacketPaper: Color = Color(0xFFE7DBC5),
    val jacketInk: Color = Color(0xFF58472F),
    val jacketSpine: Color = Color(0xFFBCAA89),
    val jacketOlivePaper: Color = Color(0xFFC5C9B5),
    val jacketOliveInk: Color = Color(0xFF344731),
    val jacketOliveSpine: Color = Color(0xFFA7AF92)
)

/**
 * Original approved journey palette, shared with the light reference boards.
 */
val MaktabaLightColors = MaktabaColors(
    isDark = false,
    background = Color(0xFFFBF5EB),
    backgroundElevated = Color(0xFFFDF9F2),
    surface = Color(0xFFFFFDF8),
    surfaceAlt = Color(0xFFEEE5D6),
    divider = Color(0xFFDED2C0),
    scrim = Color(0x59000000),
    ink = Color(0xFF392C22),
    inkSoft = Color(0xFF716354),
    inkMuted = Color(0xFF716354),
    primary = Color(0xFF7C4B2C),
    primaryDark = Color(0xFF7C4B2C),
    onPrimary = Color(0xFFFFFDF8),
    secondary = Color(0xFF7A6047),
    onSecondary = Color(0xFFFFFFFF),
    accent = Color(0xFF4B6040),
    onAccent = Color(0xFFFFFFFF),
    gold = Color(0xFF82541E),
    goldSoft = Color(0xFFF1E5CC),
    success = Color(0xFF4B6040),
    successContainer = Color(0xFFE5E9DC),
    warning = Color(0xFF82541E),
    warningContainer = Color(0xFFF1E5CC),
    danger = Color(0xFF964C38),
    dangerContainer = Color(0xFFF1DFD5),
    onDanger = Color(0xFFFFFFFF),
    chipSelected = Color(0xFF392C22),
    onChipSelected = Color(0xFFFBF5EB),
    chipUnselected = Color(0xFFEEE5D6),
    onChipUnselected = Color(0xFF716354),
    navActive = Color(0xFF7C4B2C),
    navInactive = Color(0xFF716354),
    skeleton = Color(0xFFEEE5D6)
)

/**
 * Dark palette. Built by shifting the same warm hues onto dark walnut grounds rather than
 * inverting the light values, so the app still reads as Maktaba at night.
 */
val MaktabaDarkColors = MaktabaColors(
    isDark = true,
    background = Color(0xFF211B16),
    backgroundElevated = Color(0xFF261F19),
    surface = Color(0xFF2A231D),
    surfaceAlt = Color(0xFF342B22),
    divider = Color(0xFF46392C),
    scrim = Color(0x8C000000),
    ink = Color(0xFFF2E7D8),
    inkSoft = Color(0xFFC0AD98),
    inkMuted = Color(0xFFC0AD98),
    primary = Color(0xFFD8AA80),
    primaryDark = Color(0xFFD8AA80),
    onPrimary = Color(0xFF24190F),
    secondary = Color(0xFF8C7359),
    onSecondary = Color(0xFFFFFFFF),
    accent = Color(0xFFB5C7A2),
    onAccent = Color(0xFF211B16),
    gold = Color(0xFFE4BC7A),
    goldSoft = Color(0xFF403222),
    success = Color(0xFFB5C7A2),
    successContainer = Color(0xFF303A27),
    warning = Color(0xFFE4BC7A),
    warningContainer = Color(0xFF403222),
    danger = Color(0xFFEEAE91),
    dangerContainer = Color(0xFF412A21),
    onDanger = Color(0xFF211B16),
    chipSelected = Color(0xFFF2E7D8),
    onChipSelected = Color(0xFF211B16),
    chipUnselected = Color(0xFF342B22),
    onChipUnselected = Color(0xFFC0AD98),
    navActive = Color(0xFFD8AA80),
    navInactive = Color(0xFFC0AD98),
    skeleton = Color(0xFF342B22)
)

val LocalMaktabaColors = staticCompositionLocalOf { MaktabaLightColors }
