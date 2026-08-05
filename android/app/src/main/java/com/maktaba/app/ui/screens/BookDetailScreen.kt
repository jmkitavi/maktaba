package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
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
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookHavenBottomNav
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.SecondaryButton
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.*

@Composable
fun BookDetailScreen(navController: NavHostController, bookId: String = Routes.DEFAULT_BOOK_ID) {
    val book = LibraryRepository.bookById(bookId) ?: LibraryRepository.books.first()

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        // Left-edge bookshelf/lamp bleed decoration
        Image(
            painter = painterResource(R.drawable.illus_book_detail),
            contentDescription = null,
            contentScale = ContentScale.FillHeight,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(0.55f)
                .width(70.dp)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
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
                    "Book Detail",
                    modifier = Modifier.weight(1f),
                    color = InkBrown,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = InkBrown)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(4.dp))
                Image(
                    painter = painterResource(book.coverRes),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.46f)
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    book.title,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = InkBrown,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    book.author.uppercase(),
                    fontFamily = SansBody,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = WoodBrown,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(60.dp)
                        .height(1.dp)
                        .background(DividerTan)
                )
                Spacer(Modifier.height(8.dp))

                // Availability badge — reflects the book's current status.
                val (badgeText, badgeBg, badgeFg) = when (book.status) {
                    BookStatus.OWNED -> Triple("Available", SuccessGreenBg, SuccessGreen)
                    BookStatus.LENT_OUT -> Triple("Lent Out", ChipUnselectedBg, WoodBrown)
                    BookStatus.BORROWED -> Triple("Borrowed", ChipUnselectedBg, WoodBrown)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeBg)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = badgeFg, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(badgeText, color = badgeFg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    book.description,
                    fontFamily = SansBody,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = InkBrownSoft,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerTan)
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetaItem(Icons.Filled.MenuBook, "Genre", book.genre)
                    MetaItem(Icons.Filled.CalendarToday, "Published", book.published)
                    MetaItem(Icons.Filled.Description, "Pages", book.pages.toString())
                }

                Spacer(Modifier.height(10.dp))
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                when (book.status) {
                    BookStatus.OWNED -> PrimaryButton(
                        text = "Lend Book",
                        leadingIcon = { Icon(Icons.Filled.People, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Routes.LendBookConfig.createRoute(book.id)) }
                    )
                    BookStatus.LENT_OUT -> PrimaryButton(
                        text = "View Active Loan",
                        leadingIcon = { Icon(Icons.Filled.People, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Routes.ActiveLoan.createRoute(book.id)) }
                    )
                    BookStatus.BORROWED -> PrimaryButton(
                        text = "View Loan",
                        leadingIcon = { Icon(Icons.Filled.People, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Routes.ActiveLoan.createRoute(book.id)) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Edit Details",
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = {}
                )
                Spacer(Modifier.height(6.dp))
            }

            BookHavenBottomNav(
                selected = BottomNavTab.LIBRARY,
                onSelect = { navController.navigateToTab(it) }
            )
        }
    }
}

@Composable
private fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SurfaceCardAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = MutedText)
        Text(value, fontSize = 13.sp, color = InkBrown, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true, name = "BookDetailScreenPreview_Owned")
@Composable
private fun BookDetailScreenPreview_Owned() {
    BookHavenTheme {
        BookDetailScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true, name = "BookDetailScreenPreview_LentOut")
@Composable
private fun BookDetailScreenPreview_LentOut() {
    BookHavenTheme {
        BookDetailScreen(navController = rememberNavController(), bookId = "hobbit")
    }
}

@Preview(showBackground = true, name = "BookDetailScreenPreview_Borrowed")
@Composable
private fun BookDetailScreenPreview_Borrowed() {
    BookHavenTheme {
        BookDetailScreen(navController = rememberNavController(), bookId = "mockingbird")
    }
}
