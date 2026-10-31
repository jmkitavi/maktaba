package com.maktaba.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LoanRole
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.data.NotificationItem
import com.maktaba.app.journeys.JourneyFixtures
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.theme.BookHavenTheme
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoanJourneyPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-10-23T12:00:00Z")
    private val book = Book(
        id = "copy", title = "A Test Reader's Book", author = "Test Author",
        coverRes = 0, status = BookStatus.LENT_OUT, format = BookFormat.PHYSICAL
    )
    private val loan = ActiveLoan(
        id = "loan", bookId = book.id, counterpartyName = "Sam",
        role = LoanRole.LENDER, status = LoanStatus.ACTIVE,
        dueAt = now.minusSeconds(86400), acceptedAt = now.minusSeconds(86400 * 14),
        returnRequestedAt = null, returnedAt = null
    )

    @Test
    fun lenderReminderActionOpensReviewRatherThanSubmitting() {
        var reviewCalls = 0
        compose.setContent {
            BookHavenTheme {
                ActiveLoanContent(book, loan, ReturnStage.REQUEST, {}, { reviewCalls++ }, {}, {}, now = now)
            }
        }
        assertEquals(0, reviewCalls)
        compose.onNodeWithText("Lent out").assertIsDisplayed()
        compose.onNodeWithText("Send a Reminder").performScrollTo().performClick()
        assertEquals(1, reviewCalls)
    }

    @Test
    fun borrowerHasNoSendReminderAction() {
        var returnCalls = 0
        compose.setContent {
            BookHavenTheme {
                ActiveLoanContent(book.copy(status = BookStatus.BORROWED),
                    loan.copy(role = LoanRole.BORROWER), ReturnStage.REQUEST, {}, {}, { returnCalls++ }, {}, now = now)
            }
        }
        compose.onNodeWithText("Send a Reminder").assertDoesNotExist()
        compose.onNodeWithText("Borrowed").assertIsDisplayed()
        compose.onNodeWithText("Start a Return").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, returnCalls)
    }

    @Test
    fun compactActiveLoanShowsBothTouchSizedActionsAndCenteredNaturalCover() {
        val fixture = JourneyFixtures.books.single { it.id == "distant-shores" }
        val fixtureLoan = JourneyFixtures.loans.single { it.bookId == fixture.id }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drawable = runBlocking {
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(fixture.coverUrl).build())
            assertTrue("The local fixture cover must decode", result is SuccessResult)
            (result as SuccessResult).drawable
        }
        activeViewport {
            ActiveLoanContent(fixture, fixtureLoan, ReturnStage.REQUEST, {}, {}, {}, {},
                now = JourneyFixtures.clock.instant())
        }
        val viewport = compose.onNodeWithTag("active_test_viewport").fetchSemanticsNode()
        val pixelsPerDp = viewport.size.width / 360f
        assertEquals(781f, viewport.size.height / pixelsPerDp, 1f)
        compose.waitUntil(5_000) {
            val cover = compose.onNodeWithTag("active_loan_cover", useUnmergedTree = true).fetchSemanticsNode()
            kotlin.math.abs(cover.size.width.toFloat() / cover.size.height -
                drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight) < 0.02f
        }
        for (label in listOf("Send a Reminder", "Start a Return")) {
            val action = compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
            val node = action.fetchSemanticsNode()
            assertTrue("$label needs a 48dp touch target", node.size.height / pixelsPerDp >= 47.5f)
            assertEquals("$label must be entirely visible initially",
                node.size.height.toFloat(), node.boundsInRoot.height, 1f)
        }
        val cover = compose.onNodeWithTag("active_loan_cover", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Cover must keep its original jacket proportion",
            drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight,
            cover.size.width.toFloat() / cover.size.height, 0.02f)
        assertEquals("The jacket, not a side-by-side summary card, is centered",
            viewport.positionInRoot.x + viewport.size.width / 2f,
            cover.positionInRoot.x + cover.size.width / 2f, 1f)
        assertBottomVignetteSpansViewport()
    }

    @Test
    fun smallLargeFontActiveLoanMovesEverySectionInOneScrollWithoutPinnedActions() {
        val fixture = JourneyFixtures.books.single { it.id == "distant-shores" }
        val fixtureLoan = JourneyFixtures.loans.single { it.bookId == fixture.id }
        activeViewport(width = 320, height = 560, fontScale = 1.3f) {
            ActiveLoanContent(fixture, fixtureLoan, ReturnStage.REQUEST, {}, {}, {}, {},
                now = JourneyFixtures.clock.instant())
        }
        val scroll = compose.onNodeWithTag("active_loan_scroll", useUnmergedTree = true)
        val scrollNode = scroll.fetchSemanticsNode()
        val tags = listOf("header", "cover", "due", "actions", "art").map { "active_loan_$it" }
        val before = tags.associateWith { tag ->
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("$tag must belong to the same scroll container",
                generateSequence(node.parent) { it.parent }.any { it.id == scrollNode.id })
            node.positionInRoot.y
        }
        val range = scrollNode.config[SemanticsProperties.VerticalScrollAxisRange]
        val initialScroll = range.value()
        assertTrue("Small viewport/large font must really overflow", range.maxValue() > 0f)
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, scrollNode.size.height / 3f) }
        compose.waitForIdle()
        val distance = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() -
            initialScroll
        assertTrue("The single scroll must actually move content", distance > 1f)
        for (tag in tags) {
            val after = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
            assertEquals("$tag must move with the header, never stay pinned",
                distance, before.getValue(tag) - after, 1.5f)
        }
        for (label in listOf("Send a Reminder", "Start a Return")) {
            compose.onNode(hasText(label) and hasClickAction()).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("active_loan_art", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("active_loan_header", useUnmergedTree = true).assertIsNotDisplayed()
        assertBottomVignetteSpansViewport()
    }

    @Test
    fun activeLoanNavigationAndReturnStagesKeepTheirRealCallbacks() {
        val stage = mutableStateOf(ReturnStage.REQUEST)
        var backCalls = 0
        var bookCalls = 0
        var readerCalls = 0
        var returnCalls = 0
        var reminderCalls = 0
        activeViewport {
            ActiveLoanContent(book, loan, stage.value, { backCalls++ }, { reminderCalls++ },
                { returnCalls++ }, { bookCalls++ }, { readerCalls++ }, now)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Sam").performClick()
        compose.onNodeWithText("View the Book").performScrollTo().performClick()
        compose.onNodeWithText("Start a Return").performScrollTo().performClick()
        assertEquals(1, backCalls)
        assertEquals(1, bookCalls)
        assertEquals(1, readerCalls)
        assertEquals(1, returnCalls)
        assertEquals(0, reminderCalls)

        compose.runOnIdle { stage.value = ReturnStage.CONFIRM }
        compose.onNodeWithText("Send a Reminder").assertDoesNotExist()
        compose.onNodeWithText("Start a Return").assertDoesNotExist()
        compose.onNodeWithText("Review the Return").performScrollTo().assertIsEnabled().performClick()
        assertEquals(2, returnCalls)

        compose.runOnIdle { stage.value = ReturnStage.WAITING }
        compose.onNodeWithText("Review the Return").assertDoesNotExist()
        compose.onNodeWithText("Waiting for Confirmation").performScrollTo().assertIsNotEnabled()
        assertEquals(2, returnCalls)
    }

    @Test
    fun activeLoanUrgencyAndProgressFollowCalendarDatesIncludingUnknownDates() {
        val previousLocale = Locale.getDefault()
        val previousZone = TimeZone.getDefault()
        Locale.setDefault(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val calendarNow = Instant.parse("2026-10-23T23:30:00Z")
            val current = mutableStateOf(loan.copy(
                acceptedAt = Instant.parse("2026-10-21T18:00:00Z"),
                dueAt = Instant.parse("2026-10-25T00:01:00Z")
            ))
            activeViewport {
                ActiveLoanContent(book, current.value, ReturnStage.REQUEST, {}, {}, {}, {}, now = calendarNow)
            }
            fun assertCalendar(summary: String, fraction: Float?) {
                compose.onNodeWithText(summary).performScrollTo().assertIsDisplayed()
                val progressNode = compose.onNodeWithTag("active_loan_progress", useUnmergedTree = true)
                if (fraction == null) {
                    progressNode.assertDoesNotExist()
                } else {
                    val progress = progressNode.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
                    assertEquals("Elapsed progress must follow the real loan interval", fraction, progress.current, 0.001f)
                }
            }
            assertCalendar("days left", 0.6857509f)
            compose.onNodeWithText("02").assertIsDisplayed()
            compose.onNodeWithText("Agreed return · 25 Oct 2026").assertIsDisplayed()
            compose.runOnIdle { current.value = current.value.copy(dueAt = Instant.parse("2026-10-23T00:01:00Z")) }
            assertCalendar("Due today", 1f)
            compose.onNodeWithText("days left").assertDoesNotExist()
            compose.runOnIdle { current.value = current.value.copy(dueAt = Instant.parse("2026-10-22T23:59:00Z")) }
            assertCalendar("day overdue", 1f)
            compose.onNodeWithText("01").assertIsDisplayed()
            compose.runOnIdle { current.value = current.value.copy(dueAt = null) }
            assertCalendar("Due date unavailable", null)
            compose.onNodeWithText("Due today").assertDoesNotExist()
            compose.onNodeWithText("day overdue").assertDoesNotExist()
            compose.runOnIdle {
                current.value = current.value.copy(
                    dueAt = Instant.parse("2026-10-24T00:01:00Z"), acceptedAt = null
                )
            }
            assertCalendar("day left", null)
            compose.onNodeWithText("01").assertIsDisplayed()
        } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousZone)
        }
    }

    private fun activeViewport(
        width: Int = 360,
        height: Int = 781,
        fontScale: Float = 1f,
        content: @Composable () -> Unit
    ) {
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val nativeDensity = LocalDensity.current.density
                val fit = minOf(maxWidth.value / width, maxHeight.value / height, 1f)
                CompositionLocalProvider(LocalDensity provides Density(nativeDensity * fit, fontScale)) {
                    BookHavenTheme {
                        Box(Modifier.requiredSize(width.dp, height.dp).testTag("active_test_viewport")) {
                            content()
                        }
                    }
                }
            }
        }
    }

    private fun assertBottomVignetteSpansViewport() {
        val viewport = compose.onNodeWithTag("active_test_viewport").fetchSemanticsNode()
        val art = compose.onNodeWithTag("active_loan_art", useUnmergedTree = true).fetchSemanticsNode()
        val actions = compose.onNodeWithTag("active_loan_actions", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Bottom vignette must reach the left edge", viewport.positionInRoot.x, art.positionInRoot.x, 1f)
        assertEquals("Bottom vignette must use the whole viewport width", viewport.size.width, art.size.width)
        assertTrue("The vignette follows actions rather than living in a pinned footer",
            art.positionInRoot.y >= actions.positionInRoot.y + actions.size.height - 1f)
    }

    @Test
    fun returnRequestWaitsWithoutOfferingInitiatorASecondConfirmation() {
        compose.setContent {
            BookHavenTheme {
                ReturnConfirmationContent(book, loan, ReturnStage.WAITING, false, null, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Waiting for Confirmation").assertIsNotEnabled()
        compose.onNodeWithText("Confirm the Return").assertDoesNotExist()
        compose.onNodeWithText("One Last Confirmation").assertIsDisplayed()
    }

    @Test
    fun returnSuccessRemainsVisibleUntilExplicitNavigation() {
        var navigationCalls = 0
        compose.setContent {
            BookHavenTheme(darkTheme = true) {
                ReturnConfirmationContent(
                    book, loan.copy(role = LoanRole.BORROWER), ReturnStage.COMPLETE,
                    false, null, {}, {}, {}, { navigationCalls++ }
                )
            }
        }
        assertEquals(0, navigationCalls)
        compose.onNodeWithText("Back Where It Belongs").assertIsDisplayed()
        compose.onAllNodesWithText("Confirmed").assertCountEquals(2)
        compose.onNodeWithText("Back to Loans").performClick()
        assertEquals(1, navigationCalls)
    }

    @Test
    fun sentReminderShowsFeedbackInsteadOfAnotherSendButton() {
        compose.setContent {
            BookHavenTheme {
                ReturnReminderContent(book, loan, true, ReminderPresentation(sent = true),
                    now, {}, {}, {})
            }
        }
        compose.onNodeWithText("A Little Nudge, Sent").assertIsDisplayed()
        compose.onNode(hasText("Send Reminder") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithText("Open This Loan").assertIsDisplayed()
    }

    @Test
    fun reminderCooldownDisablesRetryButKeepsFailureFeedback() {
        compose.setContent {
            BookHavenTheme {
                ReturnReminderContent(book, loan, true,
                    ReminderPresentation(error = "A reminder was sent recently.", nextAllowedAt = now.plusSeconds(60)),
                    now, {}, {}, {})
            }
        }
        compose.onNodeWithText("Try Sending Again").assertIsNotEnabled()
    }

    @Test
    fun unreadFilterHidesReadNotificationsWithoutChangingTheirReadState() {
        val read = NotificationItem("read", "copy", title = "Read update", message = "Earlier", isUnread = false)
        val unread = NotificationItem("unread", "copy", title = "Unread update", message = "New")
        compose.setContent {
            var filter by remember { mutableStateOf(InboxFilter.ALL) }
            BookHavenTheme {
                NotificationsContent(listOf(read, unread), true, null, filter, null, null, now,
                    {}, { filter = it }, {}, {})
            }
        }
        compose.onNodeWithText("Read update").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show unread notifications").performClick()
        compose.onNodeWithText("Read update").assertDoesNotExist()
        compose.onNodeWithText("Unread update").assertIsDisplayed()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Read update").assertIsDisplayed()
    }

    @Test
    fun notificationListenerFailureOffersRealRetryInsteadOfEmptyInbox() {
        var retries = 0
        compose.setContent {
            BookHavenTheme {
                NotificationsContent(emptyList(), false, "Connection unavailable", InboxFilter.ALL,
                    null, null, now, {}, {}, { retries++ }, {})
            }
        }
        compose.onNodeWithText("A little quiet here.").assertDoesNotExist()
        compose.onNodeWithText("Couldn’t load this screen.").assertIsDisplayed()
        compose.onNodeWithText("Connection unavailable").assertIsDisplayed()
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun inboxSkeletonOnlyAppearsWhileLoadingAndYieldsToFailureOrResults() {
        val item = NotificationItem("update", "copy", title = "Return confirmed", message = "Your book is home.")
        val items = mutableStateOf(listOf(item))
        val loaded = mutableStateOf(false)
        val error = mutableStateOf<String?>(null)
        var opens = 0
        compose.setContent {
            BookHavenTheme {
                NotificationsContent(items.value, loaded.value, error.value, InboxFilter.ALL,
                    null, null, now, {}, {}, {}, { opens++ })
            }
        }
        compose.onNodeWithText("Loading…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Loading your notifications").assertIsDisplayed()
        compose.onAllNodesWithTag("notification-loading-placeholder").assertCountEquals(3)
        compose.onNodeWithText("Return confirmed").assertDoesNotExist()
        compose.onNodeWithText("Finish loading preview").assertDoesNotExist()
        assertEquals(0, opens)

        compose.runOnIdle { error.value = "Listener disconnected" }
        compose.onAllNodesWithTag("notification-loading-placeholder").assertCountEquals(0)
        compose.onNodeWithText("Loading…").assertDoesNotExist()
        compose.onNodeWithText("Listener disconnected").assertIsDisplayed()
        compose.onNodeWithText("Couldn’t load this screen.").assertIsDisplayed()

        compose.runOnIdle { error.value = null; loaded.value = true }
        compose.onAllNodesWithTag("notification-loading-placeholder").assertCountEquals(0)
        compose.onNodeWithText("Return confirmed").assertIsDisplayed().performClick()
        assertEquals(1, opens)

        compose.runOnIdle { items.value = emptyList() }
        compose.onNodeWithText("A little quiet here.").assertIsDisplayed()
        compose.onNodeWithText("Loading…").assertDoesNotExist()
        compose.onNodeWithText("Couldn’t load this screen.").assertDoesNotExist()
    }

    @Test
    fun inboxBottomTabsUseTheRealNavigationCallback() {
        var selected: BottomNavTab? = null
        compose.setContent {
            BookHavenTheme {
                NotificationsContent(
                    items = emptyList(), loaded = true, error = null,
                    filter = InboxFilter.ALL, openingId = null, actionError = null, now = now,
                    onBack = {}, onFilter = {}, onRetry = {}, onOpen = {},
                    onTab = { selected = it }
                )
            }
        }
        compose.onNodeWithText("Loans").performClick()
        assertEquals(BottomNavTab.LOANS, selected)
    }
}
