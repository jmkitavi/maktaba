package com.maktaba.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.journeys.JourneyFixtures
import com.maktaba.app.journeys.JourneyCapture
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.theme.BookHavenTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Native viewport captures; companion destinations reuse 24/26/12/25, not preview layouts. */
@OptIn(ExperimentalTestApi::class)
class LoanJourneyCaptureTest {
    @get:Rule val compose = createComposeRule()

    private enum class Page { ACTIVE, REMINDER, RETURN, INBOX, BOOK }

    private data class Scenario(
        val ref: String,
        val page: Page,
        val fixtureId: String = "distant-shores",
        val stage: ReturnStage = ReturnStage.REQUEST,
        val review: Boolean = false,
        val reminder: ReminderPresentation = ReminderPresentation(),
        val filter: InboxFilter = InboxFilter.ALL,
        val loaded: Boolean = true,
        val empty: Boolean = false,
        val allRead: Boolean = false,
        val error: String? = null,
        val actionError: String? = null,
        val opening: Boolean = false,
        val submitting: Boolean = false,
        val notificationType: String? = null,
        val expected: String
    )

    @Test
    fun captureOriginalLoansAndNotificationJourneys() {
        JourneyCapture.prepareWindow()
        val arguments = InstrumentationRegistry.getArguments()
        val theme = (arguments.getString("captureTheme") ?: "light").lowercase(Locale.ROOT)
        require(theme == "light" || theme == "dark") { "captureTheme must be light or dark." }
        val fontScale = arguments.getString("captureFontScale")?.toFloat() ?: 1f
        require(fontScale.isFinite() && fontScale > 0f)
        val requestedWidth = arguments.getString("captureWidth")?.toInt()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousLocale = Locale.getDefault()
        val previousZone = TimeZone.getDefault()
        Locale.setDefault(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val fixtures = JourneyFixtures.books.map { original ->
                original.copy(coverUrl = JourneyFixtures.coverUrl(original.catalogId))
            }
            val catalogue = JourneyFixtures.catalogue.map { original ->
                original.copy(coverUrl = JourneyFixtures.coverUrl(original.catalogId))
            }
            listOf("distant-shores", "small-things", "stillwater").forEach { id ->
                val book = fixtures.book(id)
                require(book.coverUrl.startsWith("android.resource://")) {
                    "Capture jackets must be original local debug resources: $id"
                }
                runBlocking {
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context).data(book.coverUrl).build()
                    )
                    assertTrue("Original jacket did not decode: $id", result is SuccessResult)
                }
            }
            assertEquals("Letters from Distant Shores", fixtures.book("distant-shores").title)
            assertEquals("A Field Guide to Small Things", fixtures.book("small-things").title)
            assertEquals("Stillwater & Other Stories", fixtures.book("stillwater").title)
            assertEquals(Instant.parse("2026-10-23T12:00:00Z"), JourneyFixtures.clock.instant())
            assertEquals(ZoneOffset.UTC.rules, JourneyFixtures.clock.zone.rules)
            verifyOriginalLoan(fixtures.book("distant-shores"), "Sam", true, "2026-09-28", "2026-10-19")
            verifyOriginalLoan(fixtures.book("small-things"), "Alex", false, "2026-10-04", "2026-10-25")
            val scenarios = scenarios()
            var scenario by mutableStateOf(scenarios.first())
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    BookHavenTheme(darkTheme = theme == "dark") {
                        key(scenario.ref) {
                            RenderScenario(scenario, fixtures, catalogue)
                        }
                    }
                }
            }
            scenarios.forEach { next ->
                verifyNotificationRoute(next, fixtures)
                compose.runOnIdle { scenario = next }
                compose.waitForIdle()
                if (next.page == Page.INBOX && !next.loaded && next.error == null) {
                    compose.onNodeWithContentDescription("Loading your notifications").assertIsDisplayed()
                } else {
                    compose.onAllNodesWithText(next.expected).onFirst().assertIsDisplayed()
                }
                if (next.page == Page.RETURN) {
                    compose.onNodeWithContentDescription("Back").assertIsDisplayed().assertHasClickAction()
                }
                if (next.stage == ReturnStage.WAITING && next.page == Page.RETURN) {
                    compose.onNodeWithText("Waiting for Confirmation").assertIsNotEnabled()
                }
                if (next.reminder.nextAllowedAt != null) {
                    compose.onNodeWithText("Try Sending Again").assertIsNotEnabled()
                }
                val image = compose.onRoot().captureToImage()
                requestedWidth?.let { width ->
                    assertEquals(
                        "captureWidth must be set on the emulator; do not resize captured output.",
                        width.toFloat(), image.width / context.resources.displayMetrics.density, 1f
                    )
                }
                val destination = File(context.filesDir, "journey-captures/${next.ref}-$theme.png")
                assertTrue(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
                destination.outputStream().use { output ->
                    assertTrue(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                assertTrue("Capture not persisted: ${next.ref}", destination.length() > 0L)
                JourneyCapture.recordGeometry(
                    compose, next.ref, theme, requestedWidth ?: JourneyCapture.width, fontScale
                )
            }
        } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousZone)
        }
    }

    @Composable
    private fun RenderScenario(scenario: Scenario, books: List<Book>, catalogue: List<Book>) {
        val now = JourneyFixtures.clock.instant()
        val book = books.book(scenario.fixtureId)
        when (scenario.page) {
            Page.ACTIVE -> ActiveLoanContent(
                book = book, loan = fixtureLoan(book), stage = scenario.stage,
                onBack = {}, onReminder = {}, onReturn = {}, onBook = {}, onReader = {}, now = now
            )
            Page.REMINDER -> ReturnReminderContent(
                book = book, loan = fixtureLoan(book), review = scenario.review,
                state = scenario.reminder, now = now, onBack = {}, onOpenLoan = {}, onSend = {}
            )
            Page.RETURN -> ReturnConfirmationContent(
                book = book, loan = fixtureLoan(book).copy(
                    status = when (scenario.stage) {
                        ReturnStage.COMPLETE -> LoanStatus.RETURNED
                        ReturnStage.WAITING, ReturnStage.CONFIRM -> LoanStatus.RETURN_REQUESTED
                        else -> LoanStatus.ACTIVE
                    }
                ),
                stage = scenario.stage, loading = scenario.submitting, error = scenario.error,
                onBack = {}, onSubmit = {}, onOpenLoan = {}, onLoans = {}
            )
            Page.INBOX -> NotificationsContent(
                items = if (scenario.empty) emptyList() else JourneyFixtures.notifications.map {
                    if (scenario.allRead) it.copy(isUnread = false) else it
                },
                loaded = scenario.loaded, error = scenario.error, filter = scenario.filter,
                openingId = if (scenario.opening) JourneyFixtures.notifications.first().id else null,
                actionError = scenario.actionError, now = now,
                onBack = {}, onFilter = {}, onRetry = {}, onOpen = {}
            )
            Page.BOOK -> {
                val notification = JourneyFixtures.notifications.first { it.type == "return_confirmed" }
                val returnedCopies = books.map {
                    if (it.catalogId == book.catalogId) it.copy(status = BookStatus.OWNED) else it
                }
                BookDetailContent(
                    state = LibraryBookDetailState(
                        selection = selectLibraryDetail(
                            notification.catalogBookId.ifBlank { notification.bookId },
                            catalogueOnly = false, copies = returnedCopies, catalogue = catalogue
                        ),
                        loaded = true, savedLoaded = true, hidden = false
                    ),
                    onBack = {}, onRetry = {}, onSave = {}, onLend = {}, onLoan = {}, onRemove = {},
                    onAddToCollection = {}, onToggleHidden = {}, onManageSharing = {},
                    clock = JourneyFixtures.clock
                )
            }
        }
    }

    private fun verifyNotificationRoute(scenario: Scenario, books: List<Book>) {
        val type = scenario.notificationType ?: return
        val book = books.book(scenario.fixtureId)
        val notification = JourneyFixtures.notifications.first {
            it.type == type && (it.bookId == book.id || it.catalogBookId == book.catalogId)
        }
        val expected = when (scenario.page) {
            Page.ACTIVE -> Routes.ActiveLoan.createRoute(notification.bookId)
            Page.RETURN -> Routes.ReturnConfirmation.createRoute(notification.bookId)
            Page.BOOK -> Routes.BookDetail.createRoute(notification.catalogBookId.ifBlank { notification.bookId })
            Page.REMINDER -> Routes.ReturnReminder.createRoute(notification.bookId)
            else -> error("Inbox is not a notification destination.")
        }
        assertEquals("Notification route changed: $type", expected, notificationRoute(notification))
    }

    private fun fixtureLoan(book: Book): ActiveLoan =
        JourneyFixtures.loans.single { it.bookId == book.id }

    private fun verifyOriginalLoan(book: Book, reader: String, lender: Boolean, accepted: String, due: String) {
        val loan = fixtureLoan(book)
        assertEquals(reader, loan.counterpartyName)
        assertEquals(lender, loan.isLender)
        assertEquals(LocalDate.parse(accepted), loan.acceptedAt?.atZone(ZoneOffset.UTC)?.toLocalDate())
        assertEquals(LocalDate.parse(due), loan.dueAt?.atZone(ZoneOffset.UTC)?.toLocalDate())
    }

    private fun List<Book>.book(id: String): Book = single { it.id == id || it.catalogId == id }

    /**
     * Companion 50 is the real borrower Active Loan (24), not its local-preview summary.
     * Companion 51 is the real other-reader Return confirmation (26), not an "open design" link.
     * Companion 52 is the existing Book First detail (12), resolving the returned title.
     * Companion 53 is the real overdue received Reminder (25), not the gallery's routing explanation.
     * Their prototype pills, harness notices and "Open approved ..." buttons are deliberately absent;
     * compare these captures against the corresponding original designs, not preview pixel metrics.
     */
    private fun scenarios(): List<Scenario> = listOf(
        Scenario("active-lender", Page.ACTIVE, expected = "Letters from Distant Shores"),
        Scenario("reminder-send", Page.REMINDER, review = true, expected = "A Little Nudge for Sam"),
        Scenario("reminder-sent", Page.REMINDER, review = true,
            reminder = ReminderPresentation(sent = true), expected = "A Little Nudge, Sent"),
        Scenario("return-request", Page.RETURN, expected = "Back With Its Owner?"),
        Scenario("return-waiting", Page.RETURN, stage = ReturnStage.WAITING, expected = "One Last Confirmation"),
        Scenario("return-done", Page.RETURN, stage = ReturnStage.COMPLETE, expected = "Back Where It Belongs"),
        Scenario("reminder-borrower", Page.REMINDER, fixtureId = "small-things", expected = "A Gentle Reminder"),
        Scenario("active-borrower", Page.ACTIVE, fixtureId = "small-things", expected = "A Field Guide to Small Things"),
        Scenario("return-confirm-borrower", Page.RETURN, fixtureId = "small-things",
            stage = ReturnStage.CONFIRM, expected = "Confirm the Return"),
        Scenario("return-done-borrower", Page.RETURN, fixtureId = "small-things",
            stage = ReturnStage.COMPLETE, expected = "Back Where It Belongs"),
        Scenario("companion-49", Page.INBOX, expected = "3 unread updates"),
        Scenario("companion-50", Page.ACTIVE, fixtureId = "small-things",
            notificationType = "loan_reminder", expected = "A Field Guide to Small Things"),
        Scenario("companion-51", Page.RETURN, fixtureId = "small-things", stage = ReturnStage.CONFIRM,
            notificationType = "return_requested", expected = "Confirm the Return"),
        Scenario("companion-52", Page.BOOK, fixtureId = "stillwater",
            notificationType = "return_confirmed", expected = "Stillwater & Other Stories"),
        Scenario("companion-53", Page.REMINDER, notificationType = "lender_overdue", expected = "This One Is Overdue"),
        Scenario("companion-49-unread", Page.INBOX, filter = InboxFilter.UNREAD, expected = "A gentle reminder"),
        Scenario("companion-49-loading", Page.INBOX, loaded = false, empty = true, expected = ""),
        Scenario("companion-49-empty", Page.INBOX, empty = true, expected = "You Are All Caught Up"),
        Scenario("companion-49-no-unread", Page.INBOX, filter = InboxFilter.UNREAD, allRead = true,
            expected = "No unread notifications"),
        Scenario("companion-49-error", Page.INBOX, loaded = false, error = "Connection unavailable",
            expected = "Could Not Load Notifications"),
        Scenario("companion-49-unavailable", Page.INBOX,
            actionError = "This notification's destination is no longer available.",
            expected = "This notification's destination is no longer available."),
        Scenario("companion-49-opening", Page.INBOX, opening = true, expected = "Opening…"),
        Scenario("companion-49-read-error", Page.INBOX,
            actionError = "We could not open this notification. Tap it to try again.",
            expected = "We could not open this notification. Tap it to try again."),
        Scenario("reminder-error", Page.REMINDER, review = true,
            reminder = ReminderPresentation(error = "We could not send the reminder. Try again."),
            expected = "A Little Nudge for Sam"),
        Scenario("reminder-cooldown", Page.REMINDER, review = true,
            reminder = ReminderPresentation(error = "A reminder was sent recently.",
                nextAllowedAt = JourneyFixtures.clock.instant().plusSeconds(3600)), expected = "A Little Nudge for Sam"),
        Scenario("reminder-sending", Page.REMINDER, review = true,
            reminder = ReminderPresentation(sending = true), expected = "A Little Nudge for Sam"),
        Scenario("return-request-error", Page.RETURN, error = "We could not update this return. Try again.",
            expected = "Back With Its Owner?"),
        Scenario("return-confirm-error", Page.RETURN, fixtureId = "small-things", stage = ReturnStage.CONFIRM,
            error = "We could not update this return. Try again.", expected = "Confirm the Return"),
        Scenario("return-confirm-submitting", Page.RETURN, fixtureId = "small-things", stage = ReturnStage.CONFIRM,
            submitting = true, expected = "Confirm the Return")
    )
}
