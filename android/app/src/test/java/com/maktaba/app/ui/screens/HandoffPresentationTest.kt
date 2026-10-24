package com.maktaba.app.ui.screens

import com.maktaba.app.data.*
import com.maktaba.app.util.LoanTimeFormatter
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HandoffPresentationTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")
    private val book = Book("copy", "A real title", "An author", 0, BookStatus.OWNED,
        format = BookFormat.PHYSICAL)
    private val loan = ActiveLoan("invite", book.id, "Lender", LoanRole.BORROWER,
        LoanStatus.PENDING, now.plusSeconds(3600), null, null, null)
    private val invite = PendingLoanInvite("invite", book.id,
        requireNotNull(LoanInviteCode.parse("ABCD-1234")), now.plusSeconds(7200), now.plusSeconds(3600))

    @Test
    fun acceptsManualCodesAndProperDeepLinksButRejectsOtherQrPayloads() {
        assertEquals("ABCD-1234", parseLendingCode(" abcd-1234 "))
        assertEquals("ABCD-1234", parseLendingCode(lendingLinkFor("ABCD-1234")))
        assertEquals("ABCD-1234", parseLendingCode("maktaba://loan/?source=qr&code=abcd%2D1234"))
        listOf("https://loan?code=ABCD-1234", "maktaba://other?code=ABCD-1234",
            "maktaba://loan?code=ABCD-1234&code=EFGH-5678", "maktaba://loan?code=%ZZ",
            "maktaba://user@loan?code=ABCD-1234", "maktaba://loan:80?code=ABCD-1234",
            "maktaba://loan/another?code=ABCD-1234", "ABCD-12345", "not a code"
        ).forEach { assertNull(it, parseLendingCode(it)) }
    }

    @Test
    fun defaultUsesFourteenLocalCalendarDaysAndLocalEndOfDayAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val clock = Clock.fixed(Instant.parse("2026-10-24T03:30:00Z"), zone)
        val picker = defaultReturnPickerMillis(clock)
        assertEquals(LocalDate.of(2026, 11, 6), Instant.ofEpochMilli(picker).atZone(ZoneOffset.UTC).toLocalDate())
        val due = Instant.ofEpochMilli(LoanTimeFormatter.localDateToEndOfDayMillis(picker, zone))
        assertEquals(LocalDate.of(2026, 11, 6), due.atZone(zone).toLocalDate())
        assertEquals(23, due.atZone(zone).hour)
        assertEquals(59, due.atZone(zone).minute)
    }

    @Test
    fun lendingRequiresAnOwnedPhysicalCopyWithNoAcceptedOpenLoan() {
        assertTrue(canLendCopy(book, null))
        assertFalse(canLendCopy(book.copy(format = BookFormat.UNKNOWN), null))
        assertFalse(canLendCopy(book.copy(format = BookFormat.DIGITAL), null))
        assertFalse(canLendCopy(book.copy(status = BookStatus.BORROWED), null))
        assertFalse(canLendCopy(book.copy(status = BookStatus.LENT_OUT), null))
        assertFalse(canLendCopy(book, loan.copy(status = LoanStatus.ACTIVE)))
        assertFalse(canLendCopy(book, loan.copy(status = LoanStatus.RETURN_REQUESTED)))
    }

    @Test
    fun expiryAndDueDateAreExclusiveBoundariesAndIssuedDetailsCannotDrift() {
        assertTrue(inviteIsPending(invite, now))
        assertFalse(inviteIsPending(invite, invite.expiresAt))
        assertFalse(inviteIsPending(invite.copy(dueAt = now), now))
        assertFalse(inviteIsPending(invite.copy(dueAt = null), now))
        listOf("cancelled", "accepted", "used", "expired").forEach {
            assertFalse(inviteIsPending(invite.copy(status = it), now))
        }
        assertTrue(sameIssuedInvite(invite, invite.copy(status = "cancelled")))
        assertFalse(sameIssuedInvite(invite, invite.copy(dueAt = now.plusSeconds(200))))
        assertFalse(sameIssuedInvite(invite, invite.copy(expiresAt = now.plusSeconds(200))))
        assertFalse(sameIssuedInvite(invite, invite.copy(code = requireNotNull(LoanInviteCode.parse("EFGH-5678")))))
    }

    @Test
    fun onlyPendingBorrowerCanConfirmAndSubmissionSuccessAndTerminalFailureDisableIt() {
        val state = ConfirmBorrowState(book, loan, now = now)
        assertTrue(canConfirmBorrow(state))
        assertFalse(canConfirmBorrow(state.copy(submitting = true)))
        assertFalse(canConfirmBorrow(state.copy(accepted = true)))
        assertFalse(canConfirmBorrow(state.copy(unavailable = true)))
        assertFalse(canConfirmBorrow(state.copy(now = loan.dueAt!!)))
        assertFalse(canConfirmBorrow(state.copy(invitation = loan.copy(role = LoanRole.LENDER))))
        assertFalse(canConfirmBorrow(state.copy(invitation = loan.copy(status = LoanStatus.ACTIVE))))
    }

    @Test
    fun addBookValidatesBothIsbnFieldsAndKeepsUnknownAndDigitalEditionsSafe() {
        val draft = AddBookDraft(title = " A title ", author = " An author ")
        assertNull(validateAddBookDraft(draft))
        assertNotNull(validateAddBookDraft(draft.copy(title = " ")))
        assertNotNull(validateAddBookDraft(draft.copy(author = ", ,")))
        assertNotNull(validateAddBookDraft(draft.copy(isbn13 = "9780141036144", isbn10 = "invalid")))
        assertNull(validateAddBookDraft(draft.copy(isbn13 = "978-0-14-103614-4", isbn10 = "0-14-103614-1")))
        assertNotNull(validateAddBookDraft(draft.copy(pages = "99999999999999999999")))
        assertEquals(BookFormat.UNKNOWN, addBookEditionFormat(draft))
        assertEquals(BookFormat.UNKNOWN, addBookEditionFormat(draft.copy(binding = "Unknown")))
        assertEquals(BookFormat.DIGITAL, addBookEditionFormat(draft.copy(binding = "E-book")))
        assertEquals(BookFormat.DIGITAL, addBookEditionFormat(draft.copy(binding = "E book")))
        val digitalWithoutBinding = draft.copy(originalFormat = BookFormat.DIGITAL)
        assertEquals("Digital edition", digitalWithoutBinding.metadata().binding)
        val digital = draft.copy(originalFormat = BookFormat.DIGITAL, binding = "Paperback")
        assertEquals(BookFormat.DIGITAL, addBookEditionFormat(digital))
        assertNotNull(validateAddBookDraft(digital))
    }

    @Test
    fun explicitManualEditionKeepsDraftButCannotSilentlyReattachToTheSharedCatalogue() {
        val linked = AddBookDraft(title = "Retained title", author = "Retained author",
            catalogBookId = "shared-edition", source = "firebase", isbn13 = "9780141036144",
            originalFormat = BookFormat.DIGITAL, binding = "E-book")
        val manual = manualAddBookDraft(linked, linked.isbn13)
        assertEquals(linked.title, manual.title)
        assertEquals(linked.author, manual.author)
        assertEquals("", manual.catalogBookId)
        assertEquals("", manual.isbn13)
        assertEquals("", manual.isbn10)
        assertEquals("manual", manual.source)
        assertNull(validateAddBookDraft(manual.copy(binding = "Paperback")))
        val fallback = manualAddBookDraft(AddBookDraft(title = "Typed before a lookup error"), "978-0-14-103614-4")
        assertEquals("Typed before a lookup error", fallback.title)
        assertEquals("9780141036144", fallback.isbn13)
    }
}
