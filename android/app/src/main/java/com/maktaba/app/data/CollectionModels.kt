package com.maktaba.app.data

import java.text.Normalizer
import java.util.Locale
import com.google.firebase.Timestamp

data class LibrarySharingSettings(
    val libraryShared: Boolean = false,
    val libraryName: String = "",
    val hiddenCatalogIds: Set<String> = emptySet(),
    val publicationEpoch: Long = 0
)

data class CustomCollection(
    val id: String,
    val name: String,
    val catalogIds: List<String>,
    val shared: Boolean,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
) {
    fun visibleCatalogIds(hiddenIds: Set<String>): List<String> =
        catalogIds.distinct().filterNot { it in hiddenIds }
}

data class SharedLibrarySummary(
    val ownerUid: String,
    val libraryName: String,
    val ownerDisplayName: String,
    val ownerPhotoUrl: String,
    val bookCount: Int,
    val coverUrls: List<String>,
    val publicationEpoch: Long,
    val previewBooks: List<SharedCollectionBook> = emptyList()
)

// Collection books intentionally cannot carry copy ownership or loan/availability data.
data class SharedCollectionBook(
    val catalogBookId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val format: BookFormat,
    val addedAt: Long? = null
)

enum class SharedAvailability { AVAILABLE, ON_LOAN }

data class SharedLibraryBook(
    val catalogBookId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val format: BookFormat,
    val availability: SharedAvailability,
    val addedAt: Long? = null
)

data class SharedCollectionSummary(
    val id: String,
    val ownerUid: String,
    val name: String,
    val bookCount: Int,
    val coverUrls: List<String>,
    val publicationEpoch: Long
)

data class PublicReaderProfile(
    val ownerUid: String,
    val ownerDisplayName: String,
    val ownerPhotoUrl: String,
    val collectionCount: Int,
    val publicationEpoch: Long
)

data class SharedLibraryPage(
    val library: SharedLibrarySummary,
    val books: List<SharedLibraryBook>,
    val hasMore: Boolean
)

data class SharedCollectionPage(
    val collection: SharedCollectionSummary,
    val reader: PublicReaderProfile,
    val books: List<SharedCollectionBook>,
    val hasMore: Boolean
)

data class PublicReaderPage(
    val reader: PublicReaderProfile,
    val library: SharedLibrarySummary?,
    val collections: List<SharedCollectionSummary>,
    val hasMore: Boolean
)

object CollectionValidation {
    fun name(value: String): String = value.trim().also {
        require(it.length in 1..60) { "Use a collection name between 1 and 60 characters." }
    }

    fun libraryName(value: String): String = value.trim().also {
        require(it.length in 1..40) { "Use a library name between 1 and 40 characters." }
    }

    fun catalogIds(values: Collection<String>): List<String> = values.distinct().also {
        require(it.size <= 500) { "A collection can contain up to 500 titles." }
        require(it.all { id -> id.isNotBlank() && '/' !in id }) { "A title reference is invalid." }
    }

    fun search(value: String): String =
        Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").take(60)
}

fun collectionCandidates(
    copies: List<Book>,
    saved: List<Book>,
    catalogue: List<Book>
): List<Book> = (copies + saved + catalogue).distinctBy { it.catalogId }

internal object CollectionWireMapping {
    fun collectionBook(data: Map<String, Any?>, epoch: Long): SharedCollectionBook {
        check((data["publicationEpoch"] as? Number)?.toLong() == epoch) {
            "This publication has changed."
        }
        fun required(key: String) = (data[key] as? String)?.takeIf { it.isNotBlank() }
            ?: error("A shared record is incomplete.")
        return SharedCollectionBook(
            catalogBookId = required("catalogBookId"),
            title = required("title"),
            author = data["author"] as? String ?: "",
            coverUrl = data["coverUrl"] as? String ?: "",
            format = BookFormat.from(data["format"] as? String, null),
            addedAt = (data["addedAt"] as? Timestamp)?.toDate()?.time
        )
    }
}

fun collectionErrorMessage(error: Throwable): String {
    val firestoreCode = (error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
    when (firestoreCode) {
        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            return "Sign in again to continue."
        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
        com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND ->
            return "This content is private, publishing, or no longer shared. Check again shortly."
        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
        com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            return "We could not verify this content online. Check your connection and retry."
        else -> Unit
    }
    val code = (error as? com.google.firebase.functions.FirebaseFunctionsException)?.code
    return when (code) {
        com.google.firebase.functions.FirebaseFunctionsException.Code.UNAUTHENTICATED ->
            "Sign in again to continue."
        com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED ->
            "This content is no longer shared with you."
        com.google.firebase.functions.FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
            "Too many changes. Wait a moment, then retry."
        com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE,
        com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
            "We could not reach Book Haven. Check your connection and retry."
        else -> error.localizedMessage?.takeIf { it.isNotBlank() }
            ?: "We could not complete this request. Please retry."
    }
}
