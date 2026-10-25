package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency
import java.time.Clock

private val ShelfLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None
)

/**
 * A shelf card. It now carries loan state, because the whole point of the product is
 * knowing where your books are - and that was previously invisible until you tapped in.
 */
@Composable
fun BookCoverCard(
    book: Book,
    modifier: Modifier = Modifier,
    loan: ActiveLoan? = null,
    clock: Clock = Clock.systemDefaultZone(),
    onClick: (() -> Unit)? = null
) {
    val colors = MaktabaTheme.colors
    val titleSize = if (LocalConfiguration.current.screenWidthDp <= 330) 14.sp else 13.sp
    val urgency = loan?.let { LoanTimeFormatter.urgency(it.dueAt, clock) } ?: LoanUrgency.UNKNOWN
    val visuals = statusVisuals(colors, book.status)
    val dueLine = loan?.let { LoanTimeFormatter.shortRemaining(it.dueAt, clock) }
    val counterparty = loan?.counterpartyName?.takeIf { it.isNotBlank() }

    val description = buildString {
        append(book.title)
        append(", by ")
        append(book.author)
        append(". ")
        append(visuals.label)
        if (counterparty != null) {
            append(if (book.status == BookStatus.LENT_OUT) ", to " else ", from ")
            append(counterparty)
        }
        if (dueLine != null) {
            append(". ")
            append(dueLine)
        }
        if (book.format == BookFormat.DIGITAL) append(". Digital edition")
    }

    Column(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(4.dp, MaktabaShapes.cover).clip(MaktabaShapes.cover).background(colors.surfaceAlt)) {
            BookCoverImage(
                book = book,
                contentScale = ContentScale.Fit,
                decorative = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
        }
        Box(Modifier.heightIn(min = 30.dp).padding(top = 7.dp, bottom = 4.dp)) {
                StatusPill(
                    visuals = visuals,
                    compact = true,
                    decorative = true,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
        }
        Column(Modifier.fillMaxWidth()) {
            Text(
                book.title,
                color = colors.ink,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = titleSize, lineHeight = titleSize * 1.35f, lineHeightStyle = ShelfLineHeight
                ),
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                book.author,
                color = colors.inkMuted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp, lineHeight = 15.sp, lineHeightStyle = ShelfLineHeight
                ),
                modifier = Modifier.padding(top = 5.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                if (dueLine != null) {
                    Icon(
                        if (urgency == LoanUrgency.OVERDUE) Icons.Outlined.WarningAmber else Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = urgencyColor(urgency),
                        modifier = Modifier.padding(top = 2.dp).size(10.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    dueLine ?: when (book.format) {
                        BookFormat.DIGITAL -> "Digital edition"
                        BookFormat.PHYSICAL -> "Physical edition"
                        BookFormat.UNKNOWN -> "Edition not specified"
                    },
                    color = if (dueLine != null) urgencyColor(urgency) else colors.inkMuted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp, lineHeight = 15.sp, lineHeightStyle = ShelfLineHeight
                    ),
                    modifier = Modifier.weight(1f),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
