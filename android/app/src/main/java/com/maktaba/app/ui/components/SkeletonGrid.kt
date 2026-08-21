package com.maktaba.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme

/**
 * Placeholder shelf shown while the first Firestore snapshot is in flight. Without it the
 * library screen said "No books in this filter yet" to every user on every cold start.
 */
@Composable
fun BookGridSkeleton(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    itemCount: Int = 9,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val colors = MaktabaTheme.colors
    val pulse = if (LocalInspectionMode.current) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "skeletonAlpha"
        )
        value
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.semantics { contentDescription = "Loading your library" },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false
    ) {
        items((0 until itemCount).toList()) {
            Column(
                Modifier
                    .clip(MaktabaShapes.medium)
                    .background(colors.surface)
                    .alpha(pulse)
                    .clearAndSetSemantics {}
            ) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .background(colors.skeleton)
                )
                Spacer(Modifier.height(8.dp))
                Spacer(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.skeleton)
                )
                Spacer(Modifier.height(6.dp))
                Spacer(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.skeleton)
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
