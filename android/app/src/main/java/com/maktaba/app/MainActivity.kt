package com.maktaba.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.screens.*
import com.maktaba.app.ui.theme.BookHavenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Real launch (tapping the app icon) starts at Onboarding.
        // Debug convenience: `adb shell am start -n com.maktaba.app/.MainActivity --es route <route>`
        // jumps straight to a given screen for screenshotting, bypassing manual navigation.
        val startRoute = intent?.getStringExtra("route") ?: Routes.Onboarding.route
        setContent {
            BookHavenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BookHavenNavHost(startRoute = startRoute)
                }
            }
        }
    }
}

@Composable
fun BookHavenNavHost(startRoute: String) {
    val navController: NavHostController = rememberNavController()
    val bookIdArg = navArgument("bookId") {
        type = NavType.StringType
        defaultValue = Routes.DEFAULT_BOOK_ID
    }
    NavHost(navController = navController, startDestination = startRoute) {
        composable(Routes.ScreenPicker.route) { ScreenPickerScreen(navController) }
        composable(Routes.Onboarding.route) { OnboardingScreen(navController) }
        composable(Routes.HomeLibrary.route) { HomeLibraryScreen(navController) }
        composable(Routes.Collections.route) { CollectionsScreen(navController) }
        composable(Routes.Wishlist.route) { WishlistScreen(navController) }
        composable(Routes.Profile.route) { ProfileScreen(navController) }
        composable(Routes.AddBook.route) { AddBookScreen(navController) }
        composable(Routes.Notifications.route) { NotificationsScreen(navController) }
        composable(Routes.ScanEnterCode.route) { ScanEnterCodeScreen(navController) }
        composable(Routes.BookDetail.route, arguments = listOf(bookIdArg)) { entry ->
            BookDetailScreen(navController, entry.arguments?.getString("bookId") ?: Routes.DEFAULT_BOOK_ID)
        }
        composable(Routes.ShareLendingCode.route, arguments = listOf(bookIdArg)) { entry ->
            ShareLendingCodeScreen(navController, entry.arguments?.getString("bookId") ?: Routes.DEFAULT_BOOK_ID)
        }
        composable(Routes.LendBookConfig.route, arguments = listOf(bookIdArg)) { entry ->
            LendBookConfigScreen(navController, entry.arguments?.getString("bookId") ?: Routes.DEFAULT_BOOK_ID)
        }
        composable(Routes.ConfirmBorrow.route, arguments = listOf(bookIdArg)) { entry ->
            ConfirmBorrowScreen(navController, entry.arguments?.getString("bookId") ?: Routes.DEFAULT_BOOK_ID)
        }
        composable(Routes.ActiveLoan.route, arguments = listOf(bookIdArg)) { entry ->
            ActiveLoanScreen(navController, entry.arguments?.getString("bookId") ?: Routes.DEFAULT_BOOK_ID)
        }
        composable(Routes.ReturnConfirmation.route, arguments = listOf(bookIdArg)) { entry ->
            ReturnConfirmationScreen(navController, entry.arguments?.getString("bookId") ?: Routes.DEFAULT_BOOK_ID)
        }
        composable(Routes.ReturnReminder.route, arguments = listOf(bookIdArg)) { entry ->
            ReturnReminderScreen(navController, entry.arguments?.getString("bookId") ?: Routes.DEFAULT_BOOK_ID)
        }
    }
}
