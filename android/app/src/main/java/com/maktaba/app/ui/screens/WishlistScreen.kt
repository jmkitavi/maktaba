package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.CreamBackground
import com.maktaba.app.ui.theme.MutedText

@Composable
fun WishlistScreen(navController: NavHostController) {
    MaktabaScaffold(
        selectedTab = BottomNavTab.WISHLIST,
        onTabSelected = { navController.navigateToTab(it) }
    ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(title = "Wishlist")
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Your wishlist is empty.", color = MutedText, fontSize = 15.sp)
            }
        }
    }
    }
}
