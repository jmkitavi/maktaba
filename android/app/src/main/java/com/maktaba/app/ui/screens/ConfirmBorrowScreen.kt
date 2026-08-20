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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.OutlinedPillButton
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.theme.*
import com.maktaba.app.util.LoanTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ConfirmBorrowScreen(navController: NavHostController, inviteCode: String) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resolving by remember(inviteCode) { mutableStateOf(true) }
    var resolvedBookId by remember(inviteCode) {
        mutableStateOf(LibraryRepository.bookIdForCode(inviteCode))
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(inviteCode) {
        if (resolvedBookId == null) {
            runCatching { LibraryRepository.resolveLoanCode(inviteCode) }
                .onSuccess { resolvedBookId = it }
                .onFailure { error = it.localizedMessage ?: "This borrowing invitation is unavailable." }
        }
        resolving = false
    }
    if (resolving) {
        Box(Modifier.fillMaxSize().background(CreamBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = WoodBrown)
        }
        return
    }
    val bookId = resolvedBookId
    val book = bookId?.let(LibraryRepository::bookById)
    val invitation = bookId?.let(LibraryRepository::activeLoanFor)
    if (book == null || invitation == null) {
        UnavailableState(
            title = "Invitation unavailable",
            message = "This borrowing invitation could not be found.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }
    val lenderName = invitation.counterpartyName
    val dueDate = LoanTimeFormatter.formatDate(invitation.dueAt)

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = InkBrown)
                }
            }

            Text(
                "Confirm Borrow",
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = InkBrown,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(40.dp).height(1.dp).background(AccentGoldSoft))
                Icon(Icons.Filled.AutoStories, contentDescription = null, tint = AccentGold, modifier = Modifier.padding(horizontal = 10.dp).size(24.dp))
                Box(Modifier.width(40.dp).height(1.dp).background(AccentGoldSoft))
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Please confirm you've received\nthe book from your lender.",
                fontFamily = SansBody,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = InkBrownSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceCard)
                    .padding(18.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                BookCoverImage(
                    book = book,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Book", color = WoodBrown, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        book.title,
                        fontFamily = SerifDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = InkBrown
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(book.author, fontSize = 14.sp, color = MutedText)

                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerTan))
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SurfaceCardAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Lender", color = WoodBrown, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(lenderName, color = InkBrown, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceCardAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Due Return Date (agreed)", color = WoodBrown, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(dueDate, color = InkBrown, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Please return the book by this date.", color = MutedText, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (error != null) {
                    Text(error!!, color = Color(0xFFB3261E), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    text = "Confirm Receipt",
                    loading = loading,
                    enabled = !loading,
                    leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        if (loading) return@PrimaryButton
                        loading = true
                        error = null
                        scope.launch {
                            runCatching { LibraryRepository.confirmBorrow(book.id, lenderName, dueDate) }
                                .onSuccess { navController.navigate(Routes.ActiveLoan.createRoute(book.id)) }
                                .onFailure { error = it.localizedMessage ?: "Could not accept this loan." }
                            loading = false
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedPillButton(text = "Cancel", onClick = { navController.popBackStack() })
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentGoldSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "We'll send a friendly reminder before the due date so you never miss a return.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = InkBrownSoft
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true, name = "ConfirmBorrowScreenPreview")
@Composable
private fun ConfirmBorrowScreenPreview() {
    BookHavenTheme {
        ConfirmBorrowScreen(navController = rememberNavController(), inviteCode = "ABCD-2345")
    }
}
