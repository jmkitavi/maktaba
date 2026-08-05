package com.maktaba.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.SecondaryButton

/** Debug-only entry screen: lists every real screen so any of them can be
 * opened directly (also reachable via `--es route <route>` intent extra). */
@Composable
fun ScreenPickerScreen(navController: NavHostController) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Book Haven — Screen Picker") }
        items(Routes.all) { route ->
            val target = when (route) {
                is Routes.BookDetail -> route.createRoute(Routes.DEFAULT_BOOK_ID)
                is Routes.ShareLendingCode -> route.createRoute(Routes.DEFAULT_BOOK_ID)
                is Routes.LendBookConfig -> route.createRoute(Routes.DEFAULT_BOOK_ID)
                is Routes.ConfirmBorrow -> route.createRoute(Routes.DEFAULT_BOOK_ID)
                is Routes.ActiveLoan -> route.createRoute(Routes.DEFAULT_BOOK_ID)
                is Routes.ReturnConfirmation -> route.createRoute(Routes.DEFAULT_BOOK_ID)
                is Routes.ReturnReminder -> route.createRoute(Routes.DEFAULT_BOOK_ID)
                else -> route.route
            }
            SecondaryButton(text = route.route) {
                navController.navigate(target)
            }
        }
    }
}

@Preview(showBackground = true, name = "ScreenPickerScreenPreview")
@Composable
private fun ScreenPickerScreenPreview() {
    BookHavenTheme {
        ScreenPickerScreen(navController = rememberNavController())
    }
}
