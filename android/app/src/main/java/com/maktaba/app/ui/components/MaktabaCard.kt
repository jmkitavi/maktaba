package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme

/**
 * The cream surface card that the app draws a dozen times. Owning it in one place is what
 * keeps corner radius and inner padding from drifting apart screen by screen.
 */
@Composable
fun MaktabaCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaktabaShapes.medium,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaktabaTheme.colors.surface)
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(contentPadding),
        content = content
    )
}
