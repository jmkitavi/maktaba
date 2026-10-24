package com.maktaba.app.ui.screens

import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LoanRole
import com.maktaba.app.data.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CoreLibraryPresentationTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC)
    private val owned = Book(
        id = "catalogue-1", catalogId = "catalogue-1",
        title = "Private copy title", author = "An Author", coverRes = 0,
        status = BookStatus.OWNED, format = BookFormat.PHYSICAL
    )
    private val catalogue = owned.copy(title = "Public catalogue title", ownerId = "")

    @Test
    fun catalogueContextNeverSelectsOwnedCopyEvenWithIdenticalId() {
        val selection = selectLibraryDetail(
            "catalogue-1", catalogueOnly = true, copies = listOf(owned),
            catalogue = listOf(catalogue)
        )!!
        assertEquals("Public catalogue title", selection.book.title)
        assertFalse(selection.privateCopy)
        assertFalse(selection.canLend)
        assertFalse(selection.canRemove)
        assertNull(selectLibraryDetail("catalogue-1", true, listOf(owned), emptyList()))
        assertTrue(selectLibraryDetail("catalogue-1", false, listOf(owned), listOf(catalogue))!!.privateCopy)
    }

    @Test
    fun initialErrorIsNotLoadingOrEmptyAndFilteredEmptyIsNotAnEmptyLibrary() {
        assertEquals(LibraryFeedPhase.ERROR, libraryFeedPhase(false, "Offline", 0))
        assertEquals(LibraryFeedPhase.ERROR, libraryFeedPhase(true, "Offline", 4))
        assertEquals(LibraryFeedPhase.LOADING, libraryFeedPhase(false, null, 0))
        assertEquals(LibraryFeedPhase.EMPTY, libraryFeedPhase(true, null, 0))
        assertEquals(LibraryFeedPhase.NO_RESULTS, libraryFeedPhase(true, null, 4, 0))
    }

    @Test
    fun returnedNotificationCatalogueIdResolvesRestoredOwnedCopyButNotBorrowedCopy() {
        val restored = owned.copy(id = "restored-copy")
        val lent = owned.copy(id = "lent-copy", status = BookStatus.LENT_OUT)
        val borrowed = owned.copy(id = "borrowed-copy", status = BookStatus.BORROWED)
        val copies = listOf(borrowed, lent, restored)
        assertEquals(
            LibraryDetailSelection(restored, privateCopy = true),
            selectLibraryDetail(owned.catalogId, false, copies, listOf(catalogue))
        )
        assertEquals(
            LibraryDetailSelection(catalogue, privateCopy = false),
            selectLibraryDetail(owned.catalogId, true, copies, listOf(catalogue))
        )
        assertEquals(
            LibraryDetailSelection(catalogue, privateCopy = false),
            selectLibraryDetail(owned.catalogId, false, listOf(borrowed), listOf(catalogue))
        )
        assertEquals(
            LibraryDetailSelection(borrowed, privateCopy = true),
            selectLibraryDetail(borrowed.id, false, copies, listOf(catalogue))
        )
    }

    @Test
    fun searchCombinesWithRoleAndOverdueUsesOpenLoansAndLocalDate() {
        val lent = owned.copy(id = "lent", status = BookStatus.LENT_OUT, genre = "Essays")
        val today = owned.copy(id = "today", status = BookStatus.BORROWED)
        val returned = owned.copy(id = "returned")
        val books = listOf(owned, lent, today, returned)
        val loans = listOf(
            loan("lent", Instant.parse("2026-10-19T23:59:59Z")),
            loan("today", Instant.parse("2026-10-23T01:00:00Z")),
            loan("returned", Instant.parse("2026-10-18T12:00:00Z"), status = LoanStatus.RETURNED)
        )
        assertEquals(
            listOf(lent),
            filterLibraryBooks(books, loans, LibraryFilter.LENT_OUT, "  eSSaYS ", clock)
        )
        assertEquals(listOf(lent), filterLibraryBooks(books, loans, LibraryFilter.OVERDUE, "", clock))
        assertTrue(filterLibraryBooks(books, loans, LibraryFilter.BORROWED, "Essays", clock).isEmpty())
    }

    @Test
    fun loansExcludePendingAndReturnedAndSortUnknownDatesLastWithinRole() {
        val early = loan("early", Instant.parse("2026-10-19T12:00:00Z"))
        val later = loan("later", Instant.parse("2026-10-25T12:00:00Z"), status = LoanStatus.RETURN_REQUESTED)
        val unknown = loan("unknown", null)
        val borrowed = loan("borrowed", Instant.parse("2026-10-18T12:00:00Z"), role = LoanRole.BORROWER)
        assertEquals(
            listOf(early, later, unknown),
            visibleLibraryLoans(
                listOf(
                    unknown, later, borrowed, early,
                    loan("pending", null, status = LoanStatus.PENDING),
                    loan("closed", null, status = LoanStatus.RETURNED)
                ),
                lender = true
            )
        )
        assertEquals(listOf(borrowed), visibleLibraryLoans(listOf(early, borrowed), lender = false))
    }

    @Test
    fun physicalOwnedCopyOnlyCanLendAndBorrowedCopiesCannotBeRemoved() {
        assertTrue(LibraryDetailSelection(owned, true).canLend)
        assertFalse(LibraryDetailSelection(owned.copy(format = BookFormat.DIGITAL), true).canLend)
        assertFalse(LibraryDetailSelection(owned.copy(format = BookFormat.UNKNOWN), true).canLend)
        assertFalse(LibraryDetailSelection(owned.copy(status = BookStatus.BORROWED), true).canRemove)
        assertFalse(LibraryDetailSelection(owned.copy(status = BookStatus.LENT_OUT), true).canRemove)
    }

    private fun loan(
        bookId: String,
        due: Instant?,
        status: LoanStatus = LoanStatus.ACTIVE,
        role: LoanRole = LoanRole.LENDER
    ) = ActiveLoan(
        id = "loan-$bookId", bookId = bookId, counterpartyName = "Another Reader",
        role = role, status = status, dueAt = due, acceptedAt = null,
        returnRequestedAt = null, returnedAt = null
    )
}
