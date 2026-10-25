package com.maktaba.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import com.google.firebase.functions.FirebaseFunctionsException
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.R
import com.maktaba.app.data.Book
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency
import java.time.Instant
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReturnReminderScreen(navController: NavHostController, bookId: String, review: Boolean = false) {
    val book = LibraryRepository.bookById(bookId)
    val loan = LibraryRepository.activeLoanFor(bookId)
    val scope = rememberCoroutineScope()
    var sending by remember(bookId) { mutableStateOf(false) }
    var sentLoanId by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var error by remember(bookId) { mutableStateOf<String?>(null) }
    var cooldownMillis by rememberSaveable(bookId) { mutableStateOf<Long?>(null) }
    var cooldownLoanId by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(bookId) {
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    val back: () -> Unit = { navController.popBackStack() }
    val library: () -> Unit = { navController.navigate(Routes.HomeLibrary.route) }
    val loadError = LibraryRepository.libraryError ?: LibraryRepository.loansError
    if (loadError != null) {
        LoanLoadFailure(loadError, back, library)
        return
    }
    if (!LibraryRepository.hasLoadedLibrary || !LibraryRepository.hasLoadedLoans) {
        LoadingState("Loading this reminder")
        return
    }
    if (book == null || loan == null || loan.status !in setOf(LoanStatus.ACTIVE, LoanStatus.RETURN_REQUESTED) ||
        (review && (!loan.isLender || loan.status != LoanStatus.ACTIVE))
    ) {
        UnavailableState(
            "Reminder unavailable",
            if (review && loan?.isLender == false) "Only the lender can send this reminder."
            else "This loan reminder is no longer available.",
            back, library
        )
        return
    }
    val cooldown = listOfNotNull(
        cooldownMillis?.takeIf { cooldownLoanId == loan.id }?.let(Instant::ofEpochMilli),
        loan.nextReminderAllowedAt
    ).maxOrNull()
    val state = ReminderPresentation(sending, sentLoanId == loan.id, error, cooldown)
    ReturnReminderContent(
        book, loan, review, state, now, back,
        onOpenLoan = { openJourneyLoan(navController, bookId) },
        onSend = send@{
            val current = LibraryRepository.activeLoanFor(bookId)
            if (FirebaseSession.currentUser == null) {
                error = "Sign in before sending a reminder."
                return@send
            }
            if (current == null || !current.isLender || current.status != LoanStatus.ACTIVE) {
                error = "This reminder is no longer available. Review the current loan."
                return@send
            }
            if (!state.canSend(current, review, Instant.now())) return@send
            // State is set before launch, so a second tap cannot dispatch another callable.
            if (sending) return@send
            sending = true
            error = null
            scope.launch {
                try {
                    cooldownMillis = LibraryRepository.sendReminder(bookId)?.toEpochMilli()
                    cooldownLoanId = current.id
                    sentLoanId = current.id
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val serverCooldown = reminderCooldownFromMessage(failure.message)
                    cooldownMillis = serverCooldown?.toEpochMilli()
                    cooldownLoanId = current.id
                    error = if (failure is FirebaseFunctionsException &&
                        failure.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                        failure.localizedMessage ?: "A reminder was sent recently. Please try again later."
                    } else {
                        failure.localizedMessage ?: "We could not send the reminder. Try again."
                    }
                } finally {
                    sending = false
                }
            }
        }
    )
}

@Composable
fun ReturnReminderContent(
    book: Book,
    loan: ActiveLoan,
    review: Boolean,
    state: ReminderPresentation,
    now: Instant,
    onBack: () -> Unit,
    onOpenLoan: () -> Unit,
    onSend: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val sendingReview = review && loan.isLender && loan.status == LoanStatus.ACTIVE
    val overdue = LoanTimeFormatter.urgency(loan.dueAt, Clock.fixed(now, ZoneId.systemDefault())) == LoanUrgency.OVERDUE
    LoanPage(
        if (sendingReview) "Send Reminder" else "Return Reminder",
        onBack,
        footer = {
            if (sendingReview && !state.sent) {
                PrimaryButton(
                    text = if (state.error != null) "Try Sending Again" else "Send Reminder",
                    enabled = state.canSend(loan, review, now),
                    loading = state.sending,
                    onClick = onSend
                )
                LoanSecondaryButton("Not Now", enabled = !state.sending, onClick = onBack)
            } else {
                PrimaryButton("Open This Loan", onClick = onOpenLoan)
                LoanSecondaryButton("Not Now", onClick = onBack)
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))
            if (state.sent) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.success,
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(colors.successContainer).padding(12.dp))
                Spacer(Modifier.height(23.dp))
            } else {
                Image(painterResource(R.drawable.illus_book_sprig), null, contentScale = ContentScale.Fit,
                    modifier = Modifier.width(90.dp).height(65.dp))
                Spacer(Modifier.height(15.dp))
            }
            LoanIntro(
                title = when {
                    state.sent -> "A Little Nudge, Sent"
                    sendingReview -> "A Little Nudge for ${loan.counterpartyName}"
                    overdue -> "This One Is Overdue"
                    else -> "A Gentle Reminder"
                },
                message = when {
                    state.sent -> "Your reminder was sent to ${loan.counterpartyName}."
                    sendingReview -> "A short reminder, with the book and agreed date. Nothing more."
                    loan.isLender -> "${loan.counterpartyName} ${if (overdue) "was due to return" else "is looking after"} your copy. Good stories are meant to keep moving."
                    overdue -> "A shared story from ${loan.counterpartyName}. The agreed return date has passed."
                    else -> "A shared story from ${loan.counterpartyName}. Keep the agreed date in mind."
                }
            )
        }
        Spacer(Modifier.height(22.dp))
        LoanBookSummary(book, compact = true)
        if (sendingReview) {
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier.fillMaxWidth().clip(MaktabaShapes.medium).background(colors.backgroundElevated)
                    .border(1.dp, colors.divider, MaktabaShapes.medium).padding(18.dp)
            ) {
                Text("TO ${loan.counterpartyName.uppercase()}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp, lineHeight = 14.4.sp, letterSpacing = 1.sp,
                        lineHeightStyle = LoanLineHeightStyle
                    ),
                    color = colors.primary)
                Spacer(Modifier.height(10.dp))
                Text(
                    buildAnnotatedString {
                        append("A gentle reminder about ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = colors.ink)) { append(book.title) }
                        append(". We agreed on ${LoanTimeFormatter.formatDate(loan.dueAt)}. Thanks for keeping the story moving.")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 22.8.sp, lineHeightStyle = LoanLineHeightStyle
                    ), color = colors.inkMuted
                )
            }
        } else {
            LoanTimeline(loan, now)
        }
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            LoanFeedback(state.error, error = true)
        }
        if (sendingReview && state.nextAllowedAt?.isAfter(now) == true) {
            val time = state.nextAllowedAt.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
            Spacer(Modifier.height(12.dp))
            LoanFeedback("Another reminder can be sent after $time. Please give the other reader time to respond.")
        }
        Spacer(Modifier.height(17.dp))
        LoanNote(
            if (sendingReview) "Sending a reminder does not change the agreed return date."
            else "Need more time? Contact the other reader and agree a new date between you. Book Haven does not offer messaging or reschedule the loan.",
            Icons.Outlined.CalendarToday
        )
    }
}
