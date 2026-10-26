package com.maktaba.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.data.Book
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.InviteUnavailableException
import com.maktaba.app.data.PendingLoanInvite
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.*
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

fun lendingLinkFor(code: String) = "maktaba://loan?code=$code"

data class ShareLendingCodeState(
    val book: Book,
    val invite: PendingLoanInvite,
    val now: Instant,
    val working: Boolean = false,
    val error: String? = null,
    val feedback: String? = null,
    val borrowerLabel: String? = null
)

@Composable
fun ShareLendingCodeScreen(
    navController: NavHostController,
    inviteId: String,
    loadInvite: suspend (String) -> PendingLoanInvite = LibraryRepository::loadPendingInvite
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val clock = remember { Clock.systemDefaultZone() }
    val borrowerLabel = remember(inviteId) {
        navController.previousBackStackEntry?.savedStateHandle?.get<String>(inviteBorrowerLabelKey(inviteId))
    }
    var invite by remember(inviteId) { mutableStateOf<PendingLoanInvite?>(null) }
    var bookSnapshot by remember(inviteId) { mutableStateOf<Book?>(null) }
    var loading by remember(inviteId) { mutableStateOf(true) }
    var loadError by remember(inviteId) { mutableStateOf<String?>(null) }
    var unavailable by remember(inviteId) { mutableStateOf<InviteUnavailableException?>(null) }
    var actionError by remember(inviteId) { mutableStateOf<String?>(null) }
    var feedback by remember(inviteId) { mutableStateOf<String?>(null) }
    var working by remember(inviteId) { mutableStateOf(false) }
    var cancelled by remember(inviteId) { mutableStateOf(false) }
    var confirmCancel by remember(inviteId) { mutableStateOf(false) }
    var retry by remember(inviteId) { mutableIntStateOf(0) }
    val now by produceState(clock.instant(), inviteId) {
        while (true) { value = clock.instant(); delay(1_000) }
    }

    suspend fun verifiedInvite(): PendingLoanInvite {
        val refreshed = loadInvite(inviteId)
        val issued = invite
        if (issued != null && !sameIssuedInvite(issued, refreshed)) {
            throw InviteUnavailableException("changed", "This invitation changed. Go back and generate a new code.")
        }
        if (!inviteIsPending(refreshed, clock.instant())) {
            throw InviteUnavailableException(refreshed.status.takeUnless { it == "pending" } ?: "expired",
                "This invitation has expired, been used, or been cancelled. Generate a new code.")
        }
        return refreshed
    }

    LaunchedEffect(inviteId, retry) {
        loading = invite == null
        loadError = null
        unavailable = null
        do {
            val result = handoffAttempt { verifiedInvite() }
            result.onSuccess { if (invite == null) invite = it }
                .onFailure {
                    if (it is InviteUnavailableException) unavailable = it
                    else loadError = it.localizedMessage ?: "Could not verify this invitation."
                }
            loading = false
            if (result.isFailure) break
            delay(15_000)
        } while (!cancelled)
    }

    // Return before touching the not-yet-loaded invitation (PR #1 regression).
    if (loading) {
        LoadingState("Loading this invitation")
        return
    }
    val issued = invite
    if (loadError != null) {
        UnavailableState("Could not verify invitation", loadError.orEmpty(),
            navController::popBackStack, { navController.navigate(Routes.HomeLibrary.route) },
            "Try again", { retry++ })
        return
    }
    if (cancelled || unavailable != null || issued == null || !inviteIsPending(issued, now)) {
        UnavailableState(
            when {
                cancelled || unavailable?.inviteStatus == "cancelled" -> "Invitation cancelled"
                unavailable?.inviteStatus in setOf("accepted", "used", "claimed") -> "Invitation already used"
                unavailable?.inviteStatus == "expired" || issued?.let { !inviteIsPending(it, now) } == true ->
                    "Invitation expired"
                else -> "Invitation unavailable"
            },
            if (cancelled) "This code no longer works. No loan was started."
            else unavailable?.message ?: "This code has expired or is no longer pending. Ask the borrower not to use it.",
            navController::popBackStack, { navController.navigate(Routes.HomeLibrary.route) },
            issued?.let { "Generate a new code" },
            issued?.let { { navController.navigate(Routes.LendBookConfig.createRoute(it.copyId)) } }
        )
        return
    }
    val book = bookSnapshot ?: LibraryRepository.canonicalBookForCopy(issued.copyId)
    if (book == null) {
        ShareInviteBookStatusContent(
            libraryLoaded = LibraryRepository.hasLoadedLibrary,
            error = LibraryRepository.libraryError,
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) },
            onRetry = LibraryRepository::retryLoads
        )
        return
    }
    LaunchedEffect(inviteId, book) { if (bookSnapshot == null) bookSnapshot = book }

    fun authorizedAction(action: () -> Unit) {
        if (working || !inviteIsPending(issued, clock.instant())) return
        working = true
        actionError = null
        scope.launch(Dispatchers.Main.immediate) {
            handoffAttempt { verifiedInvite() }
                .onSuccess { action() }
                .onFailure {
                    if (it is InviteUnavailableException) unavailable = it
                    else loadError = it.localizedMessage ?: "Could not verify the invitation. Try again."
                }
            working = false
        }
    }
    if (confirmCancel) {
        ConfirmationDialog(
            title = "Cancel this invite?",
            message = "The code stops working immediately. No loan starts from a cancelled invitation.",
            confirmLabel = "Cancel invite", loading = working,
            onDismiss = { if (!working) confirmCancel = false },
            onConfirm = {
                if (!working) {
                    working = true
                    scope.launch(Dispatchers.Main.immediate) {
                        handoffAttempt { LibraryRepository.cancelLendingInvite(issued.id) }
                            .onSuccess { cancelled = true; confirmCancel = false }
                            .onFailure { actionError = it.localizedMessage ?: "Could not cancel the invitation. Try again."; confirmCancel = false }
                        working = false
                    }
                }
            }
        )
    }
    ShareLendingCodeContent(
        ShareLendingCodeState(book, issued, now, working, actionError, feedback, borrowerLabel),
        onBack = { navController.popBackStack() },
        onCopy = { authorizedAction { clipboard.setText(AnnotatedString(issued.code.value)); feedback = "Code copied" } },
        onShare = {
            authorizedAction {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Borrow \"${book.title}\" on Book Haven.\n${lendingLinkFor(issued.code.value)}\nCode: ${issued.code.value}")
                }
                try {
                    context.startActivity(Intent.createChooser(intent, "Share lending invitation"))
                } catch (_: ActivityNotFoundException) {
                    actionError = "No sharing app is available. You can copy the code instead."
                }
            }
        },
        onCancel = { if (!working) confirmCancel = true },
        onDone = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(Routes.HomeLibrary.route) { inclusive = true } } }
    )
}

