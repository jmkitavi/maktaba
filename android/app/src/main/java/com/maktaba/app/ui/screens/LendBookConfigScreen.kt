package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.maktaba.app.R
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookHavenBottomNav
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.*

@Composable
fun LendBookConfigScreen(navController: NavHostController, bookId: String = Routes.DEFAULT_BOOK_ID) {
    var borrowerName by remember { mutableStateOf("") }
    val book = LibraryRepository.bookById(bookId) ?: LibraryRepository.books.first()
    val dueDate = "May 28, 2025"

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    "Lend Book",
                    modifier = Modifier.weight(1f),
                    color = InkBrown,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(10.dp))

                // Book summary card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Image(
                        painter = painterResource(book.coverRes),
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(0.68f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            book.title,
                            fontFamily = SerifDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = InkBrown
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(book.author, fontSize = 15.sp, color = MutedText)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCardAlt)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(book.genre, fontSize = 13.sp, color = InkBrownSoft)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Return by", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = InkBrown)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(dueDate, fontSize = 16.sp, color = InkBrown, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MutedText)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Select the date by which the book should be returned.",
                    fontSize = 12.sp,
                    color = MutedText
                )

                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Borrower's name", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = InkBrown)
                    Spacer(Modifier.width(6.dp))
                    Text("(optional)", fontSize = 14.sp, color = MutedText)
                }
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = borrowerName,
                    onValueChange = { borrowerName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    placeholder = { Text("e.g., Alex Johnson", color = MutedText) },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = MutedText) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        focusedIndicatorColor = DividerTan,
                        unfocusedIndicatorColor = DividerTan
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add a name to personalize the lend (optional).",
                    fontSize = 12.sp,
                    color = MutedText
                )

                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = "Generate Code",
                    leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        LibraryRepository.startLending(
                            bookId = book.id,
                            borrowerName = borrowerName.ifBlank { "a friend" },
                            dueDate = dueDate
                        )
                        navController.navigate(Routes.ShareLendingCode.createRoute(book.id))
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            BookHavenBottomNav(
                selected = BottomNavTab.LIBRARY,
                onSelect = { navController.navigateToTab(it) }
            )
        }
    }
}

@Preview(showBackground = true, name = "LendBookConfigScreenPreview")
@Composable
private fun LendBookConfigScreenPreview() {
    BookHavenTheme {
        LendBookConfigScreen(navController = rememberNavController())
    }
}
