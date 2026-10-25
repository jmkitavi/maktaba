package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.data.*
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.*
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

data class ConfirmBorrowState(
    val book: Book,
    val invitation: ActiveLoan,
    val submitting: Boolean = false,
    val accepted: Boolean = false,
    val error: String? = null,
    val now: Instant = Instant.now(),
    val unavailable: Boolean = false
)

internal fun canConfirmBorrow(state: ConfirmBorrowState): Boolean =
    !state.submitting && !state.accepted && !state.unavailable && state.invitation.role == LoanRole.BORROWER &&
        state.invitation.status == LoanStatus.PENDING && state.invitation.dueAt?.isAfter(state.now) == true

private val BorrowReviewSaver = listSaver<ConfirmBorrowState?, String>(
    save = { state ->
        state?.let { listOf(it.book.id, it.book.title, it.book.author, it.book.coverRes.toString(),
            it.book.catalogId, it.book.coverUrl, it.book.format.name, it.invitation.id,
            it.invitation.counterpartyName, it.invitation.dueAt?.toString().orEmpty(), it.accepted.toString()) }
            ?: emptyList()
    },
    restore = { saved ->
        if (saved.isEmpty()) null else ConfirmBorrowState(
            Book(saved[0], saved[1], saved[2], saved[3].toInt(), BookStatus.BORROWED,
                catalogId = saved[4], coverUrl = saved[5], format = BookFormat.valueOf(saved[6])),
            ActiveLoan(saved[7], saved[0], saved[8], LoanRole.BORROWER, LoanStatus.PENDING,
                saved[9].takeIf { it.isNotBlank() }?.let(Instant::parse), null, null, null),
            accepted = saved[10].toBoolean()
        )
    }
)

@Composable
fun ConfirmBorrowScreen(navController: NavHostController, inviteCode: String) {
    val scope = rememberCoroutineScope()
    var snapshot by rememberSaveable(inviteCode, stateSaver = BorrowReviewSaver) {
        mutableStateOf<ConfirmBorrowState?>(null)
    }
    var resolving by remember(inviteCode) { mutableStateOf(true) }
    var resolveError by remember(inviteCode) { mutableStateOf<String?>(null) }
    var terminalError by remember(inviteCode) { mutableStateOf(false) }
    var retry by remember(inviteCode) { mutableIntStateOf(0) }
    val now by produceState(Instant.now(), inviteCode) {
        while (true) { value = Instant.now(); delay(1_000) }
    }
    LaunchedEffect(inviteCode, retry) {
        if (snapshot?.accepted == true) {
            resolving = false
            return@LaunchedEffect
        }
        resolving = true
        resolveError = null
        terminalError = false
        handoffAttempt {
            val code = requireNotNull(parseLendingCode(inviteCode)) { "This lending code is invalid." }
            val id = LibraryRepository.resolveLoanCode(code)
            val book = requireNotNull(LibraryRepository.bookById(id)) { "The invitation's book is unavailable." }
            val invitation = requireNotNull(LibraryRepository.activeLoanFor(id)) { "The invitation is unavailable." }
            require(invitation.role == LoanRole.BORROWER && invitation.status == LoanStatus.PENDING) {
                "This invitation is no longer pending."
            }
            require(invitation.dueAt?.isAfter(Instant.now()) == true) { "This invitation's return date has passed." }
            ConfirmBorrowState(book, invitation)
        }.onSuccess { snapshot = it }
            .onFailure {
                resolveError = it.localizedMessage ?: "Could not load this invitation."
                terminalError = isTerminalHandoffFailure(it) || it is IllegalArgumentException
            }
        resolving = false
    }
    if (resolving) { LoadingState("Loading this invitation"); return }
    val loaded = snapshot
    if (resolveError != null || loaded == null) {
        UnavailableState(if (terminalError) "Invitation unavailable" else "Could not load invitation",
            resolveError ?: "Ask the lender for a new code.",
            navController::popBackStack, { navController.navigate(Routes.HomeLibrary.route) },
            if (terminalError) null else "Try again", if (terminalError) null else { { retry++ } })
        return
    }
    val state = loaded.copy(now = now)
    ConfirmBorrowContent(
        state,
        onBack = { navController.popBackStack() },
        onConfirm = {
            if (snapshot?.copy(now = Instant.now())?.let(::canConfirmBorrow) == true) {
                snapshot = state.copy(submitting = true, error = null)
                scope.launch(Dispatchers.Main.immediate) {
                    handoffAttempt {
                        val code = requireNotNull(parseLendingCode(inviteCode)) { "This lending code is invalid." }
                        val refreshedId = LibraryRepository.resolveLoanCode(code)
                        val refreshed = LibraryRepository.activeLoanFor(refreshedId)
                        if (refreshedId != state.book.id || refreshed == null || refreshed.id != state.invitation.id ||
                            refreshed.dueAt != state.invitation.dueAt ||
                            refreshed.role != LoanRole.BORROWER || refreshed.status != LoanStatus.PENDING) {
                            throw InviteUnavailableException("changed",
                                "This invitation changed. Ask the lender for a new code and review it again.")
                        }
                        LibraryRepository.confirmBorrow(
                            state.book.id, state.invitation.counterpartyName,
                            LoanTimeFormatter.formatDate(state.invitation.dueAt)
                        )
                    }.onSuccess { snapshot = state.copy(accepted = true, submitting = false) }
                        .onFailure { snapshot = state.copy(submitting = false,
                            error = it.localizedMessage ?: "Could not accept this loan. Try again.",
                            unavailable = isTerminalHandoffFailure(it)) }
                }
            }
        },
        onLoans = { navController.navigate(Routes.Loans.route) { popUpTo(Routes.HomeLibrary.route) } },
        onOpenLoan = { navController.navigate(Routes.ActiveLoan.createRoute(state.book.id)) }
    )
}

