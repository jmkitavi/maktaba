package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import com.maktaba.app.R
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.ConfirmationDialog
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ReturnConfirmationScreen(navController: NavHostController, bookId: String) {
    val currentBook = LibraryRepository.loanBookById(bookId)
    val currentLoan = LibraryRepository.loanFor(bookId)
    // Retain the title and role while the confirmed borrower copy disappears.
    var retainedBook by remember(bookId) { mutableStateOf<Book?>(null) }
    var retainedLoan by remember(bookId) { mutableStateOf<ActiveLoan?>(null) }
    if (currentBook != null) retainedBook = currentBook
    if (currentLoan != null) retainedLoan = currentLoan
    val book = currentBook ?: retainedBook
    val loan = currentLoan ?: retainedLoan
    var loading by remember(bookId) { mutableStateOf(false) }
    var showConfirmation by remember(bookId) { mutableStateOf(false) }
    var completedLoanId by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var requestedLoanId by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var error by remember(bookId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val back: () -> Unit = { navController.popBackStack() }
    val library: () -> Unit = { navController.navigate(Routes.HomeLibrary.route) }
    val loadError = LibraryRepository.libraryError ?: LibraryRepository.loansError
    if (loadError != null && (completedLoanId == null || completedLoanId != loan?.id)) {
        LoanLoadFailure(loadError, back, library)
        return
    }
    if ((!LibraryRepository.hasLoadedLibrary || !LibraryRepository.hasLoadedLoans) && book == null) {
        LoadingState("Loading this return")
        return
    }
    if (book == null || loan == null) {
        UnavailableState("Loan unavailable", "This return is no longer available.", back, library)
        return
    }
    val actualStage = returnStage(loan, FirebaseSession.currentUser?.uid)
    val stage = when {
        completedLoanId == loan.id || actualStage == ReturnStage.COMPLETE -> ReturnStage.COMPLETE
        requestedLoanId == loan.id && actualStage == ReturnStage.REQUEST -> ReturnStage.WAITING
        else -> actualStage
    }
    if (showConfirmation) {
        ConfirmationDialog(
            title = if (stage == ReturnStage.CONFIRM) "Confirm the Return?" else "Request Return Confirmation?",
            message = if (stage == ReturnStage.CONFIRM) {
                "Only confirm after the physical handover. This closes the loan for both readers."
            } else {
                "The other reader must confirm too. A request alone does not close the loan."
            },
            confirmLabel = if (stage == ReturnStage.CONFIRM) "Confirm Return" else "Request Confirmation",
            loading = loading,
            onConfirm = confirm@{
                if (loading) return@confirm
                val live = LibraryRepository.loanFor(bookId)
                if (live == null) {
                    showConfirmation = false
                    error = "This return is no longer available. Review the current loan."
                    return@confirm
                }
                val liveStage = returnStage(live, FirebaseSession.currentUser?.uid)
                if (liveStage != stage || liveStage !in setOf(ReturnStage.REQUEST, ReturnStage.CONFIRM)) {
                    showConfirmation = false
                    error = "This loan has changed. Review its current return status."
                    return@confirm
                }
                loading = true
                error = null
                scope.launch {
                    try {
                        LibraryRepository.confirmReturn(bookId)
                        if (liveStage == ReturnStage.CONFIRM) completedLoanId = live.id else requestedLoanId = live.id
                        showConfirmation = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        showConfirmation = false
                        error = failure.localizedMessage ?: "We could not update this return. Try again."
                    } finally {
                        loading = false
                }
                    }
            },
            onDismiss = { if (!loading) showConfirmation = false }
        )
    }
    ReturnConfirmationContent(
        book, loan, stage, loading, error, back,
        onSubmit = { if (!loading && stage in setOf(ReturnStage.REQUEST, ReturnStage.CONFIRM)) showConfirmation = true },
        onOpenLoan = { openJourneyLoan(navController, bookId) },
        onLoans = {
            if (!navController.popBackStack(Routes.Loans.route, false)) {
                navController.navigate(Routes.Loans.route) { launchSingleTop = true }
            }
        }
    )
}

@Composable
fun ReturnConfirmationContent(
    book: Book,
    loan: ActiveLoan,
    stage: ReturnStage,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onOpenLoan: () -> Unit,
    onLoans: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val complete = stage == ReturnStage.COMPLETE
    LoanPage(
        "Return",
        onBack,
        footer = {
            if (complete) {
                PrimaryButton("Back to Loans", onClick = onLoans)
            } else {
                PrimaryButton(
                    text = when (stage) {
                        ReturnStage.WAITING -> "Waiting for Confirmation"
                        ReturnStage.CONFIRM -> if (error != null) "Try Confirmation Again" else "Confirm the Return"
                        else -> if (error != null) "Try Request Again" else "Request Return Confirmation"
                    },
                    enabled = !loading && stage in setOf(ReturnStage.REQUEST, ReturnStage.CONFIRM),
                    loading = loading,
                    onClick = onSubmit
                )
                LoanSecondaryButton(
                    if (stage == ReturnStage.WAITING) "Back to This Loan" else "Not Yet",
                    enabled = !loading,
                    onClick = if (stage == ReturnStage.WAITING) onOpenLoan else onBack
                )
            }
        }
    ) {
        if (complete) {
            Spacer(Modifier.height(22.dp))
            Icon(Icons.Filled.Check, contentDescription = "Return complete", tint = colors.success,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(52.dp)
                    .clip(CircleShape).background(colors.successContainer).padding(13.dp))
            Spacer(Modifier.height(22.dp))
        } else {
            Spacer(Modifier.height(1.dp))
            Image(
                painterResource(R.drawable.illus_return_confirmation),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(103.dp)
            )
            Spacer(Modifier.height(17.dp))
        }
        LoanIntro(
            title = when (stage) {
                ReturnStage.COMPLETE -> "Back Where It Belongs"
                ReturnStage.WAITING -> "One Last Confirmation"
                ReturnStage.CONFIRM -> "Confirm the Return"
                else -> "Back With Its Owner?"
            },
            message = when (stage) {
                ReturnStage.COMPLETE -> "Both readers confirmed."
                ReturnStage.WAITING -> "Your part is confirmed. ${loan.counterpartyName} still needs to confirm the handover. This loan is not closed yet."
                ReturnStage.CONFIRM -> "${loan.counterpartyName} says this book is back with its owner. Only confirm if ${if (loan.isLender) "you have received this copy" else "you have handed this copy back"}."
                ReturnStage.REQUEST -> "Only continue once ${if (loan.isLender) "${loan.counterpartyName} has handed this copy back to you" else "you have handed this copy back to ${loan.counterpartyName}"}. The other reader will be asked to confirm."
                ReturnStage.UNAVAILABLE -> "This loan cannot be changed right now. Please refresh and review its status."
            },
            compactMessage = true,
            success = complete
        )
        Spacer(Modifier.height(if (complete) 26.dp else 19.dp))
        LoanBookSummary(book, compact = !complete)
        Spacer(Modifier.height(23.dp))
        ReturnConfirmations(loan.counterpartyName, stage)
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            LoanFeedback(error, error = true)
        }
        Spacer(Modifier.height(17.dp))
        LoanNote(
            when {
                complete && loan.isLender -> "Both readers confirmed the handover. This copy is back on your shelf, and the loan is closed for both readers."
                complete -> "Both readers confirmed the handover. This copy has left your borrowed books and returned to ${loan.counterpartyName}'s shelf."
                stage == ReturnStage.CONFIRM -> "Your confirmation closes this loan for both readers."
                else -> "A request alone does not close the loan. Both readers must confirm."
            },
            if (complete) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.SwapHoriz
        )
    }
}

