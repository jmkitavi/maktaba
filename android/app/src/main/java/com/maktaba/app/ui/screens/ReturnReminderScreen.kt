package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
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
import com.maktaba.app.ui.components.GreenButton
import com.maktaba.app.ui.components.OutlinedPillButton
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.*

@Composable
fun ReturnReminderScreen(navController: NavHostController, bookId: String = Routes.DEFAULT_BOOK_ID) {
    val book = LibraryRepository.bookById(bookId) ?: LibraryRepository.books.first()

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "Back",
                    tint = InkBrown,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { navController.popBackStack() }
                )
                Text(
                    "Return Reminder",
                    modifier = Modifier.weight(1f),
                    color = InkBrown,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                Box {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = InkBrown, modifier = Modifier.size(24.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0663C))
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Icon(
                Icons.Filled.AutoStories,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(30.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "A gentle reminder",
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = OliveGreen,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Good stories are meant to be shared.",
                fontSize = 14.sp,
                color = InkBrownSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
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
                        .width(130.dp)
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Reminder",
                            fontFamily = SerifDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = OliveGreen
                        )
                        Icon(
                            Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        buildString { append("\"") ; append(book.title) ; append("\" is due for return in 2 days.") },
                        fontFamily = SerifDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        color = InkBrown
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerTan))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(SurfaceCardAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Due Date", fontSize = 12.sp, color = MutedText)
                            Text("May 25, 2025", fontSize = 14.sp, color = InkBrown, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                GreenButton(
                    text = "View Details",
                    leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = { navController.navigate(Routes.BookDetail.createRoute(book.id)) }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedPillButton(text = "Snooze Reminder", onClick = { navController.popBackStack() })
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceCardAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Need more time?", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = InkBrown)
                    Text(
                        "Snooze your reminder and we'll check back with you.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MutedText
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MutedText)
            }

            Spacer(Modifier.weight(1f))
            BookHavenBottomNav(
                selected = BottomNavTab.LIBRARY,
                onSelect = { navController.navigateToTab(it) }
            )
        }
    }
}

@Preview(showBackground = true, name = "ReturnReminderScreenPreview")
@Composable
private fun ReturnReminderScreenPreview() {
    BookHavenTheme {
        ReturnReminderScreen(navController = rememberNavController())
    }
}
