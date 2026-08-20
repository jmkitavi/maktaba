package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.theme.*

/**
 * Reached via the notification bell icon on My Library. Lists mock notifications;
 * tapping a "due soon" style item marks it read and opens Return Reminder for that book.
 */
@Composable
fun NotificationsScreen(navController: NavHostController) {
    val notifications = LibraryRepository.notifications

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(title = "Notifications", onBack = { navController.popBackStack() })
            if (notifications.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("You're all caught up.", color = MutedText, fontSize = 15.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (item.isUnread) SurfaceCardAlt else SurfaceCard)
                                .clickable {
                                    if (item.bookId.isBlank()) return@clickable
                                    LibraryRepository.markNotificationRead(item.id)
                                    val route = when (item.type) {
                                        "loan_invite_accepted", "loan_reminder" ->
                                            Routes.ActiveLoan.createRoute(item.bookId)
                                        "return_requested" ->
                                            Routes.ReturnConfirmation.createRoute(item.bookId)
                                        "return_confirmed" ->
                                            Routes.BookDetail.createRoute(
                                                item.catalogBookId.ifBlank { item.bookId }
                                            )
                                        else ->
                                            Routes.ReturnReminder.createRoute(item.bookId)
                                    }
                                    navController.navigate(route)
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (item.isUnread) WoodBrown else DividerTan),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.NotificationsNone,
                                    contentDescription = null,
                                    tint = if (item.isUnread) androidx.compose.ui.graphics.Color.White else InkBrownSoft,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = InkBrown, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(item.message, color = MutedText, fontSize = 13.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "NotificationsScreenPreview")
@Composable
private fun NotificationsScreenPreview() {
    BookHavenTheme {
        NotificationsScreen(navController = rememberNavController())
    }
}
