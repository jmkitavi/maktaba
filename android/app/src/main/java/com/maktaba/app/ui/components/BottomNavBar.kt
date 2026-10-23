package com.maktaba.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation.NavHostController
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency

/**
 * Four destinations. "Collections" and the placeholder "Wishlist" used to occupy half of
 * this bar without doing anything; loans - the only time-sensitive thing in the app - had
 * no top-level home at all and took four taps to reach.
 */
enum class BottomNavTab(val label: String) {
    LIBRARY("Library"),
    LOANS("Loans"),
    WISHLIST("Wishlist"),
    PROFILE("Profile")
}

@Composable
fun MaktabaBottomNav(
    selected: BottomNavTab,
    onSelect: (BottomNavTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaktabaTheme.colors
    val overdueCount = LibraryRepository.activeLoans.count {
        LoanTimeFormatter.urgency(it.dueAt) == LoanUrgency.OVERDUE
    }

    NavigationBar(modifier = modifier, containerColor = colors.backgroundElevated) {
        BottomNavTab.values().forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.navActive,
                    selectedTextColor = colors.navActive,
                    unselectedIconColor = colors.navInactive,
                    unselectedTextColor = colors.navInactive,
                    indicatorColor = colors.surfaceAlt
                ),
                icon = {
                    val icon = when (tab) {
                        BottomNavTab.LIBRARY ->
                            if (isSelected) Icons.AutoMirrored.Filled.MenuBook
                            else Icons.AutoMirrored.Outlined.MenuBook
                        BottomNavTab.LOANS ->
                            if (isSelected) Icons.Filled.SwapHoriz else Icons.Outlined.SwapHoriz
                        BottomNavTab.WISHLIST ->
                            if (isSelected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                        BottomNavTab.PROFILE ->
                            if (isSelected) Icons.Filled.Person else Icons.Outlined.Person
                    }
                    if (tab == BottomNavTab.LOANS && overdueCount > 0) {
                        BadgedBox(badge = {
                            Badge(
                                containerColor = colors.danger,
                                contentColor = colors.onDanger,
                                // The count is announced through the icon description
                                // below, so the badge itself must stay silent.
                                modifier = Modifier.clearAndSetSemantics {}
                            ) { Text(overdueCount.toString()) }
                        }) {
                            Icon(
                                icon,
                                contentDescription = "$overdueCount overdue"
                            )
                        }
                    } else {
                        Icon(icon, contentDescription = null)
                    }
                },
                // The label is this item's only accessible name - clearing its semantics
                // left every destination announcing nothing to a screen reader.
                label = { Text(tab.label) }
            )
        }
    }
}

fun NavHostController.navigateToTab(tab: BottomNavTab) {
    val route = when (tab) {
        BottomNavTab.LIBRARY -> Routes.HomeLibrary.route
        BottomNavTab.LOANS -> Routes.Loans.route
        BottomNavTab.WISHLIST -> Routes.Wishlist.route
        BottomNavTab.PROFILE -> Routes.Profile.route
    }
    navigate(route) {
        popUpTo(Routes.HomeLibrary.route) { inclusive = false; saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
