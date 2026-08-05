package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.ui.components.BookHavenBottomNav
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.*

private data class ProfileMenuItem(val label: String, val icon: ImageVector)

/**
 * No design mockup exists for this screen (bottom nav was standardized to 4 tabs per
 * product decision) — simple, themed placeholder using mock account data so "Profile"
 * is a real, functional destination.
 */
@Composable
fun ProfileScreen(navController: NavHostController) {
    val books = LibraryRepository.books
    val owned = books.count { it.status == BookStatus.OWNED }
    val lentOut = books.count { it.status == BookStatus.LENT_OUT }
    val borrowed = books.count { it.status == BookStatus.BORROWED }

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(title = "Profile")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(WoodBrown),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Jamie Kitavi", color = InkBrown, fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Book Haven member since 2024", color = MutedText, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .padding(vertical = 16.dp)
                ) {
                    ProfileStat(label = "Owned", value = owned, modifier = Modifier.weight(1f))
                    ProfileStat(label = "Lent Out", value = lentOut, modifier = Modifier.weight(1f))
                    ProfileStat(label = "Borrowed", value = borrowed, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                val menuItems = listOf(
                    ProfileMenuItem("Notifications", Icons.Filled.Notifications),
                    ProfileMenuItem("Settings", Icons.Outlined.Settings),
                    ProfileMenuItem("Help & Support", Icons.Outlined.HelpOutline),
                    ProfileMenuItem("About Book Haven", Icons.Outlined.Info),
                    ProfileMenuItem("Log Out", Icons.Outlined.Logout)
                )
                menuItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (item.label == "Notifications") {
                                    navController.navigate(com.maktaba.app.nav.Routes.Notifications.route)
                                }
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(item.icon, contentDescription = null, tint = InkBrownSoft, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(item.label, color = InkBrown, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MutedText, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BookHavenBottomNav(
                selected = BottomNavTab.PROFILE,
                onSelect = { navController.navigateToTab(it) }
            )
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = InkBrown, fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(label, color = MutedText, fontSize = 12.sp)
    }
}

@Preview(showBackground = true, name = "ProfileScreenPreview")
@Composable
private fun ProfileScreenPreview() {
    BookHavenTheme {
        ProfileScreen(navController = rememberNavController())
    }
}
