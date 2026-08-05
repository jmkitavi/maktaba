package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.R
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.theme.BottomNavActive
import com.maktaba.app.ui.theme.BottomNavInactive
import com.maktaba.app.ui.theme.CreamBackgroundLight
import com.maktaba.app.ui.theme.SerifDisplay

/**
 * Standardized bottom navigation, per explicit product decision: every
 * screen in the app uses this same 4-tab set (My Library, Collections,
 * Wishlist, Profile) regardless of what nav items an individual design
 * mock shows.
 */
enum class BottomNavTab(val label: String) {
    LIBRARY("My Library"),
    COLLECTIONS("Collections"),
    WISHLIST("Wishlist"),
    PROFILE("Profile")
}

@Composable
fun BookHavenBottomNav(
    selected: BottomNavTab,
    onSelect: (BottomNavTab) -> Unit = {}
) {
    Column(Modifier.background(CreamBackgroundLight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomNavTab.values().forEach { tab ->
                val isSelected = tab == selected
                val tint = if (isSelected) BottomNavActive else BottomNavInactive
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                ) {
                    when (tab) {
                        BottomNavTab.LIBRARY -> Icon(
                            painter = painterResource(R.drawable.ic_nav_library),
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                        BottomNavTab.COLLECTIONS -> Icon(
                            imageVector = if (isSelected) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                        BottomNavTab.WISHLIST -> Icon(
                            imageVector = if (isSelected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                        BottomNavTab.PROFILE -> Icon(
                            imageVector = if (isSelected) Icons.Filled.Person else Icons.Outlined.Person,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
                .width(120.dp)
                .height(4.dp)
                .background(androidx.compose.ui.graphics.Color(0xFF3B2A1D), RoundedCornerShape(2.dp))
        )
    }
}

/** Navigates the shared bottom-nav tabs, popping back to the tab's root so switching
 * tabs doesn't pile up an ever-growing back stack. */
fun NavHostController.navigateToTab(tab: BottomNavTab) {
    val route = when (tab) {
        BottomNavTab.LIBRARY -> Routes.HomeLibrary.route
        BottomNavTab.COLLECTIONS -> Routes.Collections.route
        BottomNavTab.WISHLIST -> Routes.Wishlist.route
        BottomNavTab.PROFILE -> Routes.Profile.route
    }
    navigate(route) {
        popUpTo(Routes.HomeLibrary.route) { inclusive = false; saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