@Composable
fun ShareInviteBookStatusContent(
    libraryLoaded: Boolean,
    error: String?,
    onBack: () -> Unit,
    onLibrary: () -> Unit,
    onRetry: () -> Unit
) {
    when {
        error != null -> UnavailableState("Could not load this book", error,
            onBack, onLibrary, "Try again", onRetry)
        !libraryLoaded -> LoadingState("Loading this book")
        else -> UnavailableState("Book unavailable", "This invitation's book is no longer available.",
            onBack, onLibrary)
    }
}

@Composable
fun ShareLendingCodeContent(
    state: ShareLendingCodeState,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val pending = inviteIsPending(state.invite, state.now) && !state.working
    val end = minOf(state.invite.expiresAt, state.invite.dueAt ?: state.invite.expiresAt)
    val seconds = Duration.between(state.now, end).seconds.coerceAtLeast(0)
    val remaining = if (seconds >= 3600) "${seconds / 3600} h ${(seconds % 3600) / 60} min"
        else "${seconds / 60} min ${seconds % 60} s"
    HandoffPage("Share the Code", onBack, actions = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Share Link", enabled = pending, loading = state.working,
                modifier = Modifier.weight(1f), leadingIcon = {
                    Icon(Icons.Outlined.Link, null, modifier = Modifier.size(16.dp))
                }, onClick = onShare)
            OutlinedButton(onClick = onDone, shape = MaktabaShapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Done") }
        }
    }) {
        HandoffBookSummary(state.book, compact = true)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(12.dp), tint = colors.inkMuted)
            Spacer(Modifier.width(5.dp))
            Text("Code active for  $remaining", style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp, lineHeight = 18.sp), color = colors.inkMuted)
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth()) {
            Surface(shape = MaktabaShapes.medium, color = colors.surface,
                border = BorderStroke(1.dp, colors.divider)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("YOUR LENDING CODE", style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp), color = colors.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(state.invite.code.value, style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 3.sp),
                        color = colors.ink)
                    TextButton(onClick = onCopy, enabled = pending) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Copy Code", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
                    }
                    Spacer(Modifier.height(6.dp))
                    QrCodeImage(lendingLinkFor(state.invite.code.value),
                        modifier = Modifier.size(144.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
                    Spacer(Modifier.height(10.dp))
                    Text("Scan this code in Book Haven.", style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp, lineHeight = 15.sp), color = colors.inkMuted)
                }
            }
            InviteShelfArt()
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(11.dp), tint = colors.inkMuted)
            Spacer(Modifier.width(5.dp))
            Text("Waiting for ${state.borrowerLabel?.takeIf { it.isNotBlank() } ?: "the borrower"} to confirm.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 18.sp),
                color = colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
        }
        TextButton(onClick = onCancel, enabled = pending,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.danger)) {
            Text("Cancel This Invite", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
        }
        state.error?.let { HandoffNotice(it, error = true) }
        state.feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted) }
    }
}