@Composable
private fun ReturnConfirmations(counterparty: String, stage: ReturnStage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            "You" to (stage == ReturnStage.WAITING || stage == ReturnStage.COMPLETE),
            counterparty to (stage == ReturnStage.CONFIRM || stage == ReturnStage.COMPLETE)
        ).forEach { (name, confirmed) ->
            Column(Modifier.weight(1f)) {
                HorizontalDivider(color = MaktabaTheme.colors.divider)
                Row(
                    Modifier.padding(vertical = 15.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(
                            if (confirmed) MaktabaTheme.colors.successContainer else MaktabaTheme.colors.surfaceAlt
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                    Icon(
                        if (confirmed) Icons.Filled.Check else Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = if (confirmed) MaktabaTheme.colors.success else MaktabaTheme.colors.inkMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    }
                    Column {
                        Text(name, style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp, lineHeight = 17.6.sp, fontWeight = FontWeight.Medium,
                            lineHeightStyle = LoanLineHeightStyle
                        ), color = MaktabaTheme.colors.ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (confirmed) "Confirmed" else if (stage == ReturnStage.REQUEST) "Not confirmed yet" else "Awaiting confirmation",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 9.sp, lineHeight = 15.3.sp, lineHeightStyle = LoanLineHeightStyle
                            ),
                            color = if (confirmed) MaktabaTheme.colors.success else MaktabaTheme.colors.inkMuted
                        )
                    }
                }
                HorizontalDivider(color = MaktabaTheme.colors.divider)
            }
        }
    }
}
