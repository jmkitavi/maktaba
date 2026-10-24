package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookFormat
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.MaktabaCard
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.nav.Routes
import androidx.navigation.NavHostController
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// CSS line boxes retain their first and last leading; Compose otherwise trims both.
internal val LoanLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

internal fun openJourneyLoan(navController: NavHostController, bookId: String) {
    val route = Routes.ActiveLoan.createRoute(bookId)
    if (!navController.popBackStack(route, false)) navController.navigate(route) { launchSingleTop = true }
}

@Composable
internal fun LoanPage(
    title: String,
    onBack: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = MaktabaTheme.spacing
    MaktabaScaffold(topBar = { ScreenTopBar(title = title, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaktabaTheme.colors.background)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = spacing.gutter, end = spacing.gutter, top = 12.dp, bottom = 26.dp)
            ) {
                content()
            }
            HorizontalDivider(color = MaktabaTheme.colors.divider)
            Column(
                Modifier.fillMaxWidth().background(MaktabaTheme.colors.backgroundElevated)
                    .padding(start = spacing.gutter, end = spacing.gutter, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = footer
            )
        }
    }
}

@Composable
internal fun LoanBookSummary(book: Book, compact: Boolean = false) {
    val colors = MaktabaTheme.colors
    MaktabaCard(
        modifier = Modifier.border(BorderStroke(1.dp, colors.divider), MaktabaShapes.medium),
        contentPadding = PaddingValues(if (compact) 13.dp else 15.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BookCoverImage(
                book = book,
                contentScale = ContentScale.Fit,
                decorative = true,
                modifier = Modifier.width(if (compact) 42.dp else 64.dp).aspectRatio(2f / 3f)
                    .clip(MaktabaShapes.cover)
            )
            Spacer(Modifier.width(if (compact) 12.dp else 14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = if (compact) 17.sp else 20.sp,
                        lineHeight = if (compact) 21.25.sp else 24.sp,
                        letterSpacing = (-0.3).sp,
                        lineHeightStyle = LoanLineHeightStyle
                    ),
                    color = colors.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
                Text(book.author, style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = if (compact) 10.sp else 11.sp,
                    lineHeight = if (compact) 16.sp else 17.6.sp,
                    lineHeightStyle = LoanLineHeightStyle
                ), color = colors.inkMuted)
                Spacer(Modifier.height(if (compact) 5.dp else 10.dp))
                Text(
                    when (book.format) {
                        BookFormat.DIGITAL -> "Digital edition"
                        BookFormat.PHYSICAL -> "Physical edition"
                        BookFormat.UNKNOWN -> "Book edition"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp, lineHeight = 14.4.sp, lineHeightStyle = LoanLineHeightStyle
                    ),
                    color = colors.inkSoft
                )
            }
        }
    }
}

@Composable
internal fun LoanTimeline(loan: ActiveLoan, now: Instant) {
    val clock = Clock.fixed(now, ZoneId.systemDefault())
    val colors = MaktabaTheme.colors
    val urgency = LoanTimeFormatter.urgency(loan.dueAt, clock)
    val overdue = urgency == com.maktaba.app.util.LoanUrgency.OVERDUE
    val dueSoon = urgency == com.maktaba.app.util.LoanUrgency.DUE_SOON ||
        urgency == com.maktaba.app.util.LoanUrgency.DUE_TODAY
    val tint = when {
        overdue -> colors.danger
        dueSoon -> colors.warning
        else -> colors.success
    }
    val summary = LoanTimeFormatter.shortRemaining(loan.dueAt, clock)
    val start = loan.acceptedAt?.atZone(clock.zone)?.toLocalDate()
    val end = loan.dueAt?.atZone(clock.zone)?.toLocalDate()
    val totalDays = if (start != null && end != null) ChronoUnit.DAYS.between(start, end) else 0L
    // Follow actual agreed calendar dates, never the prototype's illustrative percentage.
    val fraction = if (start != null && totalDays > 0) {
        (ChronoUnit.DAYS.between(start, now.atZone(clock.zone).toLocalDate()).toFloat() / totalDays)
            .coerceIn(0f, 1f)
    } else 0f
    Column(Modifier.padding(top = 22.dp, bottom = 5.dp).semantics { contentDescription = summary }) {
        HorizontalDivider(color = colors.divider)
        Spacer(Modifier.height(18.dp))
        Text(
            "RETURN TIMELINE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold,
                lineHeightStyle = LoanLineHeightStyle
            ),
            color = colors.inkMuted
        )
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (overdue) Icons.Outlined.WarningAmber else Icons.Outlined.Schedule,
                contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp, lineHeight = 20.8.sp, lineHeightStyle = LoanLineHeightStyle
            ),
                color = tint)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(MaktabaShapes.small).background(colors.surfaceAlt)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }) {
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(tint))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimelineDate("Handed over", LoanTimeFormatter.formatDate(loan.acceptedAt), TextAlign.Start)
            TimelineDate("Due back", LoanTimeFormatter.formatDate(loan.dueAt), TextAlign.End)
        }
    }
}

