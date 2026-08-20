package com.maktaba.app.nav

sealed class Routes(val route: String) {
    object ScreenPicker : Routes("screen_picker")
    object Onboarding : Routes("onboarding")
    object Auth : Routes("auth")
    object HomeLibrary : Routes("home_library")
    object Collections : Routes("collections")
    object Wishlist : Routes("wishlist")
    object Profile : Routes("profile")
    object AddBook : Routes("add_book")
    object Notifications : Routes("notifications")
    object ScanEnterCode : Routes("scan_enter_code")

    object BookDetail : Routes("book_detail/{bookId}") {
        fun createRoute(bookId: String) = "book_detail/$bookId"
    }
    object ShareLendingCode : Routes("share_lending_code/{inviteId}") {
        fun createRoute(inviteId: String) = "share_lending_code/$inviteId"
    }
    object LendBookConfig : Routes("lend_book_config/{bookId}") {
        fun createRoute(bookId: String) = "lend_book_config/$bookId"
    }
    object ConfirmBorrow : Routes("confirm_borrow/{inviteCode}") {
        fun createRoute(inviteCode: String) = "confirm_borrow/$inviteCode"
    }
    object ActiveLoan : Routes("active_loan/{bookId}") {
        fun createRoute(bookId: String) = "active_loan/$bookId"
    }
    object ReturnConfirmation : Routes("return_confirmation/{bookId}") {
        fun createRoute(bookId: String) = "return_confirmation/$bookId"
    }
    object ReturnReminder : Routes("return_reminder/{bookId}") {
        fun createRoute(bookId: String) = "return_reminder/$bookId"
    }

    companion object {
        val all = listOf(
            Onboarding, Auth, HomeLibrary, Collections, Wishlist, Profile, AddBook, Notifications,
            ScanEnterCode
        )
    }
}
