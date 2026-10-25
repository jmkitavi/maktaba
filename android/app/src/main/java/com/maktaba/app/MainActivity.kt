package com.maktaba.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.key
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
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoanInviteCode
import com.maktaba.app.data.DevicePreferences
import com.maktaba.app.data.CollectionsRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.screens.*
import com.maktaba.app.ui.theme.BookHavenTheme

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DevicePreferences.initialize(this)

        // Real launch starts at Onboarding. A notification, a maktaba:// link, or the debug
        // `--es route <route>` extra selects a deeper destination instead - which is layered
        // *on top of* the library rather than replacing it, so Back always has somewhere to go.
        // One consume-once channel for every entry point. Holding the launch route
        // separately meant it re-fired whenever the nav host was recreated - including
        // after signing out and back in, possibly as a different account.
        pendingRoute = selectPendingRoute(
            hasSavedState = savedInstanceState?.containsKey(PENDING_ROUTE_KEY) == true,
            restoredRoute = savedInstanceState?.getString(PENDING_ROUTE_KEY),
            launchRoute = routeFromIntent(intent)
        )

        setContent {
            BookHavenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MaktabaApp(
                        pendingRoute = pendingRoute,
                        onPendingRouteHandled = ::consumePendingRoute
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = routeFromIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PENDING_ROUTE_KEY, pendingRoute)
        super.onSaveInstanceState(outState)
    }

    private fun consumePendingRoute() {
        pendingRoute = null
        intent?.apply {
            removeExtra("route")
            removeExtra("type")
            removeExtra("copyId")
            removeExtra("catalogBookId")
            data = null
        }
    }

    private fun routeFromIntent(intent: Intent?): String? {
        return intent?.getStringExtra("route")?.let(::validatedDebugRoute)
            ?: routeForLink(intent?.data)
            ?: routeForNotification(
                intent?.getStringExtra("type"),
                intent?.getStringExtra("copyId"),
                intent?.getStringExtra("catalogBookId")
            )
    }

    private companion object {
        const val PENDING_ROUTE_KEY = "pending_route"
    }
}

internal fun selectPendingRoute(
    hasSavedState: Boolean,
    restoredRoute: String?,
    launchRoute: String?
): String? = if (hasSavedState) restoredRoute else launchRoute

internal fun validatedDebugRoute(route: String): String? {
    if (!BuildConfig.DEBUG) return null
    val staticRoutes = setOf(
        Routes.ScreenPicker.route,
        Routes.HomeLibrary.route,
        Routes.Loans.route,
        Routes.Collections.route,
        Routes.Wishlist.route,
        Routes.Profile.route,
        Routes.AddBook.route,
        Routes.Notifications.route,
        Routes.ScanEnterCode.route,
        Routes.Settings.route,
        Routes.SettingsAppearance.route,
        Routes.SettingsNotifications.route,
        Routes.About.route,
        Routes.LibrarySharing.route,
        Routes.CollectionCreate.route,
        Routes.Logout.route,
    )
    if (route in staticRoutes) return route
    val idPattern = "[A-Za-z0-9_-]{6,128}"
    return route.takeIf {
        it.matches(Regex("book_detail/$idPattern")) ||
            it.matches(Regex("edit_book/$idPattern")) ||
            it.matches(Regex("share_lending_code/$idPattern")) ||
            it.matches(Regex("lend_book_config/$idPattern")) ||
            it.matches(Regex("active_loan/$idPattern")) ||
            it.matches(Regex("return_confirmation/$idPattern")) ||
            it.matches(Regex("return_reminder/$idPattern(\\?review=true)?")) ||
            it.matches(Regex("(catalogue_book|shared_library|reader_profile|report_library)/$idPattern")) ||
            it.matches(Regex("(collection|collection_edit|collection_books|collection_privacy|add_to_collection)/$idPattern")) ||
            it.matches(Regex("shared_collection/$idPattern/$idPattern")) ||
            (it.startsWith("confirm_borrow/") && LoanInviteCode.parse(it.substringAfter('/')) != null)
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
        val uid = requireNotNull(user).uid
        key(uid) {
            DisposableEffect(uid) {
                LibraryRepository.start(uid)
                CollectionsRepository.start(uid)
                onDispose {
                    LibraryRepository.stop()
                    CollectionsRepository.stop()
                }
            }
            MaktabaNavHost(
                pendingRoute = pendingRoute,
                onPendingRouteHandled = onPendingRouteHandled
            )
        }
    }
}

