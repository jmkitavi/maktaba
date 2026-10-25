package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.theme.MaktabaTheme

/**
 * Four destinations. "Collections" and the placeholder "Wishlist" used to occupy half of
 * this bar without doing anything; loans - the only time-sensitive thing in the app - had
 * no top-level home at all and took four taps to reach.
 */
enum class BottomNavTab(val label: String) {
    LIBRARY("Library"),
    LOANS("Loans"),
    COLLECTIONS("Collections"),
    PROFILE("Profile")
}

@Composable
fun MaktabaBottomNav(
    selected: BottomNavTab?,
    onSelect: (BottomNavTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaktabaTheme.colors
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    NavigationBar(
        modifier = modifier.height(76.dp + bottomInset),
        containerColor = colors.backgroundElevated,
        tonalElevation = 0.dp
    ) {
        BottomNavTab.values().forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 8.dp)
                    .background(
                        if (isSelected) colors.sage else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    ),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.navActive,
                    selectedTextColor = colors.navActive,
                    unselectedIconColor = colors.navInactive,
                    unselectedTextColor = colors.navInactive,
                    indicatorColor = Color.Transparent
                ),
                icon = {
                    val icon = when (tab) {
                        BottomNavTab.LIBRARY -> Icons.AutoMirrored.Outlined.MenuBook
                        BottomNavTab.LOANS -> Icons.Outlined.SwapHoriz
                        BottomNavTab.COLLECTIONS -> Icons.Outlined.CollectionsBookmark
                        BottomNavTab.PROFILE -> Icons.Outlined.Person
                    }
                    Box(
                        Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                },
                // The label is this item's only accessible name - clearing its semantics
                // left every destination announcing nothing to a screen reader.
                label = {
                    Text(tab.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 14.sp))
                }
            )
        }
    }
}

fun NavHostController.navigateToTab(tab: BottomNavTab) {
    val route = when (tab) {
        BottomNavTab.LIBRARY -> Routes.HomeLibrary.route
        BottomNavTab.LOANS -> Routes.Loans.route
        BottomNavTab.COLLECTIONS -> Routes.Collections.route
        BottomNavTab.PROFILE -> Routes.Profile.route
    }
    navigate(route) {
        popUpTo(Routes.HomeLibrary.route) { inclusive = false; saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
