package com.maktaba.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.maktaba.app.R
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.statusVisuals
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

@Composable
internal fun ActiveLoanPresentation(
    book: Book,
    loan: ActiveLoan,
    stage: ReturnStage,
    onBack: () -> Unit,
    onReminder: () -> Unit,
    onReturn: () -> Unit,
    onBook: () -> Unit,
    onReader: (() -> Unit)?,
    now: Instant
) {
    val colors = MaktabaTheme.colors
    val clock = Clock.fixed(now, ZoneId.systemDefault())
    MaktabaScaffold { padding ->
        // Scaffold owns the safe area; the scrolling top bar must not apply it a second time.
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .testTag("active_loan_scroll").verticalScroll(rememberScrollState())
        ) {
            ScreenTopBar(
                title = "Active Loan", onBack = onBack,
                modifier = Modifier.testTag("active_loan_header"),
                actions = {
                    Image(
                        painterResource(R.drawable.illus_loan_shelf), null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(57.dp).height(38.dp)
                            .graphicsLayer { alpha = if (colors.isDark) 0.55f else 0.7f }
                    )
                }
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = MaktabaTheme.spacing.gutter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ActiveLoanBook(book)
                ActiveLoanReader(loan, onReader)
                ActiveLoanDue(loan, clock)
                ActiveLoanTimeline(loan, clock)
                if (stage != ReturnStage.REQUEST) {
                    Spacer(Modifier.height(12.dp))
                    LoanFeedback(when (stage) {
                        ReturnStage.WAITING -> "Your part is confirmed. Waiting for ${loan.counterpartyName} to confirm the handover. The loan is still open."
                        ReturnStage.CONFIRM -> "${loan.counterpartyName} says the book is back with its owner. Review the handover before closing the loan."
                        ReturnStage.UNAVAILABLE -> "This return cannot be changed right now. Refresh the loan before continuing."
                        ReturnStage.REQUEST, ReturnStage.COMPLETE -> null
                    })
                }
                Row(
                    Modifier.padding(top = 4.dp).defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button, onClick = onBook).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("View the Book", color = colors.primary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = colors.primary, modifier = Modifier.size(14.dp))
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp).testTag("active_loan_actions"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (loan.isLender && stage == ReturnStage.REQUEST) {
                        PrimaryButton("Send a Reminder", onClick = onReminder, leadingIcon = {
                            Icon(Icons.AutoMirrored.Outlined.Send, null, modifier = Modifier.size(17.dp))
                        })
                    }
                    val returnLabel = when (stage) {
                        ReturnStage.WAITING -> "Waiting for Confirmation"
                        ReturnStage.CONFIRM -> "Review the Return"
                        ReturnStage.REQUEST, ReturnStage.COMPLETE, ReturnStage.UNAVAILABLE -> "Start a Return"
                    }
                    if (loan.isLender && stage == ReturnStage.REQUEST) {
                        LoanSecondaryButton(returnLabel, onClick = onReturn)
                    } else {
                        PrimaryButton(returnLabel,
                            enabled = stage == ReturnStage.REQUEST || stage == ReturnStage.CONFIRM,
                            onClick = onReturn)
                    }
                }
                Text(
                    when (stage) {
                        ReturnStage.WAITING -> "The other reader needs to confirm the handover."
                        ReturnStage.CONFIRM -> "Check the handover before closing this loan."
                        ReturnStage.COMPLETE -> "This loan is already closed."
                        ReturnStage.UNAVAILABLE -> "Refresh the loan before continuing."
                        ReturnStage.REQUEST -> "Both readers confirm when the book is back."
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 7.dp),
                    textAlign = TextAlign.Center, color = colors.inkMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 14.4.sp,
                        lineHeightStyle = LoanLineHeightStyle)
                )
            }
            val art = painterResource(R.drawable.illus_active_loan_nook)
            val darkArtFilter = remember {
                ColorFilter.colorMatrix(ColorMatrix().apply {
                    setToSaturation(0.86f)
                    for (row in 0..2) for (column in 0..4) this[row, column] *= 0.85f
                })
            }
            Image(
                art, null, contentScale = ContentScale.Fit,
                colorFilter = if (colors.isDark) darkArtFilter else null,
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(art.intrinsicSize.width / art.intrinsicSize.height)
                    .testTag("active_loan_art")
            )
        }
    }
}

@Composable
private fun ActiveLoanBook(book: Book) {
    val colors = MaktabaTheme.colors
    val status = statusVisuals(colors, book.status)
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp).drawBehind {
            drawRect(Brush.radialGradient(
                listOf(colors.sage.copy(alpha = 0.8f), Color.Transparent),
                center = Offset(size.width / 2, size.height / 3), radius = size.width * 0.65f
            ))
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(5.dp)).background(status.container)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(status.icon, null, tint = status.content, modifier = Modifier.size(11.dp))
            Text(status.label, color = status.content, style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
                lineHeightStyle = LoanLineHeightStyle))
        }
        Spacer(Modifier.height(8.dp))
        ActiveLoanCover(book)
        Text(
            book.title, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).semantics { heading() },
            textAlign = TextAlign.Center, color = colors.ink,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 25.sp, lineHeight = 28.75.sp, letterSpacing = (-0.6).sp,
                lineHeightStyle = LoanLineHeightStyle)
        )
        Text(
            "by ${book.author}", modifier = Modifier.padding(top = 3.dp),
            textAlign = TextAlign.Center, color = colors.inkMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp,
                lineHeightStyle = LoanLineHeightStyle)
        )
    }
}

