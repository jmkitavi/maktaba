package com.maktaba.app.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.maktaba.app.data.*
import com.maktaba.app.journeys.JourneyFixtures
import com.maktaba.app.journeys.JourneyCapture
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.util.LoanTimeFormatter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneOffset

class HandoffJourneyCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun captureOriginalHandoffsAndAddBookCompanions() {
        JourneyCapture.prepareWindow()
        val fontScale = JourneyCapture.fontScale
        val width = JourneyCapture.width
        val dark = JourneyCapture.darkTheme
        val now = JourneyFixtures.clock.instant()
        val book = JourneyFixtures.books.first { it.title == "The Art of Quiet Mornings" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val jacket = runBlocking {
            context.imageLoader.execute(ImageRequest.Builder(context)
                .data(book.coverUrl).size(Size.ORIGINAL).build())
        }
        assertTrue("The exact local handoff jacket must be ready before capture.", jacket is SuccessResult)
        val due = Instant.ofEpochMilli(LoanTimeFormatter.localDateToEndOfDayMillis(
            defaultReturnPickerMillis(JourneyFixtures.clock), ZoneOffset.UTC))
        val invite = PendingLoanInvite("capture-invite", book.id,
            requireNotNull(LoanInviteCode.parse("DEMO-2048")), due, now.plusSeconds(86400))
        val review = ActiveLoan("capture-invite", book.id, "Alex Reader", LoanRole.BORROWER,
            LoanStatus.PENDING, due, null, null, null)
        val foundDraft = AddBookDraft(
            title = book.title, author = book.author, binding = "Paperback",
            coverUrl = book.coverUrl, source = "isbnsearch", originalFormat = book.format
        )
        val scenario = mutableStateOf("lend")

        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BookHavenTheme(darkTheme = dark) {
                    when (scenario.value) {
                        "lend" -> LendBookConfigContent(
                            LendBookConfigState(book, "Jordan", due), {}, {}, {}, {})
                        "share-code" -> ShareLendingCodeContent(
                            ShareLendingCodeState(book, invite, now, borrowerLabel = "Jordan"), {}, {}, {}, {}, {})
                        "scan-code" -> ScanEnterCodeContent(
                            ScanEnterCodeState("DEMO-2048"), {}, {}, {}, {})
                        "confirm-borrow", "borrow-success" -> ConfirmBorrowContent(
                            ConfirmBorrowState(book, review, accepted = scenario.value == "borrow-success", now = now),
                            {}, {}, {}, {})
                        else -> {
                            val step = when (scenario.value) {
                                "companion-44" -> AddBookStep.ENTRY
                                "companion-45", "companion-45-cancelled" -> AddBookStep.SCANNER
                                "companion-46" -> AddBookStep.ISBN
                                "companion-47", "isbn-found" -> AddBookStep.DETAILS
                                else -> AddBookStep.SUCCESS
                            }
                            AddBookContent(
                                AddBookState(
                                    step = step,
                                    isbn = if (scenario.value == "isbn-found") "9780000000002" else "",
                                    draft = when {
                                        scenario.value == "isbn-found" -> foundDraft
                                        step == AddBookStep.SUCCESS -> AddBookDraft(
                                            title = book.title, author = book.author, binding = "Paperback"
                                        )
                                        else -> AddBookDraft()
                                    },
                                    error = if (scenario.value == "companion-45-cancelled")
                                        "Scan cancelled. Try again, type the ISBN, or enter details by hand." else null
                                ), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
                            )
                        }
                    }
                }
            }
        }

        val assertions = listOf(
            "lend" to "Generate Lending Code",
            "share-code" to "YOUR LENDING CODE",
            "scan-code" to "A shared story awaits.",
            "confirm-borrow" to "Have the book in hand?",
            "borrow-success" to "Borrow confirmed",
            "companion-44" to "A new place on your shelf.",
            "companion-45" to "Scan the back-cover barcode.",
            "companion-46" to "Find this edition.",
            "companion-47" to "Title (required)",
            "companion-48" to "A new book at home.",
            "companion-45-cancelled" to "Scan cancelled. Try again, type the ISBN, or enter details by hand.",
            "isbn-found" to "Title (required)"
        )
        assertions.forEach { (reference, text) ->
            compose.runOnIdle { scenario.value = reference }
            compose.waitForIdle()
            compose.onNodeWithText(text).assertExists()
            compose.onNodeWithContentDescription("Back").assertIsDisplayed().assertHasClickAction()
            if (reference == "borrow-success") {
                compose.onNodeWithText("I Have the Book").assertDoesNotExist()
                compose.onNodeWithText("Back to Loans").assertIsEnabled()
            }
            if (reference == "companion-45" || reference == "companion-45-cancelled") {
                compose.onNodeWithText("Open barcode scanner").assertIsEnabled()
                compose.onNodeWithText("Type ISBN instead").assertIsEnabled()
            }
            if (reference == "companion-45") {
                compose.onNodeWithText("Scan cancelled. Try again, type the ISBN, or enter details by hand.")
                    .assertDoesNotExist()
            }
            if (reference == "isbn-found") {
                compose.onNodeWithText("From ISBNsearch · check this edition").assertExists()
                compose.onNodeWithContentDescription("Title (required)").assertTextContains(book.title)
                compose.onNodeWithContentDescription("Author (required)").assertTextContains(book.author)
                compose.onNodeWithText("Add to my library").assertIsEnabled()
            }
            val bounds = compose.onRoot().getUnclippedBoundsInRoot()
            val actual = (bounds.right - bounds.left).value
            assertTrue("Set the device's logical width to $width dp; root is $actual dp",
                kotlin.math.abs(actual - width) <= 1f)
            JourneyCapture.save(compose, reference)
        }
    }
}
