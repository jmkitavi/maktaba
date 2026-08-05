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
import androidx.compose.runtime.Composable
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
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookHavenBottomNav
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.*

@Composable
fun ReturnConfirmationScreen(navController: NavHostController, bookId: String = Routes.DEFAULT_BOOK_ID) {
    val book = LibraryRepository.bookById(bookId) ?: LibraryRepository.books.first()
    val loan = LibraryRepository.activeLoanFor(book.id)
    val counterpartyLabel = if (loan?.isLender != false) "Borrower" else "Lender"
    val counterpartyName = loan?.counterpartyName ?: "Jamie Lee"

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
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
                "Return confirmed!",
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = SuccessGreen,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Thank you for sharing.",
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
                Image(
                    painter = painterResource(book.coverRes),
                    contentDescription = book.title,
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
                    DetailLine(Icons.Filled.CalendarToday, "Borrowed on", "Apr 28, 2024")
                    Spacer(Modifier.height(8.dp))
                    DetailLine(Icons.Filled.Person, counterpartyLabel, counterpartyName)
                    Spacer(Modifier.height(8.dp))
                    DetailLine(Icons.Filled.EventAvailable, "Returned on", "May 26, 2024")
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
                    Text("Return successful", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = InkBrown)
                    Text("The book has been returned. Happy reading!", fontSize = 12.sp, color = InkBrownSoft)
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                PrimaryButton(
                    text = "Confirm Return",
                    leadingIcon = { Icon(Icons.Filled.Handshake, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        LibraryRepository.confirmReturn(book.id)
                        val popped = navController.popBackStack(Routes.HomeLibrary.route, inclusive = false)
                        if (!popped) {
                            navController.navigate(Routes.HomeLibrary.route) {
                                popUpTo(0)
                            }
                        }
                    }
                )
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

            Spacer(Modifier.height(16.dp))
            BookHavenBottomNav(
                selected = BottomNavTab.LIBRARY,
                onSelect = { navController.navigateToTab(it) }
            )
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
        ReturnConfirmationScreen(navController = rememberNavController())
    }
}
