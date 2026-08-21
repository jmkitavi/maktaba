package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.R
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.ConfirmationDialog
import com.maktaba.app.ui.components.MaktabaCard
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.TextActionButton
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ReturnConfirmationScreen(navController: NavHostController, bookId: String) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var loading by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }

    val book = LibraryRepository.bookById(bookId)
    val loan = LibraryRepository.activeLoanFor(bookId)
    // Do not claim the loan is missing while its snapshot is still in flight.
    if ((book == null || loan == null) &&
        (!LibraryRepository.hasLoadedLibrary || !LibraryRepository.hasLoadedLoans)
    ) {
        LoadingState("Loading this return")
        return
    }
    if (book == null || loan == null) {
        UnavailableState(
            title = "Loan unavailable",
            message = "This return is no longer available. The loan may already be closed.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }

    val returnRequested = loan.status == LoanStatus.RETURN_REQUESTED
    val waitingForCounterparty =
        returnRequested && loan.returnRequestedById == FirebaseSession.currentUser?.uid
    val confirmingCounterpartyRequest = returnRequested && !waitingForCounterparty
    val counterpartyLabel = if (loan.isLender) "borrower" else "lender"

    if (showConfirmation) {
        ConfirmationDialog(
            title = if (confirmingCounterpartyRequest) "Confirm the return?" else "Request the return?",
            message = if (confirmingCounterpartyRequest) {
                "This closes the loan for both of you and puts the book back on the shelf."
            } else {
                "Your $counterpartyLabel will be notified and asked to confirm."
            },
            confirmLabel = if (confirmingCounterpartyRequest) "Confirm return" else "Request return",
            loading = loading,
            destructive = confirmingCounterpartyRequest,
            onConfirm = {
                loading = true
                scope.launch {
                    runCatching { LibraryRepository.confirmReturn(book.id) }
                        .onSuccess {
                            showConfirmation = false
                            navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) }
                        }
                        .onFailure {
                            showConfirmation = false
                            snackbarHostState.showSnackbar(
                                it.localizedMessage ?: "We could not update this return."
                            )
                        }
                    loading = false
                }
            },
            onDismiss = { showConfirmation = false }
        )
    }

    MaktabaScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ScreenTopBar(title = "Return", onBack = { navController.popBackStack() })
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
        ) {
            Image(
                painter = painterResource(R.drawable.illus_return_confirmation),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(130.dp)
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                when {
                    confirmingCounterpartyRequest -> "Confirm the return"
                    waitingForCounterparty -> "Return requested"
                    else -> "Request the return"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() }
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                when {
                    confirmingCounterpartyRequest ->
                        "Your $counterpartyLabel says this book is back with its owner."
                    waitingForCounterparty ->
                        "Waiting for your $counterpartyLabel to confirm they have handed it over."
                    else ->
                        "Your $counterpartyLabel will be asked to confirm the hand-over."
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
                            .width(84.dp)
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
                        Text(
                            book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            "With ${loan.counterpartyName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inkSoft
                        )
                        Text(
                            "Due ${LoanTimeFormatter.formatDate(loan.dueAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inkSoft
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.lg))
            PrimaryButton(
                text = when {
                    confirmingCounterpartyRequest -> "Confirm the return"
                    waitingForCounterparty -> "Waiting for confirmation"
                    else -> "Request the return"
                },
                enabled = !waitingForCounterparty && !loading,
                loading = loading,
                leadingIcon = {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { showConfirmation = true }
            )
            Spacer(Modifier.height(spacing.xxs))
            TextActionButton(text = "Not now", onClick = { navController.popBackStack() })
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Preview(showBackground = true, name = "Return")
@Composable
private fun ReturnConfirmationScreenPreview() {
    BookHavenTheme {
        ReturnConfirmationScreen(navController = rememberNavController(), bookId = "preview")
    }
}
