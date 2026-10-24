package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.MaktabaCard
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.components.StatusPill
import com.maktaba.app.ui.components.statusVisuals
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.util.LoanTimeFormatter
import java.time.Instant

@Composable
fun ActiveLoanScreen(navController: androidx.navigation.NavHostController, bookId: String) {
    val book = LibraryRepository.bookById(bookId)
    val loan = LibraryRepository.activeLoanFor(bookId)
    val back: () -> Unit = { navController.popBackStack() }
    val library: () -> Unit = { navController.navigate(Routes.HomeLibrary.route) }
    val error = LibraryRepository.libraryError ?: LibraryRepository.loansError
    if (error != null) {
        LoanLoadFailure(error, back, library)
        return
    }
    if (!LibraryRepository.hasLoadedLibrary || !LibraryRepository.hasLoadedLoans) {
        LoadingState("Loading this loan")
        return
    }
    if (book == null || loan == null || loan.status !in setOf(LoanStatus.ACTIVE, LoanStatus.RETURN_REQUESTED)) {
        UnavailableState("Loan unavailable", "This loan may already have been closed.", back, library)
        return
    }
    ActiveLoanContent(
        book = book,
        loan = loan,
        stage = returnStage(loan, FirebaseSession.currentUser?.uid),
        onBack = back,
        onReminder = { navController.navigate(Routes.ReturnReminder.createRoute(bookId, true)) },
        onReturn = { navController.navigate(Routes.ReturnConfirmation.createRoute(bookId)) },
        onBook = { navController.navigate(Routes.BookDetail.createRoute(bookId)) },
        onReader = loan.counterpartyUid.takeIf(String::isNotBlank)?.let { uid ->
            { navController.navigate(Routes.ReaderProfile.createRoute(uid)) }
        }
    )
}

@Composable
fun ActiveLoanContent(
    book: Book,
    loan: ActiveLoan,
    stage: ReturnStage,
    onBack: () -> Unit,
    onReminder: () -> Unit,
    onReturn: () -> Unit,
    onBook: () -> Unit,
    onReader: (() -> Unit)? = null,
    now: Instant = Instant.now()
) {
    val colors = MaktabaTheme.colors
    LoanPage(
        "Active Loan",
        onBack,
        footer = {
            if (loan.isLender && stage == ReturnStage.REQUEST) {
                PrimaryButton("Send a Reminder", onClick = onReminder)
            }
            val returnLabel = when (stage) {
                    ReturnStage.WAITING -> "Waiting for Confirmation"
                    ReturnStage.CONFIRM -> "Review the Return"
                    else -> "Start a Return"
            }
            if (loan.isLender && stage == ReturnStage.REQUEST) {
                LoanSecondaryButton(returnLabel, onClick = onReturn)
            } else {
                PrimaryButton(returnLabel, enabled = stage == ReturnStage.REQUEST || stage == ReturnStage.CONFIRM,
                    onClick = onReturn)
            }
        }
    ) {
        LoanBookSummary(book)
        Spacer(Modifier.height(13.dp))
        StatusPill(visuals = statusVisuals(colors, book.status), compact = true)
        Spacer(Modifier.height(13.dp))
        Row(
            modifier = Modifier.fillMaxWidth().then(
                if (onReader != null) Modifier.clickable(onClickLabel = "View Reader Profile", onClick = onReader)
                else Modifier
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceAlt), contentAlignment = Alignment.Center) {
                Text(loan.counterpartyName.take(1), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = colors.primary)
            }
            Column {
                Text(if (loan.isLender) "Borrower" else "Lender",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp, lineHeight = 14.4.sp, lineHeightStyle = LoanLineHeightStyle
                    ),
                    color = colors.inkMuted)
                Spacer(Modifier.height(2.dp))
                Text(loan.counterpartyName, style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp, lineHeight = 19.2.sp, lineHeightStyle = LoanLineHeightStyle
                ),
                    color = colors.ink)
            }
        }
        Spacer(Modifier.height(13.dp))
        MaktabaCard(
            modifier = Modifier.border(BorderStroke(1.dp, colors.divider), MaktabaShapes.medium),
            contentPadding = PaddingValues(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceAlt), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = colors.primary,
                        modifier = Modifier.size(16.dp))
                }
                Column {
                    Text("Agreed Return Date", style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp, lineHeight = 14.4.sp, lineHeightStyle = LoanLineHeightStyle
                    ),
                        color = colors.inkMuted)
                    Spacer(Modifier.height(6.dp))
                    Text(LoanTimeFormatter.formatDate(loan.dueAt),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.4.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            lineHeightStyle = LoanLineHeightStyle
                        ), color = colors.ink)
                }
            }
        }
        LoanTimeline(loan, now)
        LoanFeedback(when (stage) {
            ReturnStage.WAITING -> "Your part is confirmed. Waiting for ${loan.counterpartyName} to confirm the handover. The loan is still open."
            ReturnStage.CONFIRM -> "${loan.counterpartyName} says the book is back with its owner. Review the handover before closing the loan."
            ReturnStage.UNAVAILABLE -> "This return cannot be changed right now. Refresh the loan before continuing."
            else -> null
        })
        Spacer(Modifier.height(15.dp))
        Row(
            Modifier.defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, onClick = onBook)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("View the Book", style = MaterialTheme.typography.bodySmall, color = colors.primary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.primary,
                modifier = Modifier.size(13.dp))
        }
    }
}
