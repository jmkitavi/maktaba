package com.maktaba.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun SoulWelcomeIllustration(@DrawableRes resource: Int, modifier: Modifier = Modifier) {
    Image(painterResource(resource), contentDescription = null, contentScale = ContentScale.Fit,
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.radialGradient(
                    0f to Color.Black, 0.55f to Color.Black, 0.86f to Color.Transparent,
                    center = Offset(size.width * 0.48f, size.height * 0.5f),
                    radius = size.width * 0.69f
                ), blendMode = BlendMode.DstIn)
            })
}
