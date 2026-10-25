package com.maktaba.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.maktaba.app.ui.theme.MaktabaTheme

@Composable
fun Modifier.soulBackground(): Modifier {
    val colors = MaktabaTheme.colors
    return drawBehind {
        drawRect(colors.background)
        val center = Offset(size.width * 0.08f, -25.dp.toPx())
        val radius = size.width * 1.15f
        val verticalRadius = 190.dp.toPx()
        if (radius > 0f) {
            withTransform({
                scale(1f, verticalRadius / radius, pivot = center)
            }) {
                drawCircle(
                    Brush.radialGradient(listOf(colors.sage, Color.Transparent), center, radius),
                    radius, center
                )
            }
        }
    }
}
