package com.maktaba.app.ui.screens

import androidx.compose.runtime.Composable
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.UnavailableState
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
    ActiveLoanPresentation(book, loan, stage, onBack, onReminder, onReturn, onBook, onReader, now)
}
