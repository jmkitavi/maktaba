package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.ui.components.BookHavenBottomNav
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.*

private data class WishlistEntry(val title: String, val author: String, val coverRes: Int)

/**
 * No design mockup exists for this screen (bottom nav was standardized to 4 tabs per
 * product decision) — simple, themed placeholder using mock data so "Wishlist" is a
 * real, functional destination. Tapping "Add to Library" moves the title into the
 * shared LibraryRepository as an owned mock book.
 */
@Composable
fun WishlistScreen(navController: NavHostController) {
    var wishlist by remember {
        mutableStateOf(
            listOf(
                WishlistEntry("Project Hail Mary", "Andy Weir", R.drawable.cover_atomic_habits),
                WishlistEntry("Circe", "Madeline Miller", R.drawable.cover_alchemist),
                WishlistEntry("The Song of Achilles", "Madeline Miller", R.drawable.cover_crawdads)
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(title = "Wishlist")
            if (wishlist.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Your wishlist is empty.", color = MutedText, fontSize = 15.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(wishlist) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCard)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(entry.coverRes),
                                contentDescription = entry.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, color = InkBrown, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(entry.author, color = MutedText, fontSize = 13.sp)
                            }
                            Text(
                                "Add to Library",
                                color = WoodBrown,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        LibraryRepository.addBook(entry.title, entry.author, entry.coverRes)
                                        wishlist = wishlist.filterNot { it.title == entry.title }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BookHavenBottomNav(
                selected = BottomNavTab.WISHLIST,
                onSelect = { navController.navigateToTab(it) }
            )
        }
    }
}

@Preview(showBackground = true, name = "WishlistScreenPreview")
@Composable
private fun WishlistScreenPreview() {
    BookHavenTheme {
        WishlistScreen(navController = rememberNavController())
    }
}
