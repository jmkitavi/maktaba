package com.maktaba.app.ui.screens

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.maktaba.app.data.BookStatus
import com.maktaba.app.journeys.JourneyCapture
import com.maktaba.app.journeys.JourneyFixtures
import com.maktaba.app.ui.theme.BookHavenTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalTestApi::class)
class CoreJourneyCaptureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private data class Scenario(val reference: String, val dark: Boolean)

    @Test
    fun captureCanonicalCoreJourneys() {
        JourneyCapture.prepareWindow()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val width = arguments.getString("captureWidth")?.toInt() ?: 360
        require(width in setOf(320, 360, 390, 430)) { "captureWidth must be 320, 360, 390 or 430 dp." }
        val fontScale = arguments.getString("captureFontScale")?.toFloat() ?: 1f
        require(fontScale.isFinite() && fontScale > 0f) { "captureFontScale must be positive and finite." }
        val themes = when (arguments.getString("captureTheme")?.lowercase() ?: "both") {
            "light" -> listOf(false)
            "dark" -> listOf(true)
            "both" -> listOf(false, true)
            else -> error("captureTheme must be light, dark or both.")
        }
        assertEquals(Instant.parse("2026-10-25T12:00:00Z"), JourneyFixtures.clock.instant())
        assertEquals(ZoneOffset.UTC, JourneyFixtures.clock.zone.normalized())
        assertEquals(8, JourneyFixtures.books.size)
        assertEquals(2, visibleLibraryLoans(JourneyFixtures.loans, lender = true).size)
        assertEquals(2, visibleLibraryLoans(JourneyFixtures.loans, lender = false).size)

        // Resolve the local debug artwork before rendering, so a loading fallback cannot
        // accidentally become the canonical jacket in a screenshot.
        runBlocking {
            (JourneyFixtures.books + JourneyFixtures.catalogue).map { it.coverUrl }
                .filter(String::isNotBlank).distinct().forEach { url ->
                    require(url.startsWith("android.resource://") ||
                        url.startsWith("file:///android_asset/")) {
                        "Capture fixtures must use local debug artwork, not network covers: $url"
                    }
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context).data(url).size(Size.ORIGINAL)
                            .allowHardware(false).build()
                    )
                    assertTrue("Could not load fixture jacket $url", result is SuccessResult)
                }
        }

        val references = listOf(
            "home", "detail-owned", "detail-saved", "detail-catalogue",
            "loans-lent", "loans-borrowed", "profile", "companion-67"
        )
        val scenario = mutableStateOf(Scenario(references.first(), themes.first()))
        val deviceDensity = context.resources.displayMetrics.density
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                BookHavenTheme(darkTheme = scenario.value.dark) {
                    Surface(Modifier.width(width.dp).fillMaxHeight()) {
                        key(scenario.value) {
                            CoreReference(scenario.value.reference)
                        }
                    }
                }
            }
        }

        val destination = File(context.filesDir, "journey-captures")
        check(destination.isDirectory || destination.mkdirs()) { "Could not create $destination" }
        themes.forEach { dark ->
            references.forEach { reference ->
                composeRule.runOnIdle { scenario.value = Scenario(reference, dark) }
                composeRule.waitForIdle()
                assertReference(reference)
                val image = composeRule.onRoot().captureToImage()
                val expectedWidth = (width * deviceDensity).roundToInt()
                assertTrue(
                    "Parent must configure a ${width}dp physical viewport; captured ${image.width}px, expected ${expectedWidth}px.",
                    abs(image.width - expectedWidth) <= 1
                )
                val theme = if (dark) "dark" else "light"
                val file = File(destination, "$reference-$theme-${width}dp-font$fontScale.png")
                file.outputStream().use { stream ->
                    check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                assertTrue("Screenshot was not persisted: $file", file.length() > 0)
                JourneyCapture.recordGeometry(composeRule, reference, theme, width, fontScale)
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("journeyCapture", file.absolutePath)
                })
            }
        }
    }

    @Composable
    private fun CoreReference(reference: String) {
        when (reference) {
            "home" -> HomeLibraryContent(
                books = JourneyFixtures.books, loans = JourneyFixtures.loans,
                hasLoaded = true, error = null, loansLoaded = true, loansError = null,
                unreadCount = JourneyFixtures.notifications.count { it.isUnread },
                onBook = {}, onAdd = {}, onBorrow = {}, onNotifications = {}, onTab = {},
                onRetry = {}, clock = JourneyFixtures.clock
            )
            "detail-owned", "detail-saved", "detail-catalogue", "companion-67" -> {
                val catalogueOnly = reference != "detail-owned" && reference != "companion-67"
                val catalogId = if (reference == "detail-saved") "sunday" else "quiet-mornings"
                val requestedId = if (catalogueOnly) catalogId else {
                    JourneyFixtures.books.single { it.catalogId == catalogId }.id
                }
                val canonicalSelection = requireNotNull(selectLibraryDetail(
                    requestedId, catalogueOnly, JourneyFixtures.books, JourneyFixtures.catalogue
                ))
                val selection = if (reference == "companion-67") {
                    canonicalSelection.copy(
                        book = canonicalSelection.book.copy(
                            title = "${canonicalSelection.book.title} · Demo edition",
                            binding = "Hardcover"
                        )
                    )
                } else canonicalSelection
                BookDetailContent(
                    state = LibraryBookDetailState(
                        selection = selection, loaded = true,
                        saved = reference == "detail-saved", savedLoaded = true,
                        allowVisibilityManagement = !catalogueOnly,
                        hidden = false.takeIf { !catalogueOnly }
                    ),
                    onBack = {}, onRetry = {}, onSave = {}, onLend = {}, onLoan = {},
                    onRemove = {}, onAddToCollection = {}, onToggleHidden = {},
                    onManageSharing = {}, clock = JourneyFixtures.clock,
                    onEditBook = ({}).takeIf { !catalogueOnly }
                )
            }
            "loans-lent", "loans-borrowed" -> LoansContent(
                books = JourneyFixtures.books, loans = JourneyFixtures.loans,
                loaded = true, error = null, onLoan = {}, onChooseBook = {}, onBorrow = {},
                onTab = {}, onRetry = {}, initiallyLender = reference == "loans-lent",
                clock = JourneyFixtures.clock
            )
            "profile" -> ProfileContent(
                state = LibraryProfileState(
                    name = "Alex Reader",
                    shelfCount = JourneyFixtures.books.count { it.status == BookStatus.OWNED },
                    lentCount = JourneyFixtures.books.count { it.status == BookStatus.LENT_OUT },
                    borrowedCount = JourneyFixtures.books.count { it.status == BookStatus.BORROWED },
                    libraryLoaded = true, sharingLoaded = true
                ),
                onSharing = {}, onSettings = {}, onNotifications = {}, onLogout = {},
                onTab = {}, onRetryLibrary = {}, onRetrySharing = {}
            )
            else -> error("Unknown core reference $reference")
        }
    }

    private fun assertReference(reference: String) {
        when (reference) {
            "home" -> {
                composeRule.onNodeWithText("My Library").assertIsDisplayed()
                composeRule.onNodeWithText("THE COLLECTION").assertIsDisplayed()
                composeRule.onNodeWithContentDescription("Search books, authors, genres").assertIsDisplayed()
            }
            "detail-owned", "detail-saved", "detail-catalogue", "companion-67" -> {
                val catalogId = if (reference == "detail-saved") "sunday" else "quiet-mornings"
                val title = JourneyFixtures.catalogue.single { it.catalogId == catalogId }.title
                composeRule.onNodeWithText(
                    if (reference == "companion-67") "$title · Demo edition" else title
                ).assertIsDisplayed()
                val coverWidth = minOf((JourneyCapture.width - 40) / 2f, 200f).dp
                composeRule.onNodeWithTag("cover:$catalogId", useUnmergedTree = true)
                    .assertWidthIsEqualTo(coverWidth).assertHeightIsEqualTo(coverWidth * 1.5f)
                composeRule.onNodeWithText("BOOK HAVEN").assertDoesNotExist()
                composeRule.onNodeWithContentDescription("Edit book").assertDoesNotExist()
                composeRule.onNodeWithText("Edit book details").assertDoesNotExist()
                if (reference == "detail-owned" || reference == "companion-67") {
                    composeRule.onNodeWithText("Lend This Book").assertIsDisplayed()
                    composeRule.onNodeWithText("Catalogue Title").assertDoesNotExist()
                } else {
                    composeRule.onNodeWithText("Catalogue Title").assertIsDisplayed()
                    composeRule.onNodeWithText("Lend This Book").assertDoesNotExist()
                    composeRule.onNodeWithText("View This Loan").assertDoesNotExist()
                }
                composeRule.onNodeWithContentDescription(
                    if (reference == "detail-saved") "Remove from Want to Read" else "Save to Want to Read"
                ).assertIsDisplayed()
            }
            "loans-lent", "loans-borrowed" -> {
                composeRule.onNodeWithText("Closest return dates first").assertIsDisplayed()
                composeRule.onNodeWithText(
                    if (reference == "loans-lent") "With Sam" else "From Kit"
                ).assertIsDisplayed()
                composeRule.onNodeWithContentDescription("Notifications").assertDoesNotExist()
                if (reference == "loans-lent" && JourneyCapture.width >= 360 &&
                    JourneyCapture.fontScale == 1f
                ) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    composeRule.onNodeWithText(
                        "Letters from Distant Shores", useUnmergedTree = true
                    ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals("The canonical loan title should fit one line.", 1, layouts.single().lineCount)
                }
            }
            "profile" -> {
                composeRule.onNodeWithText("BOOK HAVEN").assertIsDisplayed()
                composeRule.onNodeWithText("Alex Reader").assertIsDisplayed()
                composeRule.onNodeWithText("Your library is private").assertExists()
                composeRule.onNodeWithText("Settings").assertExists()
                composeRule.onNodeWithContentDescription("Notifications").assertDoesNotExist()
            }
        }
    }
}
