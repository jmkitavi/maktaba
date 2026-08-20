package com.maktaba.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import com.maktaba.app.R
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.theme.BottomNavActive
import com.maktaba.app.ui.theme.BottomNavInactive
import com.maktaba.app.ui.theme.CreamBackgroundLight

enum class BottomNavTab(val label: String) {
    LIBRARY("My Library"),
    COLLECTIONS("Collections"),
    WISHLIST("Wishlist"),
    PROFILE("Profile")
}

@Composable
fun BookHavenBottomNav(
    selected: BottomNavTab,
    onSelect: (BottomNavTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier, containerColor = CreamBackgroundLight) {
        BottomNavTab.values().forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BottomNavActive,
                    selectedTextColor = BottomNavActive,
                    unselectedIconColor = BottomNavInactive,
                    unselectedTextColor = BottomNavInactive,
                    indicatorColor = CreamBackgroundLight
                ),
                icon = {
                    when (tab) {
                        BottomNavTab.LIBRARY -> Icon(painterResource(R.drawable.ic_nav_library), null)
                        BottomNavTab.COLLECTIONS -> Icon(
                            if (isSelected) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            null
                        )
                        BottomNavTab.WISHLIST -> Icon(
                            if (isSelected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            null
                        )
                        BottomNavTab.PROFILE -> Icon(
                            if (isSelected) Icons.Filled.Person else Icons.Outlined.Person,
                            null
                        )
                    }
                },
                label = { Text(tab.label) }
            )
        }
    }
}

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