@Composable
fun ConfirmBorrowContent(
    state: ConfirmBorrowState,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onLoans: () -> Unit,
    onOpenLoan: () -> Unit
) {
    val colors = MaktabaTheme.colors
    HandoffPage("Confirm Borrow", onBack, illustrated = true, actions = {
        if (state.accepted) {
            PrimaryButton("Back to Loans", onClick = onLoans)
        } else {
            PrimaryButton("I Have the Book", enabled = canConfirmBorrow(state),
                loading = state.submitting, leadingIcon = {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, modifier = Modifier.size(16.dp))
                }, onClick = onConfirm)
            if (state.unavailable) TextActionButton("Back to Loans", onClick = onLoans)
            else TextActionButton("Not Yet", enabled = !state.submitting, onClick = onBack)
        }
    }) {
        if (state.accepted) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.align(Alignment.CenterHorizontally).size(52.dp)
                .clip(CircleShape).background(colors.successContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = colors.accent, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.height(20.dp))
            HandoffHeading("A shared story begins.", centered = true)
            Spacer(Modifier.height(14.dp))
            Text("Borrow confirmed", style = MaterialTheme.typography.bodySmall, color = colors.accent,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(28.dp))
        } else {
            HandoffHeading("Have the book in hand?")
            Spacer(Modifier.height(14.dp))
            Text("Only confirm once ${state.invitation.counterpartyName} has physically handed it to you. This starts the loan in the app.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 22.sp), color = colors.inkMuted)
            Spacer(Modifier.height(24.dp))
        }
        HandoffBookSummary(state.book)
        Spacer(Modifier.height(18.dp))
        if (!state.accepted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center) {
                    Text(state.invitation.counterpartyName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge, color = colors.primary)
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("Lender", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = colors.inkMuted)
                    Text(state.invitation.counterpartyName, style = MaterialTheme.typography.bodySmall,
                        color = colors.ink)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        HandoffDateCard(state.invitation.dueAt)
        Spacer(Modifier.height(24.dp))
        HandoffNotice(if (state.accepted)
            "The handover is confirmed. You can now find this book in your borrowed loans."
            else "The app reminds both readers before the agreed date.",
            icon = if (state.accepted) null else Icons.Outlined.Notifications)
        if (!state.accepted && state.invitation.dueAt?.isAfter(state.now) != true) {
            Spacer(Modifier.height(12.dp))
            HandoffNotice("This invitation's return date has passed. Ask the lender for a new code.", error = true)
        }
        state.error?.let { Spacer(Modifier.height(12.dp)); HandoffNotice(it, error = true) }
    }
}
