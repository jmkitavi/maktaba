package com.maktaba.app.data

import org.junit.Assert.*
import org.junit.Test

class CollectionModelsTest {
    private fun book(id: String, catalogue: String = id, status: BookStatus = BookStatus.OWNED,
        format: BookFormat = BookFormat.PHYSICAL) =
        Book(id, "Title $id", "Author", 0, status, catalogId = catalogue, format = format)

    @Test
    fun candidatesRetainBorrowedDigitalAndSavedReferencesWithoutDuplicatingCopies() {
        val owned = book("copy-one", "same-title")
        val secondCopy = book("copy-two", "same-title")
        val borrowed = book("virtual-copy", "borrowed-title", BookStatus.BORROWED)
        val digital = book("digital-copy", "digital-title", format = BookFormat.DIGITAL)
        val saved = book("saved-title")
        val result = collectionCandidates(listOf(owned, secondCopy, borrowed, digital),
            listOf(saved), listOf(saved, owned))
        assertEquals(listOf("same-title", "borrowed-title", "digital-title", "saved-title"),
            result.map { it.catalogId })
        assertEquals(BookStatus.BORROWED, result[1].status)
    }

    @Test
    fun globalHidingIsOwnerScopedAndDoesNotRemovePrivateMembership() {
        val firstOwner = CustomCollection("one", "First", listOf("shared-title", "other-title"), true)
        val secondOwner = CustomCollection("two", "Second", listOf("shared-title"), true)
        assertEquals(listOf("other-title"), firstOwner.visibleCatalogIds(setOf("shared-title")))
        assertEquals(listOf("shared-title"), secondOwner.visibleCatalogIds(emptySet()))
        assertEquals(listOf("shared-title", "other-title"), firstOwner.catalogIds)
    }

    @Test
    fun allHiddenCollectionRemainsValidWithZeroPublicCount() {
        val collection = CustomCollection("one", "Still shared", listOf("hidden"), true)
        assertTrue(collection.visibleCatalogIds(setOf("hidden")).isEmpty())
        assertTrue(collection.shared)
        assertEquals(1, collection.catalogIds.size)
    }

    @Test
    fun nameValidationTrimsAndEnforcesIndependentLimits() {
        assertEquals("Quiet Sundays", CollectionValidation.name("  Quiet Sundays  "))
        assertEquals("a".repeat(60), CollectionValidation.name("a".repeat(60)))
        assertEquals("a".repeat(40), CollectionValidation.libraryName("a".repeat(40)))
        listOf("", " ", "a".repeat(61)).forEach {
            assertThrows(IllegalArgumentException::class.java) { CollectionValidation.name(it) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollectionValidation.libraryName("a".repeat(41))
        }
    }

    @Test
    fun membershipDeduplicatesBeforeLimitAndRejectsInvalidReferences() {
        val ids = (1..500).map { "catalog-$it" }
        assertEquals(ids, CollectionValidation.catalogIds(ids + ids))
        assertThrows(IllegalArgumentException::class.java) {
            CollectionValidation.catalogIds(ids + "extra")
        }
        listOf("", " ", "users/private").forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                CollectionValidation.catalogIds(listOf(invalid))
            }
        }
    }

    @Test
    fun directorySearchNormalizesCaseDiacriticsAndWhitespaceWithBoundedInput() {
        assertEquals("mira's reading room", CollectionValidation.search("  MÍRA'S   Reading Room  "))
        assertEquals(60, CollectionValidation.search("a".repeat(200)).length)
    }

    @Test
    fun publicMappingRejectsStalePublicationAndAcceptsCatalogueOnlyReferences() {
        val wire = mapOf<String, Any?>(
            "catalogBookId" to "borrowed-reference", "title" to "Public catalogue title",
            "author" to "Author", "format" to "DIGITAL", "publicationEpoch" to 7L,
            "ownerUid" to "must-not-be-mapped", "borrowerUid" to "must-not-be-mapped",
            "availability" to "on_loan"
        )
        val book = CollectionWireMapping.collectionBook(wire, 7L)
        assertEquals(SharedCollectionBook("borrowed-reference", "Public catalogue title",
            "Author", "", BookFormat.DIGITAL), book)
        assertThrows(IllegalStateException::class.java) {
            CollectionWireMapping.collectionBook(wire, 6L)
        }
    }

    @Test
    fun publicMappingDoesNotTurnIncompleteRecordIntoFakeTitle() {
        assertThrows(IllegalStateException::class.java) {
            CollectionWireMapping.collectionBook(mapOf("publicationEpoch" to 7L), 7L)
        }
    }
}
