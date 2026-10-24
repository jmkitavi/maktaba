package com.maktaba.app.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.journeys.JourneyCapture
import com.maktaba.app.journeys.JourneyFixtures
import com.maktaba.app.ui.theme.BookHavenTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class CoreLibraryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val catalogueBook = Book(
        id = "catalogue-test", catalogId = "catalogue-test", title = "A Test Title",
        author = "A Test Author", coverRes = 0, status = BookStatus.OWNED,
        format = BookFormat.PHYSICAL, genre = "Essays"
    )

    @Test
    fun catalogueDetailsCanSaveAndAddReferenceWithoutPrivateCopyActions() {
        var adds = 0
        var saves = 0
        composeRule.setContent {
            BookHavenTheme {
                BookDetailContent(
                    state = LibraryBookDetailState(
                        selection = LibraryDetailSelection(catalogueBook, privateCopy = false),
                        loaded = true, savedLoaded = true
                    ),
                    onBack = {}, onRetry = {}, onSave = { saves++ }, onLend = {},
                    onLoan = {}, onRemove = {}, onAddToCollection = { adds++ },
                    onToggleHidden = {}, onManageSharing = {}
                )
            }
        }
        composeRule.onNodeWithText("Catalogue Title").assertIsDisplayed()
        composeRule.onNodeWithText("Book detail").assertDoesNotExist()
        composeRule.onNodeWithText("Lend This Book").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Save to Want to Read").performClick()
        composeRule.onNodeWithText("More book actions").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove from library").assertDoesNotExist()
        composeRule.onNodeWithText("Hide from shared views").assertDoesNotExist()
        composeRule.onNodeWithText("Add to collection").performClick()
        composeRule.runOnIdle { assertEquals(1, adds); assertEquals(1, saves) }
    }

    @Test
    fun failedInitialLibraryLoadShowsRetryRatherThanEmptyShelf() {
        var retries = 0
        composeRule.setContent {
            BookHavenTheme {
                HomeLibraryContent(
                    books = emptyList(), loans = emptyList(), hasLoaded = false,
                    error = "Connection unavailable", loansLoaded = false, loansError = null,
                    unreadCount = 0, onBook = {}, onAdd = {}, onBorrow = {},
                    onNotifications = {}, onTab = {}, onRetry = { retries++ }
                )
            }
        }
        composeRule.onNodeWithText("Your shelf is empty").assertDoesNotExist()
        composeRule.onNodeWithText("Connection unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun profileSharingFailureDoesNotClaimPrivateAndSettingsNavigates() {
        var settingsOpens = 0
        composeRule.setContent {
            BookHavenTheme {
                ProfileContent(
                    state = LibraryProfileState(
                        name = "Test Reader", libraryLoaded = true,
                        sharingError = "Connection unavailable"
                    ),
                    onSharing = {}, onSettings = { settingsOpens++ }, onNotifications = {},
                    onLogout = {}, onTab = {}, onRetryLibrary = {}, onRetrySharing = {}
                )
            }
        }
        composeRule.onNodeWithText("Your library is private").assertDoesNotExist()
        composeRule.onNodeWithText("Sharing settings unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, settingsOpens) }
    }

    @Test
    fun homeFinalRowIsFullyVisibleClearOfFabAndNavigation() {
        JourneyCapture.prepareWindow()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val width = JourneyCapture.width
        val fontScale = JourneyCapture.fontScale
        val darkTheme = JourneyCapture.darkTheme
        val deviceDensity = context.resources.displayMetrics.density
        val books = JourneyFixtures.books
        assertTrue("Final-row evidence requires a scrollable multi-row shelf.", books.size > 6)
        runBlocking {
            books.map { it.coverUrl }.filter(String::isNotBlank).distinct().forEach { url ->
                require(url.startsWith("android.resource://") ||
                    url.startsWith("file:///android_asset/"))
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(url).size(Size.ORIGINAL)
                        .allowHardware(false).build()
                )
                assertTrue("Could not preload fixture jacket $url", result is SuccessResult)
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                BookHavenTheme(darkTheme = darkTheme) {
                    Surface(Modifier.width(width.dp).fillMaxHeight()) {
                        HomeLibraryContent(
                            books = books, loans = JourneyFixtures.loans,
                            hasLoaded = true, error = null, loansLoaded = true, loansError = null,
                            unreadCount = JourneyFixtures.notifications.count { it.isUnread },
                            onBook = {}, onAdd = {}, onBorrow = {}, onNotifications = {},
                            onTab = {}, onRetry = {}, clock = JourneyFixtures.clock
                        )
                    }
                }
            }
        }

        val gridMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val grid = composeRule.onNode(gridMatcher)
        grid.performScrollToIndex(books.lastIndex)
        composeRule.waitForIdle()

        fun fullBounds(node: SemanticsNode): Rect {
            val position = node.positionInRoot
            return Rect(
                position.x, position.y,
                position.x + node.size.width, position.y + node.size.height
            )
        }

        val root = fullBounds(composeRule.onRoot().fetchSemanticsNode())
        assertEquals(
            "Parent must configure the requested physical viewport.",
            width * deviceDensity, root.width, 1f
        )
        val viewport = fullBounds(grid.fetchSemanticsNode())
        val fab = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and
                hasContentDescription("Add or borrow a book")
        ).assertIsDisplayed()
        val navigation = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
        ).assertIsDisplayed()
        val fabBounds = fullBounds(fab.fetchSemanticsNode())
        val navigationBounds = fullBounds(navigation.fetchSemanticsNode())
        val expectedColumns = if (width <= 339) 2 else 3
        val gutterPx = ((if (width <= 339) 16 else 20) * deviceDensity).roundToInt().toFloat()
        val gapPx = ((if (width <= 339) 14 else 11) * deviceDensity).roundToInt().toFloat()
        val expectedCardWidth =
            (viewport.width - 2 * gutterPx - (expectedColumns - 1) * gapPx) / expectedColumns
        val firstFinalRowIndex = books.lastIndex / expectedColumns * expectedColumns
        books.drop(firstFinalRowIndex).forEachIndexed { column, book ->
            val card = composeRule.onNode(
                hasContentDescription("${book.title}, by ${book.author}.", substring = true) and
                    SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and
                    hasAnyAncestor(gridMatcher)
            ).assertIsDisplayed()
            val bounds = fullBounds(card.fetchSemanticsNode())
            assertTrue("${book.title}: card must have a nonzero full size.", bounds.width > 0 && bounds.height > 0)
            assertEquals(
                "${book.title}: ${width}dp must use $expectedColumns correctly sized columns.",
                expectedCardWidth, bounds.width, 1f
            )
            assertEquals(
                "${book.title}: final-row column $column must preserve gutter and grid gap.",
                viewport.left + gutterPx + column * (expectedCardWidth + gapPx), bounds.left, 1f
            )
            assertTrue(
                "${book.title}: full card $bounds must fit inside scroll viewport $viewport.",
                bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f &&
                    bounds.top >= viewport.top - 1f && bounds.bottom <= viewport.bottom + 1f
            )
            assertTrue("${book.title}: full card overlaps FAB $fabBounds.", !bounds.overlaps(fabBounds))
            assertTrue(
                "${book.title}: full card reaches bottom navigation $navigationBounds.",
                bounds.bottom <= navigationBounds.top + 1f && !bounds.overlaps(navigationBounds)
            )
        }
        JourneyCapture.save(composeRule, "home-last-row")
    }
}
