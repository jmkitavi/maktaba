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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.LoanProgressTrack
import com.maktaba.app.ui.components.MaktabaCard
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.SecondaryButton
import com.maktaba.app.ui.components.StatusPill
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.components.statusVisuals
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ActiveLoanScreen(navController: NavHostController, bookId: String) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var reminderLoading by remember { mutableStateOf(false) }

    val book = LibraryRepository.bookById(bookId)
    val loan = LibraryRepository.activeLoanFor(bookId)
    // Do not claim the loan is missing while its snapshot is still in flight.
    if ((book == null || loan == null) &&
        (!LibraryRepository.hasLoadedLibrary || !LibraryRepository.hasLoadedLoans)
    ) {
        LoadingState("Loading this loan")
        return
    }
    if (book == null || loan == null) {
        UnavailableState(
            title = "Loan unavailable",
            message = "We could not find this loan. It may already have been closed.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }

    val counterpartyLabel = if (loan.isLender) "Borrower" else "Lender"
    val urgency = LoanTimeFormatter.urgency(loan.dueAt)
    val visuals = statusVisuals(colors, book.status, urgency)
    val returnRequested = loan.status == LoanStatus.RETURN_REQUESTED
    val waitingForConfirmation = returnRequested && loan.returnRequestedByCurrentUser
    val returnActionLabel = when {
        waitingForConfirmation -> "Waiting for confirmation"
        returnRequested -> "Confirm the return"
        else -> "Request the return"
    }

    MaktabaScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { ScreenTopBar(title = "Active loan", onBack = { navController.popBackStack() }) }
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
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                        Spacer(Modifier.height(spacing.xs))
                        StatusPill(visuals = visuals, compact = true)
                        Spacer(Modifier.height(spacing.sm))
                        DetailRow(Icons.Filled.Person, counterpartyLabel, loan.counterpartyName)
                        Spacer(Modifier.height(spacing.xs))
                        DetailRow(
                            Icons.Filled.CalendarToday,
                            "Due back",
                            LoanTimeFormatter.formatDate(loan.dueAt)
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.lg))

            Text(
                "Return timeline",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(spacing.xs))
            MaktabaCard {
                LoanProgressTrack(
                    startLabel = loan.acceptedAt
                        ?.let { "Lent ${LoanTimeFormatter.formatDate(it)}" }
                        ?: "Lent",
                    endLabel = LoanTimeFormatter.formatDate(loan.dueAt),
                    fraction = LoanTimeFormatter.elapsedFraction(loan.acceptedAt, loan.dueAt),
                    urgency = urgency,
                    summary = LoanTimeFormatter.remaining(loan.dueAt)
                )
            }

            if (returnRequested) {
                Spacer(Modifier.height(spacing.sm))
                Text(
                    if (waitingForConfirmation) {
                        "You have asked for this book back. Waiting for the other person to confirm."
                    } else {
                        "The other person marked this book as returned. Confirm to close the loan."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSoft
                )
            }

            Spacer(Modifier.height(spacing.lg))

            if (loan.isLender) {
                PrimaryButton(
                    text = "Send a reminder",
                    loading = reminderLoading,
                    enabled = !reminderLoading,
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        reminderLoading = true
                        scope.launch {
                            // Feedback goes through the snackbar host rather than a loose
                            // string compared against a hard-coded success message.
                            runCatching { LibraryRepository.sendReminder(book.id) }
                                .onSuccess {
                                    snackbarHostState.showSnackbar(
                                        "Reminder sent to ${loan.counterpartyName}"
                                    )
                                }
                                .onFailure {
                                    snackbarHostState.showSnackbar(
                                        it.localizedMessage ?: "We could not send that reminder."
                                    )
                                }
                            reminderLoading = false
                        }
                    }
                )
                Spacer(Modifier.height(spacing.xs))
            }

            SecondaryButton(
                text = returnActionLabel,
                enabled = !waitingForConfirmation,
                leadingIcon = {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = colors.onSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    navController.navigate(Routes.ReturnConfirmation.createRoute(book.id))
                }
            )

            Spacer(Modifier.height(spacing.xs))
            PrimaryButton(
                text = "View the book",
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { navController.navigate(Routes.BookDetail.createRoute(book.id)) }
            )
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    val colors = MaktabaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {}
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
            Text(value, style = MaterialTheme.typography.labelMedium, color = colors.ink)
        }
    }
}

@Preview(showBackground = true, name = "Active loan")
@Composable
private fun ActiveLoanScreenPreview() {
    BookHavenTheme {
        ActiveLoanScreen(navController = rememberNavController(), bookId = "preview")
    }
}
