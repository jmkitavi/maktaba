package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency

/**
 * A shelf card. It now carries loan state, because the whole point of the product is
 * knowing where your books are - and that was previously invisible until you tapped in.
 */
@Composable
fun BookCoverCard(
    book: Book,
    modifier: Modifier = Modifier,
    loan: ActiveLoan? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = MaktabaTheme.colors
    val urgency = loan?.let { LoanTimeFormatter.urgency(it.dueAt) } ?: LoanUrgency.UNKNOWN
    val visuals = statusVisuals(colors, book.status, urgency)
    val dueLine = loan?.let { LoanTimeFormatter.shortRemaining(it.dueAt) }
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
            .clip(MaktabaShapes.medium)
            .background(colors.surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Box {
            BookCoverImage(
                book = book,
                contentScale = ContentScale.Crop,
                decorative = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )
            if (book.status != BookStatus.OWNED || urgency == LoanUrgency.OVERDUE) {
                StatusPill(
                    visuals = visuals,
                    compact = true,
                    decorative = true,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                book.title,
                color = colors.ink,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                book.author,
                color = colors.inkMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (dueLine != null) {
                Text(
                    dueLine,
                    color = urgencyColor(urgency),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (book.format == BookFormat.DIGITAL) {
                Text(
                    "Digital",
                    color = colors.inkMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
