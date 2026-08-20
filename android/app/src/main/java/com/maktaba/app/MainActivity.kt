package com.maktaba.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maktaba.app.nav.Routes
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.ui.screens.*
import com.maktaba.app.ui.theme.BookHavenTheme

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        // Real launch (tapping the app icon) starts at Onboarding.
        // Debug convenience: `adb shell am start -n com.jmkitavi.maktaba/com.maktaba.app.MainActivity --es route <route>`
        // jumps straight to a given screen for screenshotting, bypassing manual navigation.
        val startRoute = intent?.getStringExtra("route")
            ?: routeForNotification(
                intent?.getStringExtra("type"),
                intent?.getStringExtra("copyId"),
                intent?.getStringExtra("catalogBookId")
            )
            ?: Routes.Onboarding.route
        setContent {
            BookHavenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BookHavenApp(
                        startRoute = startRoute,
                        pendingRoute = pendingRoute,
                        onPendingRouteHandled = { pendingRoute = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = routeForNotification(
            intent.getStringExtra("type"),
            intent.getStringExtra("copyId"),
            intent.getStringExtra("catalogBookId")
        )
    }
}

private fun routeForNotification(type: String?, copyId: String?, catalogBookId: String?): String? {
    val copyRouteId = copyId?.takeIf { it.isNotBlank() } ?: return null
    return when (type) {
        "loan_invite_accepted", "loan_reminder" -> Routes.ActiveLoan.createRoute(copyRouteId)
        "return_requested" -> Routes.ReturnConfirmation.createRoute(copyRouteId)
        "return_confirmed" -> Routes.BookDetail.createRoute(
            catalogBookId?.takeIf { it.isNotBlank() } ?: copyRouteId
        )
        "borrower_due_soon", "borrower_due_today", "lender_overdue" ->
            Routes.ReturnReminder.createRoute(copyRouteId)
        else -> null
    }
}

@Composable
fun BookHavenApp(
    startRoute: String,
    pendingRoute: String?,
    onPendingRouteHandled: () -> Unit
) {
    var user by remember { mutableStateOf(FirebaseSession.currentUser) }
    DisposableEffect(Unit) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { user = it.currentUser }
        FirebaseSession.addAuthStateListener(listener)
        onDispose { FirebaseSession.removeAuthStateListener(listener) }
    }
    if (user == null) {
        SignedOutNavHost(startRoute = if (startRoute == Routes.Auth.route) startRoute else Routes.Onboarding.route)
    } else {
        DisposableEffect(user?.uid) {
            LibraryRepository.start(requireNotNull(user).uid)
            onDispose { LibraryRepository.stop() }
        }
        BookHavenNavHost(
            startRoute = if (startRoute == Routes.Onboarding.route) Routes.HomeLibrary.route else startRoute,
            pendingRoute = pendingRoute,
            onPendingRouteHandled = onPendingRouteHandled
        )
    }
}

@Composable
private fun SignedOutNavHost(startRoute: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startRoute) {
        composable(Routes.Onboarding.route) { OnboardingScreen(navController) }
        composable(Routes.Auth.route) { AuthScreen() }
    }
}

@Composable
fun BookHavenNavHost(
    startRoute: String,
    pendingRoute: String?,
    onPendingRouteHandled: () -> Unit
) {
    val navController: NavHostController = rememberNavController()
    val bookIdArg = navArgument("bookId") { type = NavType.StringType }
    val inviteIdArg = navArgument("inviteId") { type = NavType.StringType }
    val inviteCodeArg = navArgument("inviteCode") { type = NavType.StringType }
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
            onPendingRouteHandled()
        }
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
            BookDetailScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
        composable(Routes.ShareLendingCode.route, arguments = listOf(inviteIdArg)) { entry ->
            ShareLendingCodeScreen(navController, requireNotNull(entry.arguments?.getString("inviteId")))
        }
        composable(Routes.LendBookConfig.route, arguments = listOf(bookIdArg)) { entry ->
            LendBookConfigScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
        composable(Routes.ConfirmBorrow.route, arguments = listOf(inviteCodeArg)) { entry ->
            ConfirmBorrowScreen(navController, requireNotNull(entry.arguments?.getString("inviteCode")))
        }
        composable(Routes.ActiveLoan.route, arguments = listOf(bookIdArg)) { entry ->
            ActiveLoanScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
        composable(Routes.ReturnConfirmation.route, arguments = listOf(bookIdArg)) { entry ->
            ReturnConfirmationScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
        composable(Routes.ReturnReminder.route, arguments = listOf(bookIdArg)) { entry ->
            ReturnReminderScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
    }
}
