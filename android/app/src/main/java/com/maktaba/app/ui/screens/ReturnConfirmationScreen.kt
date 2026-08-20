package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.R
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.ConfirmationDialog
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.theme.*
import com.maktaba.app.util.LoanTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ReturnConfirmationScreen(navController: NavHostController, bookId: String) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }
    val book = LibraryRepository.bookById(bookId)
    val loan = LibraryRepository.activeLoanFor(bookId)
    if (book == null || loan == null) {
        UnavailableState(
            title = "Loan unavailable",
            message = "This return workflow is no longer available.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }
    val returnRequested = loan.status == LoanStatus.RETURN_REQUESTED
    val waitingForCounterparty = returnRequested && loan.returnRequestedById == FirebaseSession.currentUser?.uid
    val confirmingCounterpartyRequest = returnRequested && !waitingForCounterparty
    val counterpartyLabel = if (loan.isLender) "Borrower" else "Lender"
    val counterpartyName = loan.counterpartyName

    if (showConfirmation) {
        ConfirmationDialog(
            title = if (confirmingCounterpartyRequest) "Confirm returned book?" else "Request a return?",
            message = if (confirmingCounterpartyRequest) {
                "This closes the loan for both participants."
            } else {
                "The other participant will be notified and asked to confirm."
            },
            confirmLabel = if (confirmingCounterpartyRequest) "Confirm Return" else "Request Return",
            loading = loading,
            destructive = confirmingCounterpartyRequest,
            onConfirm = {
                loading = true
                error = null
                scope.launch {
                    runCatching { LibraryRepository.confirmReturn(book.id) }
                        .onSuccess { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
                        .onFailure { error = it.localizedMessage ?: "Could not update the return." }
                    loading = false
                    if (error == null) showConfirmation = false
                }
            },
            onDismiss = { showConfirmation = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = InkBrown)
                }
                Text(
                    "Return Confirmation",
                    modifier = Modifier.weight(1f),
                    color = InkBrown,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(6.dp))
            Image(
                painter = painterResource(R.drawable.illus_return_confirmation),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )

            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    confirmingCounterpartyRequest -> "Confirm the return"
                    waitingForCounterparty -> "Return requested"
                    else -> "Request a return"
                },
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = SuccessGreen,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    confirmingCounterpartyRequest -> "The other party marked this book as returned."
                    waitingForCounterparty -> "Waiting for the other party to confirm."
                    else -> "The other party will be asked to confirm."
                },
                fontSize = 15.sp,
                color = InkBrownSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                BookCoverImage(
                    book = book,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        book.title,
                        fontFamily = SerifDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = InkBrown
                    )
                    Text(book.author, fontSize = 14.sp, color = SuccessGreen)
                    Spacer(Modifier.height(10.dp))
                    DetailLine(
                        Icons.Filled.CalendarToday,
                        "Borrowed on",
                        LoanTimeFormatter.formatDate(loan.acceptedAt)
                    )
                    Spacer(Modifier.height(8.dp))
                    DetailLine(Icons.Filled.Person, counterpartyLabel, counterpartyName)
                    Spacer(Modifier.height(8.dp))
                    DetailLine(Icons.Filled.EventAvailable, "Status", if (confirmingCounterpartyRequest) "Awaiting your confirmation" else "Active loan")
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCardAlt)
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Two-party confirmation", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = InkBrown)
                    Text("Both lender and borrower confirm before the loan closes.", fontSize = 12.sp, color = InkBrownSoft)
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                PrimaryButton(
                    text = when {
                        confirmingCounterpartyRequest -> "Confirm Return"
                        waitingForCounterparty -> "Awaiting Confirmation"
                        else -> "Request Return"
                    },
                    leadingIcon = { Icon(Icons.Filled.Handshake, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    enabled = !waitingForCounterparty && !loading,
                    loading = loading,
                    onClick = { showConfirmation = true }
                )
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFB3261E), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap to confirm the return and complete\nthe handshake.",
                fontSize = 12.sp,
                color = MutedText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCardAlt)
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "A friendly reminder — please return the book in the same condition. Thank you!",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = InkBrownSoft
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MutedText, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = MutedText, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = InkBrown, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true, name = "ReturnConfirmationScreenPreview")
@Composable
private fun ReturnConfirmationScreenPreview() {
    BookHavenTheme {
        ReturnConfirmationScreen(navController = rememberNavController(), bookId = "preview")
    }
}
