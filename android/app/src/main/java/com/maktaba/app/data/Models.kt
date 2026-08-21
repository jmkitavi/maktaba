package com.maktaba.app.data

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.maktaba.app.BuildConfig
import com.maktaba.app.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.Instant

enum class BookStatus { OWNED, LENT_OUT, BORROWED }

data class Book(
    val id: String,
    val title: String,
    val author: String,
    @DrawableRes val coverRes: Int,
    val status: BookStatus,
    val catalogId: String = id,
    val coverUrl: String = "",
    val ownerId: String = "",
    val genre: String = "",
    val published: String = "",
    val pages: Int = 0,
    val description: String = "",
    val binding: String = "",
    val format: BookFormat = BookFormat.UNKNOWN,
    val physicalEditionIsbn13: String = ""
)

data class NotificationItem(
    val id: String,
    val bookId: String,
    val catalogBookId: String = "",
    val title: String,
    val message: String,
    val isUnread: Boolean = true,
    val type: String = "",
    val loanId: String? = null
)

data class BookMetadata(
    val catalogBookId: String = "",
    val title: String = "",
    val authors: List<String> = emptyList(),
    val isbn13: String = "",
    val isbn10: String = "",
    val publisher: String = "",
    val publishedDate: String = "",
    val binding: String = "",
    val pageCount: Int = 0,
    val genres: List<String> = emptyList(),
    val description: String = "",
    val coverUrl: String = "",
    val source: String = "manual",
    val sourceUrl: String = "",
    val format: BookFormat = BookFormat.UNKNOWN,
    val physicalEditionIsbn13: String = ""
)

object LibraryRepository {
    val books: SnapshotStateList<Book> = emptyList<Book>().toMutableStateList()
    val maktabaCollection: SnapshotStateList<Book> = emptyList<Book>().toMutableStateList()
    val activeLoans: SnapshotStateList<ActiveLoan> = emptyList<ActiveLoan>().toMutableStateList()
    val notifications: SnapshotStateList<NotificationItem> = emptyList<NotificationItem>().toMutableStateList()
    val wishlistCatalogIds: SnapshotStateList<String> = emptyList<String>().toMutableStateList()
    val lendingCodes: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String> =
        androidx.compose.runtime.mutableStateMapOf()
    val pendingInvites: androidx.compose.runtime.snapshots.SnapshotStateMap<String, PendingLoanInvite> =
        androidx.compose.runtime.mutableStateMapOf()
    private val loanIdsByBook: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String> =
        androidx.compose.runtime.mutableStateMapOf()

    /**
     * False until the first catalogue *and* first user-copies snapshot have arrived.
     * Without this the library screen cannot tell "you own nothing" from "Firestore has
     * not answered yet", and told every returning user their shelf was empty on launch.
     */
    var hasLoadedLibrary by mutableStateOf(false)
        private set

    /**
     * Loans arrive from their own pair of listeners, so a screen that resolves a loan can
     * be looking at an empty list long after [hasLoadedLibrary] flips. Without this, loan
     * screens rendered "Loan unavailable" for the moment before the snapshot landed.
     */
    var hasLoadedLoans by mutableStateOf(false)
        private set

    private var catalogLoaded = false
    private var copiesLoaded = false
    private val loanFieldsLoaded = mutableSetOf<String>()

    private fun markLoaded(catalog: Boolean = false, copies: Boolean = false) {
        if (catalog) catalogLoaded = true
        if (copies) copiesLoaded = true
        if (catalogLoaded && copiesLoaded) hasLoadedLibrary = true
    }

