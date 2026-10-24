package com.maktaba.app.nav

import java.net.URLEncoder

private fun routeArgument(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

sealed class Routes(val route: String) {
    object ScreenPicker : Routes("screen_picker")
    object Onboarding : Routes("onboarding")
    object Auth : Routes("auth")
    object HomeLibrary : Routes("home_library")
    object Loans : Routes("loans")
    object Wishlist : Routes("wishlist")
    object Collections : Routes("collections")
    object Profile : Routes("profile")
    object Settings : Routes("settings")
    object SettingsAppearance : Routes("settings/appearance")
    object SettingsNotifications : Routes("settings/notifications")
    object About : Routes("settings/about")
    object LibrarySharing : Routes("library_sharing")
    object CollectionCreate : Routes("collection_create")
    object AddBook : Routes("add_book")
    object Notifications : Routes("notifications")
    object ScanEnterCode : Routes("scan_enter_code")

    object BookDetail : Routes("book_detail/{bookId}") {
        fun createRoute(bookId: String) = "book_detail/${routeArgument(bookId)}"
    }
    object ShareLendingCode : Routes("share_lending_code/{inviteId}") {
        fun createRoute(inviteId: String) = "share_lending_code/${routeArgument(inviteId)}"
    }
    object LendBookConfig : Routes("lend_book_config/{bookId}") {
        fun createRoute(bookId: String) = "lend_book_config/${routeArgument(bookId)}"
    }
    object ConfirmBorrow : Routes("confirm_borrow/{inviteCode}") {
        fun createRoute(inviteCode: String) = "confirm_borrow/${routeArgument(inviteCode)}"
    }
    object ActiveLoan : Routes("active_loan/{bookId}") {
        fun createRoute(bookId: String) = "active_loan/${routeArgument(bookId)}"
    }
    object ReturnConfirmation : Routes("return_confirmation/{bookId}") {
        fun createRoute(bookId: String) = "return_confirmation/${routeArgument(bookId)}"
    }
    object ReturnReminder : Routes("return_reminder/{bookId}?review={review}") {
        fun createRoute(bookId: String, review: Boolean = false) =
            "return_reminder/${routeArgument(bookId)}" + if (review) "?review=true" else ""
    }
    object CatalogueBookDetail : Routes("catalogue_book/{catalogId}") {
        fun createRoute(catalogId: String) = "catalogue_book/${routeArgument(catalogId)}"
    }
    object SharedLibrary : Routes("shared_library/{ownerUid}") {
        fun createRoute(ownerUid: String) = "shared_library/${routeArgument(ownerUid)}"
    }
    object CollectionDetail : Routes("collection/{collectionId}") {
        fun createRoute(collectionId: String) = "collection/${routeArgument(collectionId)}"
    }
    object CollectionEdit : Routes("collection_edit/{collectionId}") {
        fun createRoute(collectionId: String) = "collection_edit/${routeArgument(collectionId)}"
    }
    object CollectionBooks : Routes("collection_books/{collectionId}") {
        fun createRoute(collectionId: String) = "collection_books/${routeArgument(collectionId)}"
    }
    object CollectionPrivacy : Routes("collection_privacy/{collectionId}") {
        fun createRoute(collectionId: String) = "collection_privacy/${routeArgument(collectionId)}"
    }
    object AddToCollection : Routes("add_to_collection/{catalogId}") {
        fun createRoute(catalogId: String) = "add_to_collection/${routeArgument(catalogId)}"
    }
    object ReaderProfile : Routes("reader_profile/{readerUid}") {
        fun createRoute(readerUid: String) = "reader_profile/${routeArgument(readerUid)}"
    }
    object SharedCollection : Routes("shared_collection/{ownerUid}/{collectionId}") {
        fun createRoute(ownerUid: String, collectionId: String) =
            "shared_collection/${routeArgument(ownerUid)}/${routeArgument(collectionId)}"
    }
    object ReportLibrary : Routes("report_library/{ownerUid}") {
        fun createRoute(ownerUid: String) = "report_library/${routeArgument(ownerUid)}"
    }

    companion object {
        val all = listOf(
            Onboarding, Auth, HomeLibrary, Loans, Collections, Wishlist, Profile, AddBook,
            Notifications, ScanEnterCode, Settings, LibrarySharing
        )
    }
}