@Composable
private fun ActiveLoanCover(book: Book) {
    var ratio by remember(book.coverUrl) { mutableFloatStateOf(2f / 3f) }
    Box(
        Modifier.width(100.dp).aspectRatio(ratio).shadow(6.dp, MaktabaShapes.cover)
            .clip(MaktabaShapes.cover).testTag("active_loan_cover")
    ) {
        if (book.coverUrl.isBlank()) {
            BookCoverImage(book, Modifier.fillMaxSize(), decorative = true)
        } else {
            SubcomposeAsyncImage(
                model = book.coverUrl, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = {
                    val drawable = it.result.drawable
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        ratio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
                    }
                },
                loading = { BookCoverImage(book.copy(coverUrl = ""), Modifier.fillMaxSize(), decorative = true) },
                error = { BookCoverImage(book.copy(coverUrl = ""), Modifier.fillMaxSize(), decorative = true) }
            )
        }
    }
}

@Composable
private fun ActiveLoanReader(loan: ActiveLoan, onReader: (() -> Unit)?) {
    val colors = MaktabaTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).then(
            if (onReader != null) Modifier.clickable(role = Role.Button,
                onClickLabel = "View Reader Profile", onClick = onReader) else Modifier
        ).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(Modifier.size(31.dp).clip(CircleShape).background(colors.terracotta),
            contentAlignment = Alignment.Center) {
            Text(loan.counterpartyName.take(1), color = colors.terraAction,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
        }
        Column(Modifier.weight(1f)) {
            Text(if (loan.isLender) "Borrower" else "Lender", color = colors.inkMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 15.sp,
                    lineHeightStyle = LoanLineHeightStyle))
            Text(loan.counterpartyName, color = colors.ink,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp,
                    lineHeightStyle = LoanLineHeightStyle))
        }
        if (onReader != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.inkMuted,
                modifier = Modifier.size(15.dp))
        }
    }
    HorizontalDivider(color = colors.divider)
}

@Composable
private fun ActiveLoanDue(loan: ActiveLoan, clock: Clock) {
    val colors = MaktabaTheme.colors
    val days = LoanTimeFormatter.daysUntilDue(loan.dueAt, clock)
    val overdue = days != null && days < 0
    val tint = if (overdue) colors.danger else colors.primary
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text("—", color = colors.sageLine)
        Text(if (overdue) "Past the return date" else "Time to return",
            modifier = Modifier.padding(horizontal = 10.dp), color = tint,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp,
                lineHeightStyle = LoanLineHeightStyle))
        Text("—", color = colors.sageLine)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("active_loan_due"),
        color = if (overdue) colors.terracotta else colors.sage,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (overdue) colors.danger else colors.sageLine)
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 68.dp).padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (days != null && days != 0L) {
                Text(abs(days).toString().padStart(2, '0'), color = tint,
                    modifier = Modifier.padding(end = 17.dp),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 38.sp,
                        letterSpacing = (-1.5).sp, lineHeightStyle = LoanLineHeightStyle))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(when {
                    days == null -> "Due date unavailable"
                    days == 0L -> "Due today"
                    overdue -> if (days == -1L) "day overdue" else "days overdue"
                    days == 1L -> "day left"
                    else -> "days left"
                }, color = tint, style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp, lineHeight = 25.sp, lineHeightStyle = LoanLineHeightStyle))
                if (loan.dueAt != null) {
                    Text("Agreed return · ${LoanTimeFormatter.formatDate(loan.dueAt)}",
                        color = colors.inkMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 16.sp,
                            lineHeightStyle = LoanLineHeightStyle))
                }
            }
        }
    }
}

@Composable
private fun ActiveLoanTimeline(loan: ActiveLoan, clock: Clock) {
    val colors = MaktabaTheme.colors
    val fraction = LoanTimeFormatter.elapsedFraction(loan.acceptedAt, loan.dueAt, clock)
    val tint = if ((LoanTimeFormatter.daysUntilDue(loan.dueAt, clock) ?: 0) < 0) colors.danger else colors.primary
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (fraction != null) {
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(MaktabaShapes.small).background(colors.divider)
                    .testTag("active_loan_progress").semantics {
                        contentDescription = "Agreed loan period elapsed"
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    }
            ) {
                if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(tint))
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActiveLoanDate("Handed over", LoanTimeFormatter.formatDate(loan.acceptedAt), Modifier.weight(1f), TextAlign.Start)
            ActiveLoanDate("Due back", LoanTimeFormatter.formatDate(loan.dueAt), Modifier.weight(1f), TextAlign.End)
        }
    }
}

@Composable
private fun ActiveLoanDate(label: String, value: String, modifier: Modifier, alignment: TextAlign) {
    Column(modifier) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = alignment,
            color = MaktabaTheme.colors.inkMuted, style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 9.sp, lineHeight = 15.sp, lineHeightStyle = LoanLineHeightStyle))
        Text(value, modifier = Modifier.fillMaxWidth(), textAlign = alignment,
            color = MaktabaTheme.colors.ink, style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium,
                lineHeightStyle = LoanLineHeightStyle))
    }
}