@Composable
private fun SignedOutNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.Onboarding.route) {
        composable(Routes.Onboarding.route) { OnboardingScreen(navController) }
        composable(Routes.Auth.route) { AuthScreen(onBack = { navController.popBackStack() }) }
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
    val catalogIdArg = navArgument("catalogId") { type = NavType.StringType }
    val collectionIdArg = navArgument("collectionId") { type = NavType.StringType }
    val ownerUidArg = navArgument("ownerUid") { type = NavType.StringType }
    val readerUidArg = navArgument("readerUid") { type = NavType.StringType }
    val reviewArg = navArgument("review") { type = NavType.BoolType; defaultValue = false }

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
        composable(Routes.Collections.route) { CollectionsScreen(navController) }
        composable(Routes.Wishlist.route) { WishlistScreen(navController) }
        composable(Routes.Profile.route) { ProfileScreen(navController) }
        composable(Routes.Logout.route) { LogoutScreen(navController) }
        composable(Routes.EditOwnedBook.route, arguments = listOf(bookIdArg)) { entry ->
            EditOwnedBookScreen(navController, requireNotNull(entry.arguments?.getString("bookId")))
        }
        composable(Routes.AddBook.route) { AddBookScreen(navController) }
        composable(Routes.Notifications.route) { NotificationsScreen(navController) }
        composable(Routes.ScanEnterCode.route) { ScanEnterCodeScreen(navController) }
        composable(Routes.Settings.route) { SettingsScreen(navController) }
        composable(Routes.SettingsAppearance.route) { SettingsScreen(navController, SettingsPage.APPEARANCE) }
        composable(Routes.SettingsNotifications.route) { SettingsScreen(navController, SettingsPage.NOTIFICATIONS) }
        composable(Routes.About.route) { SettingsScreen(navController, SettingsPage.ABOUT) }
        composable(Routes.LibrarySharing.route) { LibrarySharingScreen(navController) }
        composable(Routes.CollectionCreate.route) { CollectionCreateScreen(navController) }
        composable(Routes.CatalogueBookDetail.route, arguments = listOf(catalogIdArg)) { entry ->
            BookDetailScreen(navController, requireNotNull(entry.arguments?.getString("catalogId")), catalogueOnly = true)
        }
        composable(Routes.SharedLibrary.route, arguments = listOf(ownerUidArg)) { entry ->
            SharedLibraryScreen(navController, requireNotNull(entry.arguments?.getString("ownerUid")))
        }
        composable(Routes.CollectionDetail.route, arguments = listOf(collectionIdArg)) { entry ->
            CollectionDetailScreen(navController, requireNotNull(entry.arguments?.getString("collectionId")))
        }
        composable(Routes.CollectionEdit.route, arguments = listOf(collectionIdArg)) { entry ->
            CollectionEditScreen(navController, requireNotNull(entry.arguments?.getString("collectionId")))
        }
        composable(Routes.CollectionBooks.route, arguments = listOf(collectionIdArg)) { entry ->
            CollectionBooksScreen(navController, requireNotNull(entry.arguments?.getString("collectionId")))
        }
        composable(Routes.CollectionPrivacy.route, arguments = listOf(collectionIdArg)) { entry ->
            CollectionPrivacyScreen(navController, requireNotNull(entry.arguments?.getString("collectionId")))
        }
        composable(Routes.AddToCollection.route, arguments = listOf(catalogIdArg)) { entry ->
            AddToCollectionScreen(navController, requireNotNull(entry.arguments?.getString("catalogId")))
        }
        composable(Routes.ReaderProfile.route, arguments = listOf(readerUidArg)) { entry ->
            ReaderProfileScreen(navController, requireNotNull(entry.arguments?.getString("readerUid")))
        }
        composable(Routes.SharedCollection.route, arguments = listOf(ownerUidArg, collectionIdArg)) { entry ->
            SharedCollectionScreen(
                navController, requireNotNull(entry.arguments?.getString("ownerUid")),
                requireNotNull(entry.arguments?.getString("collectionId"))
            )
        }
        composable(Routes.ReportLibrary.route, arguments = listOf(ownerUidArg)) { entry ->
            ReportLibraryScreen(navController, requireNotNull(entry.arguments?.getString("ownerUid")))
        }
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
        composable(Routes.ReturnReminder.route, arguments = listOf(bookIdArg, reviewArg)) { entry ->
            ReturnReminderScreen(
                navController, requireNotNull(entry.arguments?.getString("bookId")),
                review = entry.arguments?.getBoolean("review") == true
            )
        }
    }
}
