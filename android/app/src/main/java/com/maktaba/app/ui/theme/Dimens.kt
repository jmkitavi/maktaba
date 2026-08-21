package com.maktaba.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An 8pt-derived spacing scale. Screens previously built vertical rhythm from ad-hoc
 * Spacer heights ranging over fourteen different values; everything now snaps to these.
 */
@Immutable
data class MaktabaSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    /** The standard horizontal gutter for screen content. */
    val gutter: Dp = 20.dp
)

val LocalMaktabaSpacing = staticCompositionLocalOf { MaktabaSpacing() }

/**
 * Four corner radii replace the ten that were in use. `pill` is used for buttons and chips.
 */
object MaktabaShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
    val cover = RoundedCornerShape(8.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    val material = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = small,
        medium = medium,
        large = large,
        extraLarge = RoundedCornerShape(32.dp)
    )
}

/** Minimum touch target, per the Material accessibility guidance. */
val MinTouchTarget = 48.dp
