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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
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
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookHavenBottomNav
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.SecondaryButton
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.*

@Composable
fun ActiveLoanScreen(navController: NavHostController, bookId: String = Routes.DEFAULT_BOOK_ID) {
    val book = LibraryRepository.bookById(bookId) ?: LibraryRepository.books.first()
    val loan = LibraryRepository.activeLoanFor(book.id)
    val counterpartyLabel = if (loan?.isLender != false) "Borrower" else "Lender"
    val counterpartyName = loan?.counterpartyName ?: "Alex Johnson"
    val dueDate = loan?.dueDate ?: "May 28, 2025"

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
                    "Active Loan",
                    modifier = Modifier.weight(1f),
                    color = InkBrown,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceCard)
                    .padding(18.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Image(
                    painter = painterResource(book.coverRes),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    LabelRow(Icons.Filled.MenuBook, "Book")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        book.title,
                        fontFamily = SerifDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = InkBrown
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerTan))
                    Spacer(Modifier.height(6.dp))
                    Text(book.author, fontSize = 14.sp, color = MutedText)

                    Spacer(Modifier.height(12.dp))
                    LabelRow(Icons.Filled.Person, counterpartyLabel)
                    Spacer(Modifier.height(4.dp))
                    Text(counterpartyName, color = InkBrown, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                    Spacer(Modifier.height(12.dp))
                    LabelRow(Icons.Filled.CalendarToday, "Due Return Date")
                    Spacer(Modifier.height(4.dp))
                    Text(dueDate, color = InkBrown, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "Days Remaining",
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = InkBrown,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TimeBlock("05", "Days")
                TimeBlock("14", "Hours")
                TimeBlock("32", "Minutes")
                TimeBlock("47", "Seconds")
            }

            Spacer(Modifier.height(22.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                PrimaryButton(
                    text = "Send Reminder",
                    leadingIcon = { Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = {}
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Confirm Return",
                    leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = { navController.navigate(Routes.ReturnConfirmation.createRoute(book.id)) }
                )
            }

            Spacer(Modifier.height(18.dp))
            BookHavenBottomNav(
                selected = BottomNavTab.LIBRARY,
                onSelect = { navController.navigateToTab(it) }
            )
        }
    }
}

@Composable
private fun LabelRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SurfaceCardAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = MutedText, fontSize = 13.sp)
    }
}

@Composable
private fun TimeBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = WoodBrown)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = MutedText)
    }
}

@Preview(showBackground = true, name = "ActiveLoanScreenPreview")
@Composable
private fun ActiveLoanScreenPreview() {
    BookHavenTheme {
        ActiveLoanScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true, name = "ActiveLoanScreenPreview_Borrowed")
@Composable
private fun ActiveLoanScreenPreview_Borrowed() {
    BookHavenTheme {
        ActiveLoanScreen(navController = rememberNavController(), bookId = "mockingbird")
    }
}
