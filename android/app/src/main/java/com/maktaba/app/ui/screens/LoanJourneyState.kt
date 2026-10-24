package com.maktaba.app.ui.screens

import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.data.NotificationItem
import com.maktaba.app.nav.Routes
import java.time.Instant

enum class ReturnStage { REQUEST, WAITING, CONFIRM, COMPLETE, UNAVAILABLE }

fun returnStage(loan: ActiveLoan, currentUid: String?): ReturnStage = when {
    currentUid.isNullOrBlank() -> ReturnStage.UNAVAILABLE
    currentUid == loan.counterpartyUid -> ReturnStage.UNAVAILABLE
    loan.status == LoanStatus.RETURNED -> ReturnStage.COMPLETE
    loan.status == LoanStatus.ACTIVE -> ReturnStage.REQUEST
    loan.status != LoanStatus.RETURN_REQUESTED -> ReturnStage.UNAVAILABLE
    loan.returnRequestedById.isNullOrBlank() -> ReturnStage.UNAVAILABLE
    loan.returnRequestedById == currentUid || loan.returnRequestedByCurrentUser -> ReturnStage.WAITING
    loan.returnRequestedById == loan.counterpartyUid -> ReturnStage.CONFIRM
    else -> ReturnStage.UNAVAILABLE
}

data class ReminderPresentation(
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
    val nextAllowedAt: Instant? = null
) {
    fun canSend(loan: ActiveLoan, review: Boolean, now: Instant): Boolean =
        review && loan.isLender && loan.status == LoanStatus.ACTIVE &&
            !sending && !sent && (nextAllowedAt == null || !now.isBefore(nextAllowedAt))
}

enum class InboxFilter { ALL, UNREAD }
enum class InboxStatus { LOADING, ERROR, EMPTY, NO_UNREAD, READY }

fun inboxStatus(
    loaded: Boolean,
    error: String?,
    items: List<NotificationItem>,
    filter: InboxFilter
): InboxStatus = when {
    error != null -> InboxStatus.ERROR
    !loaded -> InboxStatus.LOADING
    items.isEmpty() -> InboxStatus.EMPTY
    filter == InboxFilter.UNREAD && items.none { it.isUnread } -> InboxStatus.NO_UNREAD
    else -> InboxStatus.READY
}

// These are the existing notification destinations, including the legacy fallback.
fun notificationRoute(item: NotificationItem): String? {
    val destinationId = if (item.type == "return_confirmed") {
        item.catalogBookId.ifBlank { item.bookId }
    } else {
        item.bookId
    }
    if (destinationId.isBlank()) return null
    return when (item.type) {
        "loan_invite_accepted", "loan_reminder" -> Routes.ActiveLoan.createRoute(destinationId)
        "return_requested" -> Routes.ReturnConfirmation.createRoute(destinationId)
        "return_confirmed" -> Routes.BookDetail.createRoute(destinationId)
        else -> Routes.ReturnReminder.createRoute(destinationId)
    }
}

internal fun reminderCooldownFromMessage(message: String?): Instant? {
    val timestamp = message?.substringAfter("Try again after ", "")?.trim()?.removeSuffix(".")
    return timestamp?.takeIf(String::isNotBlank)?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
