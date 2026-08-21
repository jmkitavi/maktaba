package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.maktaba.app.data.BookStatus
import com.maktaba.app.ui.theme.MaktabaColors
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanUrgency

/** Resolved presentation for a book's current state. */
data class StatusVisuals(
    val label: String,
    val icon: ImageVector,
    val content: Color,
    val container: Color
)

/**
 * A book that is out on loan must never be drawn with a success check - that was reading
 * as "all good" for books the owner no longer had, including overdue ones.
 */
fun statusVisuals(
    colors: MaktabaColors,
    status: BookStatus,
    urgency: LoanUrgency = LoanUrgency.UNKNOWN
): StatusVisuals {
    if (urgency == LoanUrgency.OVERDUE) {
        return StatusVisuals("Overdue", Icons.Filled.ErrorOutline, colors.danger, colors.dangerContainer)
    }
    if (urgency == LoanUrgency.DUE_TODAY || urgency == LoanUrgency.DUE_SOON) {
        val label = if (status == BookStatus.LENT_OUT) "Due back soon" else "Due soon"
        return StatusVisuals(label, Icons.Filled.ScheduleSend, colors.warning, colors.warningContainer)
    }
    return when (status) {
        BookStatus.OWNED ->
            StatusVisuals("On your shelf", Icons.Filled.CheckCircle, colors.success, colors.successContainer)
        BookStatus.LENT_OUT ->
            StatusVisuals("Lent out", Icons.Filled.CallMade, colors.primary, colors.chipUnselected)
        BookStatus.BORROWED ->
            StatusVisuals("Borrowed", Icons.Filled.CallReceived, colors.accent, colors.chipUnselected)
    }
}

@Composable
fun StatusPill(
    visuals: StatusVisuals,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** True when a parent already announces this state to screen readers. */
    decorative: Boolean = false
) {
    val shape = MaktabaShapes.pill
    Row(
        modifier = modifier
            .clip(shape)
            .background(visuals.container)
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 3.dp else 6.dp
            )
            .then(if (decorative) Modifier.clearAndSetSemantics {} else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            visuals.icon,
            contentDescription = null,
            tint = visuals.content,
            modifier = Modifier.size(if (compact) 12.dp else 15.dp)
        )
        Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
        Text(
            visuals.label,
            color = visuals.content,
            style = if (compact) MaterialTheme.typography.labelSmall
            else MaterialTheme.typography.labelMedium
        )
    }
}

/** Colour for a due-date line, so overdue can never be typeset like on-time. */
@Composable
fun urgencyColor(urgency: LoanUrgency): Color {
    val colors = MaktabaTheme.colors
    return when (urgency) {
        LoanUrgency.OVERDUE -> colors.danger
        LoanUrgency.DUE_TODAY, LoanUrgency.DUE_SOON -> colors.warning
        else -> colors.inkMuted
    }
}
