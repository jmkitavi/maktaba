package com.maktaba.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.maktaba.app.ui.components.BookCoverCard
import com.maktaba.app.ui.components.BookHavenBottomNav
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.*

private val filters = listOf("All", "Owned", "Lent Out", "Borrowed")

private fun statusForFilter(filter: String): BookStatus? = when (filter) {
    "Owned" -> BookStatus.OWNED
    "Lent Out" -> BookStatus.LENT_OUT
    "Borrowed" -> BookStatus.BORROWED
    else -> null
}

@Composable
fun HomeLibraryScreen(navController: NavHostController) {
    var selectedFilter by remember { mutableStateOf("All") }
    var showAddSheet by remember { mutableStateOf(false) }
    val books = LibraryRepository.books
    val filteredBooks = statusForFilter(selectedFilter)?.let { status ->
        books.filter { it.status == status }
    } ?: books
    val unreadCount = LibraryRepository.unreadNotificationCount

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header — illustration moved to top-left (no drawer nav exists in this app),
            // notification bell added top-right per product decision.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.illus_home_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "My Library",
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = InkBrown,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Icon(
                        Icons.Filled.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = InkBrown,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { navController.navigate(Routes.Notifications.route) }
                    )
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(WoodBrown)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(45.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(SurfaceCard)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MutedText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Search books, authors, genres...",
                    color = MutedText,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.Tune, contentDescription = "Filter", tint = MutedText, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.height(14.dp))

            // Filter chips — now actually filter the grid below by BookStatus.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) ChipSelectedBg else ChipUnselectedBg)
                            .clickable { selectedFilter = filter },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            filter,
                            color = if (isSelected) ChipSelectedText else ChipUnselectedText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (filteredBooks.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No books in this filter yet.", color = MutedText, fontSize = 15.sp)
                }
            } else {
                // Book grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredBooks, key = { it.id }) { book ->
                        BookCoverCard(
                            book = book,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate(Routes.BookDetail.createRoute(book.id)) }
                        )
                    }
                }
            }
        }

        // FAB — opens an action sheet: Add a Book / Borrow a Book.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 86.dp)
                .size(58.dp)
                .clip(CircleShape)
                .background(WoodBrown)
                .clickable { showAddSheet = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Book", tint = Color.White, modifier = Modifier.size(26.dp))
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BookHavenBottomNav(
                selected = BottomNavTab.LIBRARY,
                onSelect = { navController.navigateToTab(it) }
            )
        }

        AnimatedVisibility(visible = showAddSheet) {
            AddBookActionSheet(
                onDismiss = { showAddSheet = false },
                onAddBook = {
                    showAddSheet = false
                    navController.navigate(Routes.AddBook.route)
                },
                onBorrowBook = {
                    showAddSheet = false
                    navController.navigate(Routes.ScanEnterCode.route)
                }
            )
        }
    }
}

@Composable
private fun AddBookActionSheet(
    onDismiss: () -> Unit,
    onAddBook: () -> Unit,
    onBorrowBook: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CreamBackgroundLight)
                .clickable(enabled = false) {}
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                "What would you like to do?",
                color = InkBrown,
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(18.dp))
            ActionSheetOption(title = "Add a Book", subtitle = "Add a new book to your library", onClick = onAddBook)
            Spacer(Modifier.height(12.dp))
            ActionSheetOption(title = "Borrow a Book", subtitle = "Scan or enter a lending code", onClick = onBorrowBook)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionSheetOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(title, color = InkBrown, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = MutedText, fontSize = 13.sp)
    }
}

@Preview(showBackground = true, name = "HomeLibraryScreenPreview")
@Composable
private fun HomeLibraryScreenPreview() {
    BookHavenTheme {
        HomeLibraryScreen(navController = rememberNavController())
    }
}
