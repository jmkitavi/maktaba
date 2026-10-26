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
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.Instant
import java.io.IOException

enum class BookStatus { OWNED, LENT_OUT, BORROWED }

enum class BookJacketTone { LINEN, OLIVE }

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
    val physicalEditionIsbn13: String = "",
    val jacketTone: BookJacketTone = BookJacketTone.LINEN,
    val isbn13: String = "",
    val isbn10: String = "",
    val publisher: String = ""
)

data class NotificationItem(
    val id: String,
    val bookId: String,
    val catalogBookId: String = "",
    val title: String,
    val message: String,
    val isUnread: Boolean = true,
    val type: String = "",
    val loanId: String? = null,
    val createdAt: Instant? = null
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

    var hasLoadedNotifications by mutableStateOf(false)
        private set
    var hasLoadedWishlist by mutableStateOf(false)
        private set
    private var catalogError by mutableStateOf<String?>(null)
    private var copiesError by mutableStateOf<String?>(null)
    private var lenderLoansError by mutableStateOf<String?>(null)
    private var borrowerLoansError by mutableStateOf<String?>(null)
    val libraryError: String? get() = catalogError ?: copiesError
    val loansError: String? get() = lenderLoansError ?: borrowerLoansError
    var notificationsError by mutableStateOf<String?>(null)
        private set
    var wishlistError by mutableStateOf<String?>(null)
        private set
    var deviceTokenError by mutableStateOf<String?>(null)
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
    private val listeners = mutableMapOf<String, ListenerRegistration>()
    private val catalog = mutableMapOf<String, Map<String, Any?>>()
    private val copies = mutableMapOf<String, Map<String, Any?>>()
    private val loans = mutableMapOf<String, Map<String, Any?>>()
    private val lenderLoans = mutableMapOf<String, Map<String, Any?>>()
    private val borrowerLoans = mutableMapOf<String, Map<String, Any?>>()
    private val pendingBorrowLoans = mutableMapOf<String, ActiveLoan>()
    private val pendingBorrowBooks = mutableMapOf<String, Book>()
    private var currentUid: String? = null
    private var sessionGeneration = 0L
    private var tokenJob: Job? = null

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
            val generation = sessionGeneration
            tokenJob = scope.launch {
                try {
                    val token = FirebaseMessaging.getInstance().token.await()
                    if (generation == sessionGeneration) registerDeviceToken(uid, token)
                } catch (error: FirebaseException) {
                    notificationRegistrationFailed(error, generation)
                } catch (error: IOException) {
                    notificationRegistrationFailed(error, generation)
                }
            }
        }
    }

    private fun notificationRegistrationFailed(error: Exception, generation: Long) {
        if (generation == sessionGeneration) {
            deviceTokenError = "Notification registration failed. Try again in Settings."
            Log.e(TAG, "Device notification registration failed", error)
        }
    }

    fun stop() {
        sessionGeneration++
        currentUid = null
        tokenJob?.cancel()
        tokenJob = null
        listeners.values.forEach { it.remove() }
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
        hasLoadedNotifications = false
        hasLoadedWishlist = false
        catalogError = null
        copiesError = null
        lenderLoansError = null
        borrowerLoansError = null
        notificationsError = null
        wishlistError = null
        deviceTokenError = null
    }

    fun retryLoads() {
        val uid = currentUid ?: return
        if (catalogError != null) {
            catalogError = null
            listenToCatalog()
        }
        if (copiesError != null) {
            copiesError = null
            listenToCopies(uid)
        }
        if (lenderLoansError != null) {
            lenderLoansError = null
            listenToLoans(uid, "lenderUid", lenderLoans)
        }
        if (borrowerLoansError != null) {
            borrowerLoansError = null
            listenToLoans(uid, "borrowerUid", borrowerLoans)
        }
        if (notificationsError != null) {
            notificationsError = null
            listenToNotifications(uid)
        }
        if (wishlistError != null) {
            wishlistError = null
            listenToWishlist(uid)
        }
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
        books.find { it.id == id } ?: pendingBorrowBooks[id] ?:
            maktabaCollection.find { it.catalogId == id }

    fun canonicalBookForCopy(copyId: String): Book? {
        val copy = books.find { it.id == copyId } ?: return null
        return maktabaCollection.find { it.catalogId == copy.catalogId }
            ?.copy(id = copy.id, status = copy.status)
    }

    fun activeLoanFor(bookId: String): ActiveLoan? =
        activeLoans.find { it.bookId == bookId } ?: pendingBorrowLoans[bookId]

    fun loanFor(bookId: String): ActiveLoan? {
        activeLoanFor(bookId)?.let { return it }
        val uid = currentUid ?: return null
        val entry = loans.entries.filter { it.value["copyId"] == bookId }
            .maxByOrNull {
                (it.value["acceptedAt"] as? Timestamp)?.toDate()?.time
                    ?: (it.value["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L
            } ?: return null
        return loanFromData(entry.key, entry.value, uid)
    }

    fun loanBookById(bookId: String): Book? {
        bookById(bookId)?.let { return it }
        val loan = loanFor(bookId) ?: return null
        val catalogId = loans[loan.id]?.get("catalogBookId") as? String ?: return null
        val details = catalog[catalogId] ?: return null
        return bookFromData(
            bookId, catalogId, details,
            if (loan.isLender) BookStatus.OWNED else BookStatus.BORROWED
        )
    }

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
            pageCount = (metadata["pageCount"] as? Number)?.toInt() ?: 0,
            genres = (metadata["genres"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            description = metadata["description"] as? String ?: "",
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

    suspend fun updateOwnedBook(bookId: String, draft: OwnedBookDraft) {
        val uid = requireNotNull(currentUid) { "Sign in before editing a book." }
        val generation = sessionGeneration
        val fields = draft.toFields()
        val ref = firestore.collection("userBooks").document(bookId)
        firestore.runTransaction { transaction ->
            check(generation == sessionGeneration && currentUid == uid) { "Your session changed. Sign in again." }
            val copy = transaction.get(ref)
            require(copy.exists() && copy.getString("ownerId") == uid) { "Only your own books can be edited." }
            transaction.update(ref, mapOf("privateDetails" to fields, "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
        check(generation == sessionGeneration && currentUid == uid) { "Your session changed. Sign in again." }
    }

    suspend fun startLending(bookId: String, borrowerName: String, dueAtMillis: Long): PendingLoanInvite {
        val generation = sessionGeneration
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
        check(generation == sessionGeneration && currentUid != null) { "Your session changed. Sign in again." }
        val invite = pendingLoanInviteFromCreateResult(bookId, result)
        lendingCodes[invite.code.value] = bookId
        loanIdsByBook[bookId] = invite.id
        pendingInvites[invite.id] = invite
        return invite
    }

    suspend fun loadPendingInvite(inviteId: String): PendingLoanInvite {
        val generation = sessionGeneration
        val snapshot = firestore.collection("loanInvites").document(inviteId).get(Source.SERVER).await()
        check(generation == sessionGeneration && currentUid != null) { "Your session changed. Sign in again." }
        if (!snapshot.exists()) {
            forgetPendingInvite(inviteId)
            throw InviteUnavailableException("not_found", "This lending invitation is no longer available.")
        }
        val status = snapshot.getString("status").orEmpty()
        if (status != "pending") {
            forgetPendingInvite(inviteId)
            throw InviteUnavailableException(status, "This lending invitation is ${status.ifBlank { "unavailable" }}.")
        }
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
        if (!invite.expiresAt.isAfter(Instant.now()) || invite.dueAt?.isAfter(Instant.now()) == false) {
            forgetPendingInvite(inviteId)
            throw InviteUnavailableException("expired", "This lending invitation has expired.")
        }
        pendingInvites[inviteId] = invite
        lendingCodes[code.value] = invite.copyId
        loanIdsByBook[invite.copyId] = inviteId
        return invite
    }

    private fun forgetPendingInvite(inviteId: String) {
        val copyId = pendingInvites.remove(inviteId)?.copyId ?: return
        if (loanIdsByBook[copyId] == inviteId) loanIdsByBook.remove(copyId)
        lendingCodes.entries.filter { it.value == copyId }.map { it.key }.forEach(lendingCodes::remove)
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
        val generation = sessionGeneration
        val result = callFunction("resolveLoanInvite", mapOf("inviteCode" to code.trim().uppercase()))
        check(generation == sessionGeneration && currentUid != null) { "Your session changed. Sign in again." }
        val canAccept = result["canAccept"] as? Boolean
            ?: error("This invitation is missing its acceptance status.")
        val copyId = result["copyId"] as? String ?: error("The invitation is missing its book copy.")
        val loanId = result["loanId"] as? String ?: error("The invitation is missing its loan.")
        val invitationStatus = result["status"] as? String
        val acceptedSnapshot = if (invitationStatus == "accepted") {
            firestore.collection("loans").document(loanId).get(Source.SERVER).await().also {
                check(generation == sessionGeneration && currentUid != null) { "Your session changed. Sign in again." }
                if (!it.exists()) throw InviteUnavailableException("not_found", "This loan is no longer available.")
                check(it.getString("copyId") == copyId) { "This invitation does not match its loan." }
            }
        } else null
        val status = invitationLoanStatus(invitationStatus, canAccept, acceptedSnapshot?.getString("status"))
        val acceptedLoan = acceptedSnapshot?.data?.let { loanFromData(loanId, it, requireNotNull(currentUid)) }
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
            status = if (acceptedLoan?.isLender == true) {
                if (status == LoanStatus.RETURNED) BookStatus.OWNED else BookStatus.LENT_OUT
            } else BookStatus.BORROWED,
            format = BookFormat.from(
                result["format"] as? String ?: catalog[catalogId]?.get("format") as? String,
                result["binding"] as? String ?: catalog[catalogId]?.get("binding") as? String
            )
        )
        pendingBorrowBooks[copyId] = pendingBook
        val pendingLoan = acceptedLoan ?: ActiveLoan(
            id = loanId,
            bookId = copyId,
            counterpartyName = lenderName,
            role = LoanRole.BORROWER,
            status = status,
            dueAt = dueAtMillis?.let(Instant::ofEpochMilli),
            acceptedAt = (result["acceptedAtMillis"] as? Number)?.toLong()?.let(Instant::ofEpochMilli),
            returnRequestedAt = (result["returnRequestedAtMillis"] as? Number)?.toLong()?.let(Instant::ofEpochMilli),
            returnedAt = (result["returnedAtMillis"] as? Number)?.toLong()?.let(Instant::ofEpochMilli),
            counterpartyUid = result["lenderUid"] as? String ?: ""
        )
        pendingBorrowLoans[copyId] = pendingLoan
        return copyId
    }

    suspend fun confirmBorrow(bookId: String, lenderName: String, dueDate: String) {
        val generation = sessionGeneration
        val loanId = requireNotNull(loanIdsByBook[bookId] ?: activeLoanFor(bookId)?.id) {
            "Resolve a valid lending code first."
        }
        callFunction("acceptLoanInvite", mapOf("loanId" to loanId))
        check(generation == sessionGeneration && currentUid != null) { "Your session changed. Sign in again." }
        pendingBorrowLoans[bookId]?.let {
            pendingBorrowLoans[bookId] = it.copy(status = LoanStatus.ACTIVE)
        }
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

    suspend fun sendReminder(bookId: String): Instant? {
        val loanId = requireNotNull(activeLoanFor(bookId)?.id) { "No active loan was found." }
        val result = callFunction("sendLoanReminder", mapOf("loanId" to loanId))
        return (result["nextReminderAllowedAtMillis"] as? Number)?.toLong()?.let(Instant::ofEpochMilli)
    }

    suspend fun markNotificationRead(id: String) {
        requireNotNull(currentUid) { "Sign in before updating notifications." }
        val generation = sessionGeneration
        firestore.collection("notifications").document(id).update(
            mapOf(
                "isRead" to true,
                "readAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        if (generation != sessionGeneration) return
        val index = notifications.indexOfFirst { it.id == id }
        if (index >= 0) notifications[index] = notifications[index].copy(isUnread = false)
    }

    val unreadNotificationCount get() = notifications.count { it.isUnread }

    suspend fun retryDeviceNotifications() {
        val uid = requireNotNull(currentUid) { "Sign in before enabling notifications." }
        check(!BuildConfig.USE_FIREBASE_EMULATORS) { "Push delivery is unavailable in the local emulator environment." }
        val generation = sessionGeneration
        val token = FirebaseMessaging.getInstance().token.await()
        check(generation == sessionGeneration) { "Your session changed. Sign in again." }
        registerDeviceToken(uid, token)
        if (generation == sessionGeneration) deviceTokenError = null
    }

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
                    it.expiresAt.isAfter(Instant.now()) &&
                    it.dueAt?.isAfter(Instant.now()) == true
            }

    suspend fun removeBook(bookId: String) {
        requireNotNull(currentUid) { "Sign in before changing your library." }
        callFunction("removeBookFromLibrary", mapOf("copyId" to bookId))
    }

    private fun listenToCatalog() {
        val generation = sessionGeneration
        listeners.remove("catalog")?.remove()
        listeners["catalog"] = firestore.collection("catalogBooks").addSnapshotListener { snapshot, error ->
            if (generation != sessionGeneration) return@addSnapshotListener
            if (error != null) {
                Log.e(TAG, "Catalog listener failed", error)
                catalogError = "Your books couldn’t be loaded. Check your connection and try again."
                return@addSnapshotListener
            }
            snapshot ?: return@addSnapshotListener
            catalogError = null
            catalog.clear()
            snapshot.documents.forEach { catalog[it.id] = it.data.orEmpty() }
            Log.d(TAG, "Catalog snapshot: ${catalog.size} books")
            markLoaded(catalog = true)
            rebuildMaktabaCollection()
            rebuildBooksAndLoans()
        }
    }

    private fun listenToCopies(uid: String) {
        val generation = sessionGeneration
        listeners.remove("copies")?.remove()
        listeners["copies"] = firestore.collection("userBooks").whereEqualTo("ownerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (generation != sessionGeneration) return@addSnapshotListener
                if (error != null) {
                    Log.e(TAG, "User books listener failed", error)
                    copiesError = "Your library couldn’t be loaded. Check your connection and try again."
                    return@addSnapshotListener
                }
                snapshot ?: return@addSnapshotListener
                copiesError = null
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
        val generation = sessionGeneration
        listeners.remove(participantField)?.remove()
        listeners[participantField] = firestore.collection("loans").whereEqualTo(participantField, uid)
            .addSnapshotListener { snapshot, error ->
                if (generation != sessionGeneration) return@addSnapshotListener
                if (error != null) {
                    Log.e(TAG, "$participantField loans listener failed", error)
                    val message = "Your loans couldn’t be loaded. Check your connection and try again."
                    if (participantField == "lenderUid") lenderLoansError = message else borrowerLoansError = message
                    return@addSnapshotListener
                }
                snapshot ?: return@addSnapshotListener
                if (participantField == "lenderUid") lenderLoansError = null else borrowerLoansError = null
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
        val generation = sessionGeneration
        listeners.remove("notifications")?.remove()
        listeners["notifications"] = firestore.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (generation != sessionGeneration) return@addSnapshotListener
                if (error != null) {
                    Log.e(TAG, "Notifications listener failed", error)
                    notificationsError = "Notifications couldn’t be loaded. Check your connection and try again."
                    return@addSnapshotListener
                }
                snapshot ?: return@addSnapshotListener
                notificationsError = null
                hasLoadedNotifications = true
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
                        loanId = doc.getString("loanId"),
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.toInstant()
                    )
                })
            }
    }

    private fun listenToWishlist(uid: String) {
        val generation = sessionGeneration
        listeners.remove("wishlist")?.remove()
        listeners["wishlist"] = firestore.collection("users").document(uid).collection("wishlist")
            .addSnapshotListener { snapshot, error ->
                if (generation != sessionGeneration) return@addSnapshotListener
                if (error != null) {
                    Log.e(TAG, "Wishlist listener failed", error)
                    wishlistError = "Want to Read couldn’t be loaded. Check your connection and try again."
                    return@addSnapshotListener
                }
                snapshot ?: return@addSnapshotListener
                wishlistError = null
                hasLoadedWishlist = true
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
            val book = bookFromData(
                copyId = copyId,
                catalogId = catalogId,
                details = details,
                status = if (loan == null) BookStatus.OWNED else BookStatus.LENT_OUT,
                ownerId = uid
            )
            applyOwnedBookDetails(book, copy["privateDetails"] as? Map<*, *> ?: emptyMap<String, Any>())
        }.toMutableList()
        active.values.filter { it["borrowerUid"] == uid }.forEach { loan ->
            val copyId = loan["copyId"] as? String ?: return@forEach
            if (rebuiltBooks.none { it.id == copyId }) {
                val catalogId = loan["catalogBookId"] as? String ?: return@forEach
                rebuiltBooks += bookFromData(copyId, catalogId, catalog[catalogId].orEmpty(), BookStatus.BORROWED)
            }
        }
        if (catalogLoaded && copiesLoaded) {
            books.clear()
            books.addAll(rebuiltBooks)
            Log.d(TAG, "Rebuilt visible library: ${books.size} books")
        }

        activeLoans.clear()
        activeLoans.addAll(active.mapNotNull { (loanId, loan) ->
            loanFromData(loanId, loan, uid)
        })
        val authoritativeCopyIds = activeLoans.map { it.bookId }.toSet()
        authoritativeCopyIds.forEach {
            pendingBorrowLoans.remove(it)
            pendingBorrowBooks.remove(it)
        }
    }

    private fun loanFromData(loanId: String, loan: Map<String, Any?>, uid: String): ActiveLoan? {
        val copyId = loan["copyId"] as? String ?: return null
        val lender = loan["lenderUid"] == uid
        return ActiveLoan(
            id = loanId, bookId = copyId,
            counterpartyName = (loan[if (lender) "borrowerDisplayName" else "lenderDisplayName"] as? String)
                ?: if (lender) "Borrower" else "Lender",
            role = if (lender) LoanRole.LENDER else LoanRole.BORROWER,
            status = LoanStatus.from(loan["status"] as? String),
            dueAt = (loan["dueDate"] as? Timestamp)?.toDate()?.toInstant(),
            acceptedAt = (loan["acceptedAt"] as? Timestamp)?.toDate()?.toInstant(),
            returnRequestedAt = (loan["returnRequestedAt"] as? Timestamp)?.toDate()?.toInstant(),
            returnedAt = (loan["returnedAt"] as? Timestamp)?.toDate()?.toInstant(),
            returnRequestedById = loan["returnRequestedByUid"] as? String,
            returnRequestedByCurrentUser = loan["returnRequestedByUid"] == uid,
            counterpartyUid = loan[if (lender) "borrowerUid" else "lenderUid"] as? String ?: "",
            nextReminderAllowedAt = (loan["lastManualReminderAt"] as? Timestamp)?.toDate()?.toInstant()
                ?.plusSeconds(6 * 60 * 60)
        )
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
            published = (details["publishedYear"] as? Number)?.toInt()?.toString()
                ?: (details["publishedDate"] as? String).orEmpty(),
            pages = (details["pageCount"] as? Number)?.toInt() ?: 0,
            description = details["description"] as? String ?: "",
            binding = details["binding"] as? String ?: "",
            isbn13 = details["isbn13"] as? String ?: "",
            isbn10 = details["isbn10"] as? String ?: "",
            publisher = details["publisher"] as? String ?: "",
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
