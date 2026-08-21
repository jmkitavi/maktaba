package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanUrgency

/**
 * Elapsed-versus-remaining track for an active loan. The screen previously announced a
 * "Return Timeline" heading above a single line of text, which showed neither.
 */
@Composable
fun LoanProgressTrack(
    startLabel: String,
    endLabel: String,
    fraction: Float?,
    urgency: LoanUrgency,
    summary: String,
    modifier: Modifier = Modifier
) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val progressColor = urgencyColor(urgency).takeIf { urgency != LoanUrgency.UPCOMING }
        ?: colors.primary
    val clamped = (fraction ?: 0f).coerceIn(0f, 1f)

    Column(modifier = modifier.semantics { contentDescription = summary }) {
        Text(
            summary,
            style = MaterialTheme.typography.titleMedium,
            color = progressColor,
            modifier = Modifier.clearAndSetSemantics {}
        )
        Spacer(Modifier.height(spacing.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceAlt)
                .clearAndSetSemantics {}
        ) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val width = (constraints.maxWidth * clamped).toInt().coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width)
                        )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(progressColor)
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
        }
    }
}
