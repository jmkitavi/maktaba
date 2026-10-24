package com.maktaba.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LoanRole
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.data.NotificationItem
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.theme.BookHavenTheme
import java.time.Instant
import org.junit.Assert.assertEquals
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
        compose.onNodeWithText("Send a Reminder").performClick()
        assertEquals(1, reviewCalls)
    }

    @Test
    fun borrowerHasNoSendReminderAction() {
        compose.setContent {
            BookHavenTheme {
                ActiveLoanContent(book.copy(status = BookStatus.BORROWED),
                    loan.copy(role = LoanRole.BORROWER), ReturnStage.REQUEST, {}, {}, {}, {}, now = now)
            }
        }
        compose.onNodeWithText("Send a Reminder").assertDoesNotExist()
        compose.onNodeWithText("Borrowed").assertIsDisplayed()
        compose.onNodeWithText("Start a Return").assertIsDisplayed()
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
        compose.onNodeWithText("You Are All Caught Up").assertDoesNotExist()
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(1, retries)
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
