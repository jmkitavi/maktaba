package com.maktaba.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoanInviteCode
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.screens.*
import com.maktaba.app.ui.theme.BookHavenTheme

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        // Real launch starts at Onboarding. A notification, a maktaba:// link, or the debug
        // `--es route <route>` extra selects a deeper destination instead - which is layered
        // *on top of* the library rather than replacing it, so Back always has somewhere to go.
        // One consume-once channel for every entry point. Holding the launch route
        // separately meant it re-fired whenever the nav host was recreated - including
        // after signing out and back in, possibly as a different account.
        pendingRoute = intent?.getStringExtra("route")
            ?: routeForLink(intent?.data)
            ?: routeForNotification(
                intent?.getStringExtra("type"),
                intent?.getStringExtra("copyId"),
                intent?.getStringExtra("catalogBookId")
            )

        setContent {
            BookHavenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MaktabaApp(
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
        pendingRoute = routeForLink(intent.data)
            ?: routeForNotification(
                intent.getStringExtra("type"),
                intent.getStringExtra("copyId"),
                intent.getStringExtra("catalogBookId")
            )
    }
}

/** Handles `maktaba://loan?code=ABCD-1234`, which is what the lending QR encodes. */
internal fun routeForLink(uri: Uri?): String? {
    val data = uri ?: return null
    if (data.scheme != "maktaba" || data.host != "loan") return null
    // An externally supplied code ends up in a navigation route, so validate it against
    // the real code format rather than merely checking it is non-blank. Anything that is
    // not AAAA-9999 - path separators, injections, junk - is rejected here.
    val code = LoanInviteCode.parse(data.getQueryParameter("code")) ?: return null
    return Routes.ConfirmBorrow.createRoute(code.value)
}

internal fun routeForNotification(
    type: String?,
    copyId: String?,
    catalogBookId: String?
): String? {
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
fun MaktabaApp(
    pendingRoute: String?,
    onPendingRouteHandled: () -> Unit
) {
    var user by remember { mutableStateOf(FirebaseSession.currentUser) }
    DisposableEffect(Unit) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
            user = it.currentUser
        }
        FirebaseSession.addAuthStateListener(listener)
        onDispose { FirebaseSession.removeAuthStateListener(listener) }
    }

    if (user == null) {
        SignedOutNavHost()
    } else {
        DisposableEffect(user?.uid) {
            LibraryRepository.start(requireNotNull(user).uid)
            onDispose { LibraryRepository.stop() }
        }
        MaktabaNavHost(
            pendingRoute = pendingRoute,
            onPendingRouteHandled = onPendingRouteHandled
        )
    }
}

@Composable
private fun SignedOutNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.Onboarding.route) {
        composable(Routes.Onboarding.route) { OnboardingScreen(navController) }
        composable(Routes.Auth.route) { AuthScreen() }
    }
}

@Composable
fun MaktabaNavHost(
    pendingRoute: String?,
    onPendingRouteHandled: () -> Unit
) {
    val navController: NavHostController = rememberNavController()
    val bookIdArg = navArgument("bookId") { type = NavType.StringType }
    val inviteIdArg = navArgument("inviteId") { type = NavType.StringType }
    val inviteCodeArg = navArgument("inviteCode") { type = NavType.StringType }

    // The library is always the root of the stack, so Back from a notification-launched
    // screen lands on the shelf instead of dropping the user out of the app.
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        if (route != Routes.HomeLibrary.route) {
            navController.navigate(route) { launchSingleTop = true }
        }
        onPendingRouteHandled()
    }

    NavHost(navController = navController, startDestination = Routes.HomeLibrary.route) {
        composable(Routes.ScreenPicker.route) { ScreenPickerScreen(navController) }
        composable(Routes.HomeLibrary.route) { HomeLibraryScreen(navController) }
        composable(Routes.Loans.route) { LoansScreen(navController) }
        composable(Routes.Wishlist.route) { WishlistScreen(navController) }
        composable(Routes.Profile.route) { ProfileScreen(navController) }
        composable(Routes.AddBook.route) { AddBookScreen(navController) }
        composable(Routes.Notifications.route) { NotificationsScreen(navController) }
        composable(Routes.ScanEnterCode.route) { ScanEnterCodeScreen(navController) }
        composable(Routes.BookDetail.route, arguments = listOf(bookIdArg)) { entry ->
            BookDetailScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
        composable(Routes.ShareLendingCode.route, arguments = listOf(inviteIdArg)) { entry ->
            ShareLendingCodeScreen(
                navController,
                requireNotNull(entry.arguments?.getString("inviteId"))
            )
        }
        composable(Routes.LendBookConfig.route, arguments = listOf(bookIdArg)) { entry ->
            LendBookConfigScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
        composable(Routes.ConfirmBorrow.route, arguments = listOf(inviteCodeArg)) { entry ->
            ConfirmBorrowScreen(
                navController,
                requireNotNull(entry.arguments?.getString("inviteCode"))
            )
        }
        composable(Routes.ActiveLoan.route, arguments = listOf(bookIdArg)) { entry ->
            ActiveLoanScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
        composable(Routes.ReturnConfirmation.route, arguments = listOf(bookIdArg)) { entry ->
            ReturnConfirmationScreen(
                navController,
                requireNotNull(entry.arguments?.getString("bookId"))
            )
        }
        composable(Routes.ReturnReminder.route, arguments = listOf(bookIdArg)) { entry ->
            ReturnReminderScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
    }
}
