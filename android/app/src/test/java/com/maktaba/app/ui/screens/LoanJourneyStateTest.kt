package com.maktaba.app.ui.screens

import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.LoanRole
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.data.NotificationItem
import com.maktaba.app.nav.Routes
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanJourneyStateTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")
    private val loan = ActiveLoan(
        id = "loan", bookId = "copy", counterpartyName = "Other reader",
        role = LoanRole.LENDER, status = LoanStatus.ACTIVE,
        dueAt = now, acceptedAt = now.minusSeconds(86400),
        returnRequestedAt = null, returnedAt = null, counterpartyUid = "other"
    )

    @Test
    fun requestDoesNotCloseLoanAndOnlyOtherReaderCanConfirmForEitherRole() {
        LoanRole.values().forEach { role ->
            val active = loan.copy(role = role)
            assertEquals(ReturnStage.REQUEST, returnStage(active, "self"))
            val requested = active.copy(status = LoanStatus.RETURN_REQUESTED, returnRequestedById = "self")
            assertEquals(ReturnStage.WAITING, returnStage(requested, "self"))
            assertEquals(ReturnStage.CONFIRM, returnStage(requested.copy(returnRequestedById = "other"), "self"))
            assertEquals(ReturnStage.COMPLETE, returnStage(requested.copy(status = LoanStatus.RETURNED), "self"))
        }
    }

    @Test
    fun malformedPendingAndSignedOutReturnsCannotBeSubmitted() {
        assertEquals(ReturnStage.UNAVAILABLE, returnStage(loan, null))
        assertEquals(ReturnStage.UNAVAILABLE, returnStage(loan.copy(status = LoanStatus.PENDING), "self"))
        assertEquals(ReturnStage.UNAVAILABLE,
            returnStage(loan.copy(status = LoanStatus.RETURN_REQUESTED), "self"))
        assertEquals(ReturnStage.UNAVAILABLE,
            returnStage(loan.copy(status = LoanStatus.RETURN_REQUESTED, returnRequestedById = "unrelated"), "self"))
        assertEquals(ReturnStage.UNAVAILABLE, returnStage(loan, "other"))
        assertEquals(ReturnStage.WAITING, returnStage(
            loan.copy(status = LoanStatus.RETURN_REQUESTED,
                returnRequestedById = "self", returnRequestedByCurrentUser = true), "self"
        ))
    }

    @Test
    fun reminderRequiresLenderReviewAndOpenLoanAndRejectsRepeatSubmission() {
        val ready = ReminderPresentation()
        assertTrue(ready.canSend(loan, true, now))
        assertFalse(ready.canSend(loan, false, now))
        assertFalse(ready.canSend(loan.copy(role = LoanRole.BORROWER), true, now))
        assertFalse(ready.canSend(loan.copy(status = LoanStatus.RETURN_REQUESTED), true, now))
        assertFalse(ready.canSend(loan.copy(status = LoanStatus.RETURNED), true, now))
        assertFalse(ready.copy(sending = true).canSend(loan, true, now))
        assertFalse(ready.copy(sent = true).canSend(loan, true, now))
    }

    @Test
    fun reminderCooldownUsesExactBoundaryAndRetainsRetryableError() {
        val cooldown = ReminderPresentation(error = "Try again", nextAllowedAt = now.plusSeconds(30))
        assertFalse(cooldown.canSend(loan, true, now.plusSeconds(29)))
        assertTrue(cooldown.canSend(loan, true, now.plusSeconds(30)))
        assertEquals(now.plusSeconds(30), reminderCooldownFromMessage(
            "A reminder was just sent. Try again after 2026-10-23T12:00:30.000Z."
        ))
        assertNull(reminderCooldownFromMessage("Network unavailable"))
        assertNull(reminderCooldownFromMessage("Try again after an invalid date."))
    }

    @Test
    fun inboxDistinguishesLoadingFailureEmptyAndNoUnread() {
        val read = notification("loan_reminder").copy(isUnread = false)
        assertEquals(InboxStatus.LOADING, inboxStatus(false, null, emptyList(), InboxFilter.ALL))
        assertEquals(InboxStatus.ERROR, inboxStatus(false, "Offline", emptyList(), InboxFilter.ALL))
        assertEquals(InboxStatus.ERROR, inboxStatus(true, "Offline", listOf(read), InboxFilter.ALL))
        assertEquals(InboxStatus.EMPTY, inboxStatus(true, null, emptyList(), InboxFilter.ALL))
        assertEquals(InboxStatus.NO_UNREAD, inboxStatus(true, null, listOf(read), InboxFilter.UNREAD))
        assertEquals(InboxStatus.READY, inboxStatus(true, null, listOf(read), InboxFilter.ALL))
    }

    @Test
    fun notificationTypesKeepTheirExistingDestinationsIncludingLegacyFallback() {
        listOf("loan_invite_accepted", "loan_reminder").forEach {
            assertEquals(Routes.ActiveLoan.createRoute("copy"), notificationRoute(notification(it)))
        }
        assertEquals(Routes.ReturnConfirmation.createRoute("copy"),
            notificationRoute(notification("return_requested")))
        assertEquals(Routes.BookDetail.createRoute("catalogue"),
            notificationRoute(notification("return_confirmed")))
        listOf("borrower_due_today", "borrower_due_soon", "lender_overdue", "unknown_legacy_type").forEach {
            assertEquals(Routes.ReturnReminder.createRoute("copy"), notificationRoute(notification(it)))
        }
        assertNull(notificationRoute(notification("loan_reminder").copy(bookId = "")))
        assertEquals(Routes.BookDetail.createRoute("catalogue"),
            notificationRoute(notification("return_confirmed").copy(bookId = "")))
        assertEquals(Routes.BookDetail.createRoute("copy"),
            notificationRoute(notification("return_confirmed").copy(catalogBookId = "")))
    }

    private fun notification(type: String) = NotificationItem(
        id = type, bookId = "copy", catalogBookId = "catalogue",
        title = "Update", message = "Loan update", type = type
    )
}
