package com.maktaba.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.ConfirmationDialog
import com.maktaba.app.ui.components.GreenButton
import com.maktaba.app.ui.components.MaktabaCard
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.QrCodeImage
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.TextActionButton
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** The scheme registered in AndroidManifest, so the QR and the shared link both resolve. */
fun lendingLinkFor(code: String) = "maktaba://loan?code=$code"

@Composable
fun ShareLendingCodeScreen(navController: NavHostController, inviteId: String) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var invite by remember(inviteId) { mutableStateOf(LibraryRepository.pendingInviteById(inviteId)) }
    var loading by remember(inviteId) { mutableStateOf(invite == null) }
    var error by remember(inviteId) { mutableStateOf<String?>(null) }
    var cancelling by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    LaunchedEffect(inviteId) {
        if (invite == null) {
            runCatching { LibraryRepository.loadPendingInvite(inviteId) }
                .onSuccess { invite = it }
                .onFailure { error = it.localizedMessage ?: "This invitation is unavailable." }
            loading = false
        }
    }

    // Expiry has to be evaluated against a clock that advances. Reading Instant.now()
    // once at composition meant a code that lapsed while the screen was open stayed
    // copyable and shareable until the user navigated away.
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(1_000)
        }
    }
    val readyInvite = invite?.takeIf {
        it.status == "pending" && (it.expiresAt == null || it.expiresAt.isAfter(now))
    }
    val book = readyInvite?.let { LibraryRepository.bookById(it.copyId) }

    if (!loading && (error != null || readyInvite == null || book == null)) {
        UnavailableState(
            title = "This code has expired",
            message = error
                ?: "This invitation has expired or was cancelled. Generate a fresh code and " +
                "share it again.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } },
            primaryLabel = invite?.copyId?.let { "Generate a new code" },
            onPrimary = invite?.copyId?.let { copyId ->
                {
                    navController.navigate(Routes.LendBookConfig.createRoute(copyId)) {
                        popUpTo(Routes.HomeLibrary.route)
                    }
                }
            }
        )
        return
    }

    if (confirmCancel && readyInvite != null) {
        ConfirmationDialog(
            title = "Cancel this invite?",
            message = "The code stops working immediately and the borrower will not be able " +
                "to claim the book.",
            confirmLabel = "Cancel invite",
            loading = cancelling,
            onConfirm = {
                cancelling = true
                scope.launch {
                    runCatching { LibraryRepository.cancelLendingInvite(readyInvite.id) }
                        .onSuccess {
                            confirmCancel = false
                            navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) }
                        }
                        .onFailure {
                            error = it.localizedMessage ?: "We could not cancel this invitation."
                        }
                    cancelling = false
                }
            },
            onDismiss = { confirmCancel = false }
        )
    }

    MaktabaScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ScreenTopBar(title = "Share the code", onBack = { navController.popBackStack() })
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
        ) {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(spacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
                return@Column
            }

            val code = readyInvite!!.code.value
            val link = lendingLinkFor(code)

            Spacer(Modifier.height(spacing.xs))
            Text(
                "Ask the borrower to scan this code, or send it to them.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // The expiry was enforced but never shown, so it always arrived as a surprise.
            readyInvite.expiresAt?.let { expiry ->
                Spacer(Modifier.height(spacing.sm))
                ExpiryCountdown(expiresAt = expiry, now = now)
            }

            Spacer(Modifier.height(spacing.md))

            MaktabaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.lg)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "YOUR LENDING CODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        code,
                        style = MaterialTheme.typography.displayLarge,
                        color = colors.ink,
                        modifier = Modifier.semantics {
                            contentDescription = "Lending code ${code.toCharArray().joinToString(" ")}"
                        }
                    )
                    Spacer(Modifier.height(spacing.sm))
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            scope.launch { snackbarHostState.showSnackbar("Code copied") }
                        },
                        shape = MaktabaShapes.pill
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = colors.ink,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Copy code",
                            color = colors.ink,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(Modifier.height(spacing.md))
                    QrCodeImage(
                        value = link,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(MaktabaShapes.small)
                            .background(Color.White)
                            .padding(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(spacing.lg))

            GreenButton(
                text = "Share link",
                leadingIcon = {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = colors.onAccent)
                },
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Borrow \"${book!!.title}\" from me on Book Haven.\n" +
                                "Open $link, or enter code $code in the app."
                        )
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Share lending invitation")
                    )
                }
            )
            Spacer(Modifier.height(spacing.xs))
            TextActionButton(
                text = "Done",
                onClick = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
            )

            error?.let {
                Spacer(Modifier.height(spacing.xs))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(spacing.lg))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            Spacer(Modifier.height(spacing.xs))
            TextActionButton(
                text = "Cancel this invite",
                destructive = true,
                onClick = { confirmCancel = true }
            )
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun ExpiryCountdown(expiresAt: Instant, now: Instant) {
    val colors = MaktabaTheme.colors
    val seconds = Duration.between(now, expiresAt).seconds.coerceAtLeast(0)
    val label = when {
        seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
        seconds >= 60 -> "${seconds / 60} min ${seconds % 60} s"
        else -> "$seconds s"
    }
    val urgent = seconds < 300

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaktabaShapes.pill)
            .background(if (urgent) colors.warningContainer else colors.surfaceAlt)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = "This code is active for $label" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Schedule,
            contentDescription = null,
            tint = if (urgent) colors.warning else colors.inkMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "This code is active for $label",
            style = MaterialTheme.typography.labelMedium,
            color = if (urgent) colors.warning else colors.inkSoft
        )
    }
}

@Preview(showBackground = true, name = "Share lending code")
@Composable
private fun ShareLendingCodeScreenPreview() {
    BookHavenTheme {
        ShareLendingCodeScreen(navController = rememberNavController(), inviteId = "preview")
    }
}