@Composable
private fun TimelineDate(label: String, value: String, alignment: TextAlign) {
    Column(horizontalAlignment = if (alignment == TextAlign.End) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 9.sp, lineHeight = 16.2.sp, lineHeightStyle = LoanLineHeightStyle
        ),
            color = MaktabaTheme.colors.inkMuted, textAlign = alignment)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 10.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
            lineHeightStyle = LoanLineHeightStyle
        ), color = MaktabaTheme.colors.ink, textAlign = alignment)
    }
}

@Composable
internal fun LoanIntro(title: String, message: String, compactMessage: Boolean = false, success: Boolean = false) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        title, style = MaterialTheme.typography.headlineLarge.copy(
            fontSize = 27.sp, lineHeight = 31.05.sp, letterSpacing = (-0.6).sp,
            lineHeightStyle = LoanLineHeightStyle
        ), color = MaktabaTheme.colors.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
    Spacer(Modifier.height(if (compactMessage) 12.dp else 13.dp))
    Text(
        message, style = MaterialTheme.typography.bodySmall.copy(
            fontSize = if (success) 10.sp else if (compactMessage) 11.sp else 12.sp,
            lineHeight = if (success) 17.sp else if (compactMessage) 19.8.sp else 21.6.sp,
            lineHeightStyle = LoanLineHeightStyle
        ), color = if (success) MaktabaTheme.colors.success else MaktabaTheme.colors.inkSoft,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
    }
}

@Composable
internal fun LoanSecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        shape = MaktabaShapes.button,
        border = BorderStroke(1.dp, MaktabaTheme.colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaktabaTheme.colors.backgroundElevated,
            contentColor = MaktabaTheme.colors.primary
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun LoanNote(message: String, icon: ImageVector) {
    val colors = MaktabaTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.backgroundElevated)
            .border(1.dp, colors.divider, shape).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary,
            modifier = Modifier.padding(top = 3.dp).size(15.dp))
        Text(message, style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 11.sp, lineHeight = 19.8.sp, lineHeightStyle = LoanLineHeightStyle
        ),
            color = colors.inkMuted, modifier = Modifier.weight(1f))
    }
}
@Composable
internal fun LoanFeedback(message: String?, error: Boolean = false) {
    if (message != null) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaktabaTheme.colors.danger else MaktabaTheme.colors.inkSoft,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
internal fun LoanLoadFailure(message: String, onBack: () -> Unit, onLibrary: () -> Unit) {
    LoanPage(
        title = "Loan",
        onBack = onBack,
        footer = {
            PrimaryButton("Try Again", onClick = { com.maktaba.app.data.LibraryRepository.retryLoads() })
            LoanSecondaryButton("Back to My Library", onClick = onLibrary)
        }
    ) {
        LoanIntro("Could Not Load This Loan", message)
    }
}
