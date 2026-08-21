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
    val skeleton: Color
)

/**
 * Light palette, derived from the Maktaba design mockups.
 *
 * `inkMuted` was #8B7A6A, which measures ~4.07:1 against [surface] and therefore failed
 * WCAG AA for the 11-13sp text it is used for. #6F5E4C measures ~6.1:1 on the same ground
 * and keeps the warmth of the original.
 */
val MaktabaLightColors = MaktabaColors(
    isDark = false,
    background = Color(0xFFFBF1E3),
    backgroundElevated = Color(0xFFFDF7EF),
    surface = Color(0xFFFFFDF8),
    surfaceAlt = Color(0xFFF6EDE0),
    divider = Color(0xFFE3D3BC),
    scrim = Color(0x59000000),
    ink = Color(0xFF3B2A1D),
    inkSoft = Color(0xFF5A4636),
    inkMuted = Color(0xFF6F5E4C),
    primary = Color(0xFF7C4B2C),
    primaryDark = Color(0xFF5E3A21),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF7A6047),
    onSecondary = Color(0xFFFFFFFF),
    accent = Color(0xFF3F5233),
    onAccent = Color(0xFFFFFFFF),
    gold = Color(0xFF9A7A12),
    goldSoft = Color(0xFFDCC79A),
    success = Color(0xFF3D6631),
    successContainer = Color(0xFFDCEBD3),
    warning = Color(0xFF8A5300),
    warningContainer = Color(0xFFF8E7CD),
    danger = Color(0xFF9F2018),
    dangerContainer = Color(0xFFF9E2E0),
    onDanger = Color(0xFFFFFFFF),
    chipSelected = Color(0xFF3E2B1E),
    onChipSelected = Color(0xFFFBF1E3),
    chipUnselected = Color(0xFFEFE1CB),
    onChipUnselected = Color(0xFF5F4F3D),
    navActive = Color(0xFF7C4B2C),
    navInactive = Color(0xFF7C6B57),
    skeleton = Color(0xFFEFE3D2)
)

/**
 * Dark palette. Built by shifting the same warm hues onto dark walnut grounds rather than
 * inverting the light values, so the app still reads as Maktaba at night.
 */
val MaktabaDarkColors = MaktabaColors(
    isDark = true,
    background = Color(0xFF171310),
    backgroundElevated = Color(0xFF1F1A16),
    surface = Color(0xFF221C17),
    surfaceAlt = Color(0xFF2C241D),
    divider = Color(0xFF3B3128),
    scrim = Color(0x8C000000),
    ink = Color(0xFFF2E7D8),
    inkSoft = Color(0xFFD6C7B3),
    inkMuted = Color(0xFFA2917C),
    primary = Color(0xFFA5673F),
    primaryDark = Color(0xFF7C4B2C),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8C7359),
    onSecondary = Color(0xFFFFFFFF),
    accent = Color(0xFF5A7548),
    onAccent = Color(0xFFFFFFFF),
    gold = Color(0xFFD8B45A),
    goldSoft = Color(0xFF4A3D22),
    success = Color(0xFF8CC17A),
    successContainer = Color(0xFF23331C),
    warning = Color(0xFFE0A44F),
    warningContainer = Color(0xFF3A2A14),
    danger = Color(0xFFF08A80),
    dangerContainer = Color(0xFF3B1F1C),
    onDanger = Color(0xFF3B1F1C),
    chipSelected = Color(0xFFE8D8C2),
    onChipSelected = Color(0xFF2A1F16),
    chipUnselected = Color(0xFF322922),
    onChipUnselected = Color(0xFFCBBBA5),
    navActive = Color(0xFFD9A273),
    navInactive = Color(0xFF9C8B78),
    skeleton = Color(0xFF2A231D)
)

val LocalMaktabaColors = staticCompositionLocalOf { MaktabaLightColors }
