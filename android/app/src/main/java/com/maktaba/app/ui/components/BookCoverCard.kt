package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
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

private val ShelfLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None
)

@Composable
fun BookCoverCard(
    book: Book,
    modifier: Modifier = Modifier,
    loan: ActiveLoan? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = MaktabaTheme.colors
    val titleSize = if (LocalConfiguration.current.screenWidthDp <= 330) 14.sp else 13.sp
    val visuals = statusVisuals(colors, book.status)
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
            if (book.format != BookFormat.PHYSICAL) {
                Text(
                    if (book.format == BookFormat.DIGITAL) "Digital edition" else "Edition not specified",
                    color = colors.inkMuted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp, lineHeight = 15.sp, lineHeightStyle = ShelfLineHeight
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
