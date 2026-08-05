package com.maktaba.app.nav

/**
 * Route patterns use an optional `bookId` query-style argument (default supplied via
 * navArgument in MainActivity) so every route is still reachable bare from the debug
 * Screen Picker / `--es route` launch, while real in-app navigation always passes a
 * concrete book id via [createRoute].
 */
sealed class Routes(val route: String) {
    object ScreenPicker : Routes("screen_picker")
    object Onboarding : Routes("onboarding")
    object HomeLibrary : Routes("home_library")
    object Collections : Routes("collections")
    object Wishlist : Routes("wishlist")
    object Profile : Routes("profile")
    object AddBook : Routes("add_book")
    object Notifications : Routes("notifications")
    object ScanEnterCode : Routes("scan_enter_code")

    object BookDetail : Routes("book_detail?bookId={bookId}") {
        fun createRoute(bookId: String) = "book_detail?bookId=$bookId"
    }
    object ShareLendingCode : Routes("share_lending_code?bookId={bookId}") {
        fun createRoute(bookId: String) = "share_lending_code?bookId=$bookId"
    }
    object LendBookConfig : Routes("lend_book_config?bookId={bookId}") {
        fun createRoute(bookId: String) = "lend_book_config?bookId=$bookId"
    }
    object ConfirmBorrow : Routes("confirm_borrow?bookId={bookId}") {
        fun createRoute(bookId: String) = "confirm_borrow?bookId=$bookId"
    }
    object ActiveLoan : Routes("active_loan?bookId={bookId}") {
        fun createRoute(bookId: String) = "active_loan?bookId=$bookId"
    }
    object ReturnConfirmation : Routes("return_confirmation?bookId={bookId}") {
        fun createRoute(bookId: String) = "return_confirmation?bookId=$bookId"
    }
    object ReturnReminder : Routes("return_reminder?bookId={bookId}") {
        fun createRoute(bookId: String) = "return_reminder?bookId=$bookId"
    }

    companion object {
        val all = listOf(
            Onboarding, HomeLibrary, Collections, Wishlist, Profile, AddBook, Notifications,
            BookDetail, ScanEnterCode, ShareLendingCode,
            LendBookConfig, ConfirmBorrow, ActiveLoan, ReturnConfirmation, ReturnReminder
        )

        /** Default book id used when a detail-oriented route is opened without an explicit id
         * (e.g. via the debug Screen Picker). */
        const val DEFAULT_BOOK_ID = "night_circus"
    }
}