    private fun markLoansLoaded(participantField: String) {
        loanFieldsLoaded += participantField
        if (loanFieldsLoaded.containsAll(listOf("lenderUid", "borrowerUid"))) {
            hasLoadedLoans = true
        }
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val functions by lazy { FirebaseFunctions.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = mutableListOf<ListenerRegistration>()
    private val catalog = mutableMapOf<String, Map<String, Any?>>()
    private val copies = mutableMapOf<String, Map<String, Any?>>()
    private val loans = mutableMapOf<String, Map<String, Any?>>()
    private val lenderLoans = mutableMapOf<String, Map<String, Any?>>()
    private val borrowerLoans = mutableMapOf<String, Map<String, Any?>>()
    private val pendingBorrowLoans = mutableMapOf<String, ActiveLoan>()
    private val pendingBorrowBooks = mutableMapOf<String, Book>()
    private var currentUid: String? = null

    fun start(uid: String) {
        if (currentUid == uid && listeners.isNotEmpty()) return
        stop()
        currentUid = uid
        listenToCatalog()
        listenToCopies(uid)
        listenToLoans(uid, "lenderUid", lenderLoans)
        listenToLoans(uid, "borrowerUid", borrowerLoans)
        listenToNotifications(uid)
        listenToWishlist(uid)
        if (!BuildConfig.USE_FIREBASE_EMULATORS) {
            scope.launch {
                runCatching { registerDeviceToken(uid, FirebaseMessaging.getInstance().token.await()) }
            }
        }
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
        catalog.clear()
        copies.clear()
        loans.clear()
        lenderLoans.clear()
        borrowerLoans.clear()
        pendingBorrowLoans.clear()
        pendingBorrowBooks.clear()
        clearObservableState()
        catalogLoaded = false
        copiesLoaded = false
        loanFieldsLoaded.clear()
        hasLoadedLibrary = false
        hasLoadedLoans = false
        currentUid = null
    }

    private fun clearObservableState() {
        books.clear()
        maktabaCollection.clear()
        activeLoans.clear()
        notifications.clear()
        wishlistCatalogIds.clear()
        lendingCodes.clear()
        pendingInvites.clear()
        loanIdsByBook.clear()
    }

    fun bookById(id: String): Book? =
        books.find { it.id == id } ?: maktabaCollection.find { it.catalogId == id }

    fun activeLoanFor(bookId: String): ActiveLoan? = activeLoans.find { it.bookId == bookId }

    fun bookIdForCode(code: String): String? = lendingCodes[code.trim().uppercase()]
    fun pendingInviteById(inviteId: String): PendingLoanInvite? = pendingInvites[inviteId]

    suspend fun lookupBookByIsbn(isbn: String): BookMetadata {
        val result = callFunction("lookupBookByIsbn", mapOf("isbn" to isbn))
        val metadata = result["metadata"] as? Map<*, *>
            ?: error("Firebase returned incomplete book metadata.")
        val source = result["source"] as? String ?: "firebase"
        return BookMetadata(
            catalogBookId = metadata["catalogBookId"] as? String ?: "",
            title = metadata["title"] as? String ?: "",
            authors = (metadata["authors"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            isbn13 = metadata["isbn13"] as? String ?: "",
            isbn10 = metadata["isbn10"] as? String ?: "",
            publisher = metadata["publisher"] as? String ?: "",
            publishedDate = metadata["publishedDate"] as? String ?: "",
            binding = metadata["binding"] as? String ?: "",
            coverUrl = metadata["coverUrl"] as? String ?: "",
            source = source,
            sourceUrl = metadata["sourceUrl"] as? String ?: "",
            format = BookFormat.from(metadata["format"] as? String, metadata["binding"] as? String),
            physicalEditionIsbn13 = metadata["physicalEditionIsbn13"] as? String ?: ""
        )
    }

    suspend fun addBook(metadata: BookMetadata) {
        requireNotNull(currentUid) { "Sign in before adding a book." }
        callFunction(
            "addBookToLibrary",
            mapOf(
                "catalogBookId" to metadata.catalogBookId.ifBlank { null },
                "isbn13" to metadata.isbn13.ifBlank { null },
                "isbn10" to metadata.isbn10.ifBlank { null },
                "title" to metadata.title,
                "authors" to metadata.authors,
                "publisher" to metadata.publisher,
                "publishedDate" to metadata.publishedDate,
                "binding" to metadata.binding,
                "pageCount" to metadata.pageCount,
                "genres" to metadata.genres,
                "description" to metadata.description,
                "coverUrl" to metadata.coverUrl,
                "metadataSource" to metadata.source,
                "metadataSourceUrl" to metadata.sourceUrl,
                "physicalEditionIsbn13" to metadata.physicalEditionIsbn13.ifBlank { null }
            )
        )
    }

    suspend fun startLending(bookId: String, borrowerName: String, dueAtMillis: Long): PendingLoanInvite {
        val book = requireNotNull(bookById(bookId)) { "This book is no longer available." }
        require(book.format != BookFormat.DIGITAL) {
            "Digital editions can’t be lent through Book Haven."
        }
        val result = callFunction(
            "createLoanInvite",
            mapOf(
                "copyId" to bookId,
                "dueAtMillis" to dueAtMillis,
                "borrowerDisplayName" to borrowerName
            )
        )
        val invite = pendingLoanInviteFromCreateResult(bookId, result)
        lendingCodes[invite.code.value] = bookId
        loanIdsByBook[bookId] = invite.id
        pendingInvites[invite.id] = invite
        return invite
    }

    suspend fun loadPendingInvite(inviteId: String): PendingLoanInvite {
        pendingInvites[inviteId]?.let { return it }
        val snapshot = firestore.collection("loanInvites").document(inviteId).get().await()
        require(snapshot.exists()) { "This lending invitation is no longer available." }
        val status = snapshot.getString("status").orEmpty()
        require(status == "pending") { "This lending invitation is $status." }
        val code = LoanInviteCode.parse(snapshot.getString("code"))
            ?: error("The lending invitation has an invalid code.")
        val invite = PendingLoanInvite(
            id = inviteId,
            copyId = snapshot.getString("copyId").orEmpty(),
            code = code,
            dueAt = snapshot.getTimestamp("dueDate")?.toDate()?.toInstant(),
            expiresAt = requireNotNull(snapshot.getTimestamp("expiresAt")) {
                "The lending invitation is missing its expiry."
            }.toDate().toInstant(),
            status = status
        )
        require(invite.copyId.isNotBlank()) { "The lending invitation is missing its book." }
        pendingInvites[inviteId] = invite
        lendingCodes[code.value] = invite.copyId
        loanIdsByBook[invite.copyId] = inviteId
        return invite
    }

    suspend fun cancelLendingInvite(inviteId: String) {
        val invite = pendingInvites[inviteId] ?: loadPendingInvite(inviteId)
        callFunction("cancelLoanInvite", mapOf("loanId" to inviteId))
        loanIdsByBook.remove(invite.copyId)
        pendingInvites.remove(inviteId)
        lendingCodes.entries
            .filter { it.value == invite.copyId }
            .map { it.key }
            .forEach { lendingCodes.remove(it) }
    }

    suspend fun resolveLoanCode(code: String): String {
        val result = callFunction("resolveLoanInvite", mapOf("inviteCode" to code.trim().uppercase()))
        val copyId = result["copyId"] as? String ?: error("The invitation is missing its book copy.")
        val loanId = result["loanId"] as? String ?: error("The invitation is missing its loan.")
        val catalogId = result["catalogBookId"] as? String ?: ""
        val title = result["title"] as? String ?: "Shared book"
        val authors = result["authors"] as? List<*>
        val author = authors?.firstOrNull() as? String ?: result["author"] as? String ?: "Unknown author"
        val coverUrl = result["coverUrl"] as? String ?: ""
        val lenderName = result["lenderDisplayName"] as? String ?: "Lender"
        val dueAtMillis = (result["dueAtMillis"] as? Number)?.toLong()
        lendingCodes[code.trim().uppercase()] = copyId
        loanIdsByBook[copyId] = loanId
        val pendingBook = Book(
            id = copyId,
            catalogId = catalogId,
            title = title,
            author = author,
            coverRes = R.drawable.ic_book_arch,
            coverUrl = coverUrl,
            status = BookStatus.BORROWED
        )
        pendingBorrowBooks[copyId] = pendingBook
        if (books.none { it.id == copyId }) {
            books.add(pendingBook)
        }
        val pendingLoan = ActiveLoan(
            id = loanId,
            bookId = copyId,
            counterpartyName = lenderName,
            role = LoanRole.BORROWER,
            status = LoanStatus.PENDING,
            dueAt = dueAtMillis?.let(Instant::ofEpochMilli),
            acceptedAt = null,
            returnRequestedAt = null,
            returnedAt = null
        )
        pendingBorrowLoans[copyId] = pendingLoan
        activeLoans.removeAll { it.bookId == copyId }
        activeLoans.add(pendingLoan)
        return copyId
    }

    suspend fun confirmBorrow(bookId: String, lenderName: String, dueDate: String) {
        val loanId = requireNotNull(loanIdsByBook[bookId] ?: activeLoanFor(bookId)?.id) {
            "Resolve a valid lending code first."
        }
        callFunction("acceptLoanInvite", mapOf("loanId" to loanId))
        pendingBorrowLoans.remove(bookId)
        pendingBorrowBooks.remove(bookId)
    }

    suspend fun confirmReturn(bookId: String) {
        val activeLoan = activeLoanFor(bookId)
        val loanId = requireNotNull(activeLoan?.id ?: loanIdsByBook[bookId]) {
            "No active loan was found."
        }
        if (activeLoan?.status == LoanStatus.RETURN_REQUESTED && activeLoan.returnRequestedById == currentUid) {
            error("Waiting for the other participant to confirm the return.")
        }
        val function = if (activeLoan?.status == LoanStatus.RETURN_REQUESTED) "confirmReturn" else "requestReturn"
        callFunction(function, mapOf("loanId" to loanId))
    }

    suspend fun sendReminder(bookId: String) {
        val loanId = requireNotNull(activeLoanFor(bookId)?.id) { "No active loan was found." }
        callFunction("sendLoanReminder", mapOf("loanId" to loanId))
    }

    fun markNotificationRead(id: String) {
        val uid = currentUid ?: return
        val index = notifications.indexOfFirst { it.id == id }
        if (index >= 0) {
            notifications[index] = notifications[index].copy(isUnread = false)
        }
        firestore.collection("notifications").document(id).update(
            mapOf(
                "isRead" to true,
                "readAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
    }

    val unreadNotificationCount get() = notifications.count { it.isUnread }

    suspend fun addToWishlist(catalogId: String) {
        val uid = requireNotNull(currentUid) { "Sign in before updating your wishlist." }
        firestore.collection("users").document(uid).collection("wishlist").document(catalogId).set(
            mapOf("addedAt" to FieldValue.serverTimestamp())
        ).await()
    }

    suspend fun removeFromWishlist(catalogId: String) {
        val uid = requireNotNull(currentUid) { "Sign in before updating your wishlist." }
        firestore.collection("users").document(uid).collection("wishlist").document(catalogId).delete().await()
    }

    fun isOnWishlist(catalogId: String): Boolean = wishlistCatalogIds.contains(catalogId)

    /**
     * People this user is currently lending to, most recent first. Used to offer
     * suggestion chips so the same name is not retyped - and misspelled - every loan.
     */
    val recentBorrowerNames: List<String>
        get() = lenderLoans.values
            // Every loan this user has ever made, not just the open ones - the people you
            // lend to most are precisely the ones whose loans have already been returned.
            .sortedByDescending { loan ->
                (loan["acceptedAt"] as? Timestamp)?.toDate()?.time
                    ?: (loan["createdAt"] as? Timestamp)?.toDate()?.time
                    ?: 0L
            }
            .mapNotNull { (it["borrowerDisplayName"] as? String)?.trim()?.takeIf(String::isNotBlank) }
            .filterNot { it.equals("a friend", ignoreCase = true) }
            .distinct()
            .take(4)

    /** Catalogue entries the signed-in user has starred, resolved to displayable books. */
    val wishlistBooks: List<Book>
        get() = wishlistCatalogIds.mapNotNull { id -> maktabaCollection.find { it.catalogId == id } }

    /** Catalogue entries the user neither owns nor has already wishlisted. */
    val discoverableBooks: List<Book>
        get() {
            val ownedCatalogIds = books.map { it.catalogId }.toSet()
            return maktabaCollection.filter {
                it.catalogId !in ownedCatalogIds && it.catalogId !in wishlistCatalogIds
            }
        }

    /**
     * Deletes the user's copy of a book. Catalogue metadata is shared between users and
     * server-owned, so this removes the shelf entry only - it never edits `catalogBooks`.
     */
    /** True while any loan or un-redeemed invite still references this copy. */
    fun hasOpenLoanActivity(bookId: String): Boolean =
        activeLoanFor(bookId) != null ||
            pendingInvites.values.any {
                it.copyId == bookId &&
                    it.status == "pending" &&
                    it.expiresAt.isAfter(Instant.now())
            }

    suspend fun removeBook(bookId: String) {
        requireNotNull(currentUid) { "Sign in before changing your library." }
        callFunction("removeBookFromLibrary", mapOf("copyId" to bookId))
    }

    private fun listenToCatalog() {
        listeners += firestore.collection("catalogBooks").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Catalog listener failed", error)
                return@addSnapshotListener
            }
            snapshot ?: return@addSnapshotListener
            catalog.clear()
            snapshot.documents.forEach { catalog[it.id] = it.data.orEmpty() }
            Log.d(TAG, "Catalog snapshot: ${catalog.size} books")
            markLoaded(catalog = true)
            rebuildMaktabaCollection()
            rebuildBooksAndLoans()
        }
    }

    private fun listenToCopies(uid: String) {
        listeners += firestore.collection("userBooks").whereEqualTo("ownerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "User books listener failed", error)
                    return@addSnapshotListener
                }
                snapshot ?: return@addSnapshotListener
                copies.clear()
                snapshot.documents.forEach { copies[it.id] = it.data.orEmpty() }
                Log.d(TAG, "User books snapshot: ${copies.size} copies for $uid")
                markLoaded(copies = true)
                rebuildBooksAndLoans()
            }
    }

    private fun listenToLoans(
        uid: String,
        participantField: String,
        target: MutableMap<String, Map<String, Any?>>
    ) {
        listeners += firestore.collection("loans").whereEqualTo(participantField, uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "$participantField loans listener failed", error)
                    return@addSnapshotListener
                }
                snapshot ?: return@addSnapshotListener
                target.clear()
                snapshot.documents.forEach { target[it.id] = it.data.orEmpty() }
                markLoansLoaded(participantField)
                loans.clear()
                loans.putAll(lenderLoans)
                loans.putAll(borrowerLoans)
                rebuildBooksAndLoans()
            }
    }

    private fun listenToNotifications(uid: String) {
        listeners += firestore.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Notifications listener failed", error)
                    return@addSnapshotListener
                }
                snapshot ?: return@addSnapshotListener
                notifications.clear()
                notifications.addAll(snapshot.documents.map { doc ->
                    NotificationItem(
                        id = doc.id,
                        bookId = (doc.get("data") as? Map<*, *>)?.get("copyId") as? String
                            ?: doc.getString("catalogBookId").orEmpty(),
                        catalogBookId = doc.getString("catalogBookId").orEmpty(),
                        title = doc.getString("title").orEmpty(),
                        message = doc.getString("body").orEmpty(),
                        isUnread = doc.getBoolean("isRead") != true,
                        type = doc.getString("type").orEmpty(),
                        loanId = doc.getString("loanId")
                    )
                })
            }
    }

    private fun listenToWishlist(uid: String) {
        listeners += firestore.collection("users").document(uid).collection("wishlist")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Wishlist listener failed", error)
                    return@addSnapshotListener
                }
                snapshot ?: return@addSnapshotListener
                wishlistCatalogIds.clear()
                wishlistCatalogIds.addAll(snapshot.documents.map { it.id })
            }
    }

    private fun rebuildBooksAndLoans() {
        val uid = currentUid ?: return
        val activeStatuses = setOf("active", "return_requested")
        val active = loans.filterValues { it["status"] in activeStatuses }
        val rebuiltBooks = copies.mapNotNull { (copyId, copy) ->
            val catalogId = copy["catalogBookId"] as? String ?: return@mapNotNull null
            val details = catalog[catalogId].orEmpty()
            val loan = active.values.firstOrNull { it["copyId"] == copyId && it["lenderUid"] == uid }
            bookFromData(
                copyId = copyId,
                catalogId = catalogId,
                details = details,
                status = if (loan == null) BookStatus.OWNED else BookStatus.LENT_OUT,
                ownerId = uid
            )
        }.toMutableList()
        active.values.filter { it["borrowerUid"] == uid }.forEach { loan ->
            val copyId = loan["copyId"] as? String ?: return@forEach
            if (rebuiltBooks.none { it.id == copyId }) {
                val catalogId = loan["catalogBookId"] as? String ?: return@forEach
                rebuiltBooks += bookFromData(copyId, catalogId, catalog[catalogId].orEmpty(), BookStatus.BORROWED)
            }
        }
        pendingBorrowBooks.values.forEach { pending ->
            if (rebuiltBooks.none { it.id == pending.id }) rebuiltBooks += pending
        }
        if (catalog.isNotEmpty() || copies.isNotEmpty()) {
            books.clear()
            books.addAll(rebuiltBooks)
            Log.d(TAG, "Rebuilt visible library: ${books.size} books")
        }

        activeLoans.clear()
        activeLoans.addAll(active.mapNotNull { (loanId, loan) ->
            val copyId = loan["copyId"] as? String ?: return@mapNotNull null
            val isLender = loan["lenderUid"] == uid
            val counterpartyName = if (isLender) {
                loan["borrowerDisplayName"] as? String ?: "Borrower"
            } else {
                loan["lenderDisplayName"] as? String ?: "Lender"
            }
            val dueAt = loan["dueDate"] as? Timestamp
            ActiveLoan(
                id = loanId,
                bookId = copyId,
                counterpartyName = counterpartyName,
                role = if (isLender) LoanRole.LENDER else LoanRole.BORROWER,
                status = LoanStatus.from(loan["status"] as? String),
                dueAt = dueAt?.toDate()?.toInstant(),
                acceptedAt = (loan["acceptedAt"] as? Timestamp)?.toDate()?.toInstant(),
                returnRequestedAt = (loan["returnRequestedAt"] as? Timestamp)?.toDate()?.toInstant(),
                returnedAt = (loan["returnedAt"] as? Timestamp)?.toDate()?.toInstant(),
                returnRequestedById = loan["returnRequestedByUid"] as? String,
                returnRequestedByCurrentUser = loan["returnRequestedByUid"] == uid
            )
        })
        pendingBorrowLoans.values.forEach { pending ->
            if (activeLoans.none { it.bookId == pending.bookId }) activeLoans.add(pending)
        }
    }

    private fun rebuildMaktabaCollection() {
        maktabaCollection.clear()
        maktabaCollection.addAll(
            catalog.map { (catalogId, details) ->
                bookFromData(
                    copyId = catalogId,
                    catalogId = catalogId,
                    details = details,
                    status = BookStatus.OWNED
                )
            }.sortedBy { it.title.lowercase() }
        )
    }

    private fun bookFromData(
        copyId: String,
        catalogId: String,
        details: Map<String, Any?>,
        status: BookStatus,
        ownerId: String = ""
    ): Book {
        val authors = details["authors"] as? List<*>
        return Book(
            id = copyId,
            catalogId = catalogId,
            title = details["title"] as? String ?: "Untitled",
            author = authors?.filterIsInstance<String>()?.joinToString(", ")
                ?.takeIf { it.isNotBlank() } ?: "Unknown author",
            coverRes = R.drawable.ic_book_arch,
            coverUrl = details["coverUrl"] as? String ?: "",
            ownerId = ownerId,
            status = status,
            genre = (details["genres"] as? List<*>)?.firstOrNull() as? String ?: "",
            published = (details["publishedYear"] as? Number)?.toInt()?.toString() ?: "",
            pages = (details["pageCount"] as? Number)?.toInt() ?: 0,
            description = details["description"] as? String ?: "",
            binding = details["binding"] as? String ?: "",
            format = BookFormat.from(details["format"] as? String, details["binding"] as? String),
            physicalEditionIsbn13 = details["physicalEditionIsbn13"] as? String ?: ""
        )
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun callFunction(name: String, data: Map<String, Any?>): Map<String, Any?> {
        return functions.getHttpsCallable(name).call(data).await().getData() as? Map<String, Any?>
            ?: error("Firebase function $name returned an invalid response.")
    }

    suspend fun registerDeviceToken(uid: String, token: String) {
        val deviceId = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        firestore.collection("users").document(uid).collection("fcmTokens").document(deviceId).set(
            mapOf(
                "token" to token,
                "platform" to "android",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "appVersion" to BuildConfig.VERSION_NAME
            )
        ).await()
    }

    suspend fun unregisterDeviceToken(uid: String, token: String) {
        val deviceId = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        firestore.collection("users").document(uid).collection("fcmTokens").document(deviceId)
            .delete()
            .await()
    }

    private const val TAG = "LibraryRepository"
}
