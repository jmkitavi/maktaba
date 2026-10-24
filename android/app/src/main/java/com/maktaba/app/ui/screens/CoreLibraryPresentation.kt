package com.maktaba.app.ui.screens

import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency
import java.time.Clock

enum class LibraryFilter(val label: String) {
    ALL("All books"), OWNED("On my shelf"), LENT_OUT("Lent out"),
    BORROWED("Borrowed"), OVERDUE("Overdue")
}

enum class LibraryFeedPhase { LOADING, ERROR, EMPTY, NO_RESULTS, CONTENT }

fun libraryFeedPhase(
    loaded: Boolean,
    error: String?,
    totalCount: Int,
    visibleCount: Int = totalCount
): LibraryFeedPhase = when {
    error != null -> LibraryFeedPhase.ERROR
    !loaded -> LibraryFeedPhase.LOADING
    totalCount == 0 -> LibraryFeedPhase.EMPTY
    visibleCount == 0 -> LibraryFeedPhase.NO_RESULTS
    else -> LibraryFeedPhase.CONTENT
}

fun filterLibraryBooks(
    books: List<Book>,
    loans: List<ActiveLoan>,
    filter: LibraryFilter,
    query: String,
    clock: Clock = Clock.systemDefaultZone()
): List<Book> {
    val normalized = query.trim()
    return books.filter { book ->
        val matchesFilter = when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.OWNED -> book.status == BookStatus.OWNED
            LibraryFilter.LENT_OUT -> book.status == BookStatus.LENT_OUT
            LibraryFilter.BORROWED -> book.status == BookStatus.BORROWED
            LibraryFilter.OVERDUE -> loans.any {
                it.bookId == book.id && it.status in openLoanStatuses &&
                    LoanTimeFormatter.urgency(it.dueAt, clock) == LoanUrgency.OVERDUE
            }
        }
        matchesFilter && (normalized.isEmpty() ||
            listOf(book.title, book.author, book.genre).any {
                it.contains(normalized, ignoreCase = true)
            })
    }
}

private val openLoanStatuses = setOf(LoanStatus.ACTIVE, LoanStatus.RETURN_REQUESTED)

fun visibleLibraryLoans(loans: List<ActiveLoan>, lender: Boolean): List<ActiveLoan> =
    loans.filter { it.isLender == lender && it.status in openLoanStatuses }
        .sortedWith(
            compareBy<ActiveLoan> { it.dueAt == null }
                .thenBy { it.dueAt }.thenBy { it.counterpartyName.lowercase() }
        )

data class LibraryDetailSelection(val book: Book, val privateCopy: Boolean) {
    val canLend: Boolean
        get() = privateCopy && book.status == BookStatus.OWNED &&
            book.format == BookFormat.PHYSICAL
    val canRemove: Boolean
        get() = privateCopy && book.status == BookStatus.OWNED
}

data class LibraryBookDetailState(
    val selection: LibraryDetailSelection?,
    val loaded: Boolean,
    val error: String? = null,
    val loan: ActiveLoan? = null,
    val saved: Boolean = false,
    val savedLoaded: Boolean = false,
    val savedError: String? = null,
    val hasOpenActivity: Boolean = false,
    val allowVisibilityManagement: Boolean = false,
    val hidden: Boolean? = null,
    val sharingError: String? = null
)

fun selectLibraryDetail(
    bookId: String,
    catalogueOnly: Boolean,
    copies: List<Book>,
    catalogue: List<Book>
): LibraryDetailSelection? {
    // Never fall back to a private copy for a public catalogue destination.
    if (!catalogueOnly) {
        copies.firstOrNull { it.id == bookId }?.let {
            return LibraryDetailSelection(it, privateCopy = true)
        }
        copies.filter { it.catalogId == bookId && it.status != BookStatus.BORROWED }
            .sortedWith(compareBy<Book> { it.status != BookStatus.OWNED }.thenBy { it.id })
            .firstOrNull()?.let {
                return LibraryDetailSelection(it, privateCopy = true)
            }
    }
    return catalogue.firstOrNull { it.catalogId == bookId }?.let {
        LibraryDetailSelection(it, privateCopy = false)
    }
}

data class LibraryProfileState(
    val name: String,
    val memberSince: Int? = null,
    val shelfCount: Int = 0,
    val lentCount: Int = 0,
    val borrowedCount: Int = 0,
    val libraryLoaded: Boolean = false,
    val libraryError: String? = null,
    val sharingLoaded: Boolean = false,
    val shared: Boolean = false,
    val sharingError: String? = null
) {
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            .take(2).joinToString("") { it.take(1).uppercase() }
}
