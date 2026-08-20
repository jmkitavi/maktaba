package com.maktaba.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.PendingLoanInvite
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.ConfirmationDialog
import com.maktaba.app.ui.components.GreenButton
import com.maktaba.app.ui.components.OutlinedPillButton
import com.maktaba.app.ui.components.QrCodeImage
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun ShareLendingCodeScreen(navController: NavHostController, inviteId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    val readyInvite = invite?.takeIf {
        it.status == "pending" && (it.expiresAt == null || it.expiresAt.isAfter(Instant.now()))
    }
    val book = readyInvite?.let { LibraryRepository.bookById(it.copyId) }

    if (!loading && (error != null || readyInvite == null || book == null)) {
        UnavailableState(
            title = "Invitation unavailable",
            message = error ?: "This invitation has expired, was cancelled, or is no longer available.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }

    if (confirmCancel && readyInvite != null) {
        ConfirmationDialog(
            title = "Cancel lending invite?",
            message = "The borrower will no longer be able to use this code.",
            confirmLabel = "Cancel Invite",
            loading = cancelling,
            onConfirm = {
                cancelling = true
                scope.launch {
                    runCatching { LibraryRepository.cancelLendingInvite(readyInvite.id) }
                        .onSuccess {
                            confirmCancel = false
                            navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) }
                        }
                        .onFailure { error = it.localizedMessage ?: "Could not cancel this invitation." }
                    cancelling = false
                }
            },
            onDismiss = { confirmCancel = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = navController::popBackStack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = InkBrown)
            }
            Text(
                "Share Lending Code",
                modifier = Modifier.weight(1f),
                color = InkBrown,
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(48.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WoodBrown)
            }
            return@Column
        }

        val code = readyInvite!!.code.value
        Text(
            "Ask the borrower to scan or enter this code",
            color = InkBrownSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(20.dp))
                .background(SurfaceCard).padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("YOUR LENDING CODE", color = WoodBrown, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(code, fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = InkBrown)
            Spacer(Modifier.height(18.dp))
            QrCodeImage(
                value = "maktaba://loan?code=$code",
                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.White).padding(10.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(horizontal = 24.dp)) {
            GreenButton(
                text = "Share Link",
                leadingIcon = { Icon(Icons.Filled.Link, null, tint = Color.White) },
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Borrow ${book!!.title} from me on Maktaba. Enter code $code."
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share lending invitation"))
                }
            )
            Spacer(Modifier.height(12.dp))
            OutlinedPillButton(text = "Cancel Invite", onClick = { confirmCancel = true })
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFB3261E), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            OutlinedPillButton(
                text = "Done",
                onClick = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
