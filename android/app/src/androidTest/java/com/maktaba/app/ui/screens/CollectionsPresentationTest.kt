package com.maktaba.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.maktaba.app.data.*
import com.maktaba.app.ui.theme.BookHavenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CollectionsPresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun book(id: String, title: String, status: BookStatus = BookStatus.OWNED) =
        Book(id, title, "A real author", com.maktaba.app.R.drawable.ic_book_arch, status)

    @Test
    fun ownerPreviewExcludesHiddenTitleButRetainsBorrowedCatalogueReference() {
        val collection = CustomCollection("list", "Weekend", listOf("borrowed", "hidden"), false)
        compose.setContent {
            BookHavenTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CollectionPrivacyContent(collection,
                        LibrarySharingSettings(hiddenCatalogIds = setOf("hidden")),
                        listOf(book("borrowed", "Visible borrowed title", BookStatus.BORROWED),
                            book("hidden", "Hidden private title")),
                        onSave = {}, onHiding = {}, onBook = {})
                }
            }
        }
        compose.onNodeWithText("Review reader preview").performScrollTo().performClick()
        compose.onNodeWithText("Visible borrowed title").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hidden private title").assertDoesNotExist()
        compose.onNodeWithText("Borrowed").assertDoesNotExist()
        compose.onNodeWithText("1 visible titles").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun selectorPreservesUnavailableAndBorrowedMembershipWhenSaving() {
        var saved: List<String>? = null
        compose.setContent {
            BookHavenTheme {
                CollectionBookSelection(
                    CustomCollection("list", "References", listOf("borrowed", "unavailable"), false),
                    LoadState.Ready(LibrarySharingSettings(hiddenCatalogIds = setOf("borrowed"))),
                    listOf(book("borrowed", "Borrowed title", BookStatus.BORROWED)),
                    onSave = { saved = it })
            }
        }
        compose.onNodeWithText("Save 2 selected titles").performClick()
        compose.runOnIdle { assertEquals(listOf("borrowed", "unavailable"), saved) }
    }

    @Test
    fun readerListContainsCatalogueMetadataWithoutOwnershipOrAvailability() {
        val reader = PublicReaderProfile("reader", "Mira", "", 1, 2)
        val summary = SharedCollectionSummary("list", "reader", "Weekend Pages", 1, emptyList(), 2)
        val title = SharedCollectionBook("catalog", "Borrowed catalogue title", "An author", "",
            BookFormat.PHYSICAL)
        compose.setContent {
            BookHavenTheme {
                SharedCollectionContent(SharedCollectionPage(summary, reader, listOf(title), false),
                    onReader = {}, onBook = {})
            }
        }
        compose.onNodeWithText("Borrowed catalogue title").assertIsDisplayed()
        compose.onNodeWithText("A curated list of catalogue references, not a statement of ownership. No loan information is shown.")
            .assertIsDisplayed()
        compose.onNodeWithText("Available").assertDoesNotExist()
        compose.onNodeWithText("On Loan").assertDoesNotExist()
        compose.onNodeWithText("Owned").assertDoesNotExist()
        compose.onNodeWithText("View reader Profile").assertExists()
        compose.onNodeWithText("By Mira").assertHasClickAction()
    }

    @Test
    fun failedWishlistLoadDoesNotMasqueradeAsAnEmptyCollection() {
        compose.setContent {
            BookHavenTheme {
                Column {
                    WantToReadContent(emptyList(), false, "Connection failed", onRetry = {}, onBook = {})
                }
            }
        }
        compose.onNodeWithText("Connection failed").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertHasClickAction()
        compose.onNodeWithText("Room for your next read").assertDoesNotExist()
    }

    @Test
    fun independentListsKeepTitleHidingAvailableWhenLibraryIsPrivateAndChoicesFail() {
        var retries = 0
        compose.setContent {
            BookHavenTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LibrarySharingContent(LibrarySharingSettings(libraryName = "My private shelf"),
                        emptyList(), onSave = { _, _ -> }, onHide = { _, _ -> },
                        initialHiddenExpanded = true, hasIndependentCollections = true,
                        candidatesLoading = true, candidatesError = "Title choices unavailable",
                        onRetryCandidates = { retries++ })
                }
            }
        }
        compose.onNodeWithText("Hidden Books").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Title choices unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No catalogue titles yet.").assertDoesNotExist()
        compose.onNodeWithText("Retry titles").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun allHiddenPreviewIsDistinctFromAnEmptyCollection() {
        compose.setContent {
            BookHavenTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CollectionPrivacyContent(
                        CustomCollection("list", "Private selection", listOf("hidden"), true),
                        LibrarySharingSettings(hiddenCatalogIds = setOf("hidden")),
                        listOf(book("hidden", "Hidden private title")),
                        onSave = {}, onHiding = {}, onBook = {}, initialPreview = true)
                }
            }
        }
        compose.onNodeWithText("All selected titles are hidden").assertIsDisplayed()
        compose.onNodeWithText("No titles selected yet").assertDoesNotExist()
        compose.onNodeWithText("Hidden private title").assertDoesNotExist()
        compose.onNodeWithText("0 visible titles").assertIsDisplayed()
    }

    @Test
    fun emptyPreviewDoesNotClaimThatTitlesAreHidden() {
        compose.setContent {
            BookHavenTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CollectionPrivacyContent(
                        CustomCollection("list", "Empty list", emptyList(), false),
                        LibrarySharingSettings(), emptyList(),
                        onSave = {}, onHiding = {}, onBook = {}, initialPreview = true)
                }
            }
        }
        compose.onNodeWithText("No titles selected yet").assertIsDisplayed()
        compose.onNodeWithText("All selected titles are hidden").assertDoesNotExist()
    }

    @Test
    fun emptyCompanionPanelDoesNotAppearForNonemptyCollections() {
        val populated = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            BookHavenTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CollectionsYoursContent(
                        collections = if (populated.value)
                            listOf(CustomCollection("list", "Weekend", emptyList(), false)) else emptyList(),
                        collectionsState = LoadState.Ready(Unit),
                        settings = LoadState.Ready(LibrarySharingSettings()), savedCount = 0,
                        onWishlist = {}, onCollection = {}, onCreate = {}, onSharing = {}, onRetry = {})
                }
            }
        }
        val copy = "Grow your books into little collections — a shelf for the book club, one for the ones you'll never lend."
        compose.onNodeWithText(copy).assertIsDisplayed()
        compose.onNodeWithText("Make room for your next reading list.").assertDoesNotExist()
        compose.runOnIdle { populated.value = true }
        compose.onNodeWithText(copy).assertDoesNotExist()
        compose.onNodeWithText("Weekend").assertIsDisplayed()
    }

    @Test
    fun oneHiddenTitleUsesCatalogueIdentityRegardlessOfCandidatePosition() {
        compose.setContent {
            BookHavenTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LibrarySharingContent(
                        LibrarySharingSettings(libraryName = "Shared shelf", libraryShared = true,
                            hiddenCatalogIds = setOf("paper-garden")),
                        listOf(book("winterlight", "A Room Full of Winter Light"),
                            book("paper-garden", "The Paper Garden")),
                        onSave = { _, _ -> }, onHide = { _, _ -> }, initialHiddenExpanded = true)
                }
            }
        }
        compose.onNodeWithTag("hidden-title-paper-garden").assertIsOn()
        compose.onNodeWithTag("hidden-title-winterlight").assertIsOff()
    }
}
