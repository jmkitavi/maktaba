package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.BookFormat
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.theme.CreamBackground
import com.maktaba.app.ui.theme.InkBrown
import com.maktaba.app.ui.theme.MutedText
import com.maktaba.app.ui.theme.SurfaceCard

/**
 * No design mockup exists for this screen (bottom nav was standardized to 4 tabs per
 * product decision even though individual mockups differ) — built as a simple, themed
 * placeholder using mock data so the "Collections" tab is a real, functional destination.
 */
@Composable
fun CollectionsScreen(navController: NavHostController) {
    val books = LibraryRepository.books
    val collections = listOf(
        "Maktaba Collection" to LibraryRepository.maktabaCollection,
        "Currently Reading" to books.filter { it.status == com.maktaba.app.data.BookStatus.OWNED }.take(3),
        "On Loan" to books.filter { it.status == com.maktaba.app.data.BookStatus.LENT_OUT },
        "Borrowed From Friends" to books.filter { it.status == com.maktaba.app.data.BookStatus.BORROWED }
    )

    MaktabaScaffold(
        selectedTab = BottomNavTab.COLLECTIONS,
        onTabSelected = { navController.navigateToTab(it) }
    ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(title = "Collections")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp)
            ) {
                collections.forEach { (name, list) ->
                    if (list.isNotEmpty()) {
                        Text(
                            name,
                            color = InkBrown,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 10.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(list) { book ->
                                Column(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceCard)
                                ) {
                                    BookCoverImage(
                                        book = book,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.72f)
                                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    )
                                    Text(
                                        book.title,
                                        color = InkBrown,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                    if (book.format == BookFormat.DIGITAL) {
                                        Text(
                                            "Digital",
                                            color = MutedText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (collections.all { it.second.isEmpty() }) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("No collections yet.", color = MutedText, fontSize = 15.sp)
                    }
                }
            }
        }
    }
    }
}

@Preview(showBackground = true, name = "CollectionsScreenPreview")
@Composable
private fun CollectionsScreenPreview() {
    BookHavenTheme {
        CollectionsScreen(navController = rememberNavController())
    }
}
