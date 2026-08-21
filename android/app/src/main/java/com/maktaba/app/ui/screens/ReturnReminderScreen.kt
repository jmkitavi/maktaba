package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.GreenButton
import com.maktaba.app.ui.components.LoanProgressTrack
import com.maktaba.app.ui.components.MaktabaCard
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.StatusPill
import com.maktaba.app.ui.components.TextActionButton
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.components.statusVisuals
import com.maktaba.app.ui.components.urgencyColor
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency

@Composable
fun ReturnReminderScreen(navController: NavHostController, bookId: String) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing

    val book = LibraryRepository.bookById(bookId)
    val loan = LibraryRepository.activeLoanFor(bookId)
    if (book == null || loan == null) {
        UnavailableState(
            title = "Reminder unavailable",
            message = "This loan reminder is no longer available.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }

    val urgency = LoanTimeFormatter.urgency(loan.dueAt)
    val visuals = statusVisuals(colors, book.status, urgency)
    val overdue = urgency == LoanUrgency.OVERDUE

    MaktabaScaffold(
        topBar = { ScreenTopBar(title = "Reminder", onBack = { navController.popBackStack() }) }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
        ) {
            Spacer(Modifier.height(spacing.sm))
            Text(
                if (overdue) "This one is overdue" else "A gentle reminder",
                style = MaterialTheme.typography.headlineSmall,
                color = if (overdue) colors.danger else colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() }
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                if (overdue) {
                    "The agreed return date has passed. Good stories are meant to keep moving."
                } else {
                    "Good stories are meant to be shared - and returned."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(spacing.md))
            MaktabaCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    BookCoverImage(
                        book = book,
                        contentScale = ContentScale.Crop,
                        decorative = true,
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(0.68f)
                            .clip(MaktabaShapes.cover)
                    )
                    Spacer(Modifier.width(spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        StatusPill(visuals = visuals, compact = true)
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            LoanTimeFormatter.shortRemaining(loan.dueAt),
                            style = MaterialTheme.typography.titleSmall,
                            color = urgencyColor(urgency)
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceAlt),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.CalendarToday,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Due date",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.inkMuted
                                )
                                Text(
                                    LoanTimeFormatter.formatDate(loan.dueAt),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.ink
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.sm))
            MaktabaCard {
                LoanProgressTrack(
                    startLabel = loan.acceptedAt
                        ?.let { LoanTimeFormatter.formatDate(it) }
                        ?: "Lent",
                    endLabel = LoanTimeFormatter.formatDate(loan.dueAt),
                    fraction = LoanTimeFormatter.elapsedFraction(loan.acceptedAt, loan.dueAt),
                    urgency = urgency,
                    summary = LoanTimeFormatter.remaining(loan.dueAt)
                )
            }

            Spacer(Modifier.height(spacing.lg))
            GreenButton(
                text = "Open this loan",
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { navController.navigate(Routes.ActiveLoan.createRoute(book.id)) }
            )
            Spacer(Modifier.height(spacing.xxs))
            TextActionButton(text = "Not now", onClick = { navController.popBackStack() })

            Spacer(Modifier.height(spacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaktabaShapes.small)
                    .background(colors.surfaceAlt)
                    .padding(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Need more time?",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink
                    )
                    Text(
                        "Message the other person and agree a new return date between you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Preview(showBackground = true, name = "Return reminder")
@Composable
private fun ReturnReminderScreenPreview() {
    BookHavenTheme {
        ReturnReminderScreen(navController = rememberNavController(), bookId = "preview")
    }
}
