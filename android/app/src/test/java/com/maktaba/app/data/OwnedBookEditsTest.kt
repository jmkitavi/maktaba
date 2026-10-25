package com.maktaba.app.data

import org.junit.Assert.*
import org.junit.Test

class OwnedBookEditsTest {
    private val book = Book("copy", "Original title", "Original author", 0, BookStatus.OWNED,
        catalogId = "catalogue", ownerId = "owner", format = BookFormat.PHYSICAL,
        physicalEditionIsbn13 = "9780306406157")

    @Test fun privateDetailsChangePresentationWithoutChangingOwnershipOrLendingEligibility() {
        val draft = OwnedBookDraft.from(book).copy(title = " My edition ", binding = "Ebook",
            pages = "123", isbn13 = "978-0-306-40615-7")
        val edited = applyOwnedBookDetails(book, draft.toFields())
        assertEquals("My edition", edited.title)
        assertEquals("9780306406157", edited.isbn13)
        assertEquals(123, edited.pages)
        assertEquals(book.id, edited.id)
        assertEquals(book.catalogId, edited.catalogId)
        assertEquals(book.ownerId, edited.ownerId)
        assertEquals(BookFormat.PHYSICAL, edited.format)
        assertEquals(book.physicalEditionIsbn13, edited.physicalEditionIsbn13)
        assertEquals("Original title", book.title)
    }

    @Test fun editingRejectsInvalidIsbnCountsAndMissingRequiredDetails() {
        val valid = OwnedBookDraft.from(book)
        assertNull(valid.validationError())
        for (draft in listOf(valid.copy(title = " "), valid.copy(author = ""),
            valid.copy(isbn13 = "9780306406158"), valid.copy(isbn10 = "not an isbn"),
            valid.copy(pages = "-1"), valid.copy(pages = "100001"), valid.copy(pages = "999999999999999999999"),
            valid.copy(description = "x".repeat(20001)))) {
            assertNotNull(draft.validationError())
        }
    }
}
