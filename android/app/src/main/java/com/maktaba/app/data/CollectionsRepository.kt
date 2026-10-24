package com.maktaba.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object CollectionsRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val functions by lazy { FirebaseFunctions.getInstance() }
    private var uid: String? = null
    private var session = 0L
    private val listeners = mutableListOf<ListenerRegistration>()
    private val publicFeeds = mutableSetOf<PublicCollectionFeed<*>>()
    val collections = mutableStateListOf<CustomCollection>()
    var collectionsState: LoadState<Unit> by mutableStateOf(LoadState.Loading)
        private set
    var sharingSettings: LoadState<LibrarySharingSettings> by mutableStateOf(LoadState.Loading)
        private set

    fun start(uid: String) {
        if (this.uid == uid && listeners.isNotEmpty()) return
        stop()
        this.uid = uid
        val active = session
        listeners += db.collection("users").document(uid)
            .addSnapshotListener { value, error ->
                if (active != session) return@addSnapshotListener
                sharingSettings = when {
                    error != null -> LoadState.Error(error)
                    value == null -> LoadState.Loading
                    !value.exists() -> LoadState.NotFound
                    else -> runCatching { settings(value.data.orEmpty()) }.fold(
                        { LoadState.Ready(it) }, { LoadState.Error(it) })
                }
            }
        listenCollections(uid, active)
    }

    private fun listenCollections(owner: String, active: Long) {
        listeners += db.collection("users").document(owner).collection("collections")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { value, error ->
                if (active != session) return@addSnapshotListener
                if (error != null) {
                    collections.clear()
                    collectionsState = LoadState.Error(error)
                } else if (value != null) {
                    runCatching { value.documents.map(::customCollection) }.fold({
                        collections.clear()
                        collections.addAll(it)
                        collectionsState = LoadState.Ready(Unit)
                    }, {
                        collections.clear()
                        collectionsState = LoadState.Error(it)
                    })
                }
            }
    }

    fun stop() {
        session++
        uid = null
        listeners.forEach { it.remove() }
        listeners.clear()
        publicFeeds.toList().forEach { it.close() }
        publicFeeds.clear()
        collections.clear()
        collectionsState = LoadState.Loading
        sharingSettings = LoadState.Loading
    }

    fun retryCollections() {
        val owner = uid ?: return
        startAfterRetry(owner)
    }

    private fun startAfterRetry(owner: String) {
        listeners.forEach { it.remove() }
        listeners.clear()
        uid = null
        start(owner)
    }

    suspend fun refreshSharingSettings() = withContext(Dispatchers.Main.immediate) {
        val owner = requireSession()
        val active = session
        sharingSettings = LoadState.Loading
        try {
            val value = db.collection("users").document(owner).get(Source.SERVER).await()
            checkSession(active)
            sharingSettings = if (value.exists()) LoadState.Ready(settings(value.data.orEmpty()))
                else LoadState.NotFound
        } catch (error: Exception) {
            if (active == session) sharingSettings = LoadState.Error(error)
            throw error
        }
    }

    suspend fun setLibrarySharing(enabled: Boolean, name: String) = withContext(Dispatchers.Main.immediate) {
        val result = call("setLibrarySharing", mapOf(
            "libraryShared" to enabled, "libraryName" to CollectionValidation.libraryName(name)
        ))
        sharingSettings = LoadState.Ready(settings(result["settings"] as? Map<*, *>
            ?: error("The server did not return sharing settings.")))
    }

    suspend fun setTitleHidden(catalogId: String, hidden: Boolean) = withContext(Dispatchers.Main.immediate) {
        CollectionValidation.catalogIds(listOf(catalogId))
        val current = (sharingSettings as? LoadState.Ready)?.value
        require(!hidden || current == null || catalogId in current.hiddenCatalogIds ||
            current.hiddenCatalogIds.size < 200) { "You can hide up to 200 titles." }
        val result = call("setTitleVisibility", mapOf("catalogBookId" to catalogId, "hidden" to hidden))
        sharingSettings = LoadState.Ready(settings(result["settings"] as? Map<*, *>
            ?: error("The server did not return sharing settings.")))
    }

    suspend fun createCollection(name: String, requestId: String): String {
        require(requestId.isNotBlank()) { "A retry identifier is required." }
        return call("createCollection", mapOf(
            "name" to CollectionValidation.name(name), "requestId" to requestId
        ))["collectionId"] as? String ?: error("The server did not return a collection.")
    }

    suspend fun updateCollection(
        collectionId: String,
        name: String? = null,
        catalogIds: Collection<String>? = null,
        shared: Boolean? = null
    ) {
        val payload = mutableMapOf<String, Any>("collectionId" to collectionId)
        name?.let { payload["name"] = CollectionValidation.name(it) }
        catalogIds?.let { payload["catalogIds"] = CollectionValidation.catalogIds(it) }
        shared?.let { payload["shared"] = it }
        call("updateCollection", payload)
    }

    suspend fun deleteCollection(collectionId: String) {
        val result = call("deleteCollection", mapOf("collectionId" to collectionId))
        check(result["deleted"] == true) { "Deletion was not confirmed. Please retry." }
    }

    private suspend fun call(name: String, payload: Map<String, Any>): Map<*, *> {
        requireSession()
        val active = session
        val result = functions.getHttpsCallable(name).call(payload).await()
        checkSession(active)
        return result.getData() as? Map<*, *> ?: error("The server returned an invalid response.")
    }

    private fun requireSession(): String = checkNotNull(uid) { "Sign in to continue." }
    private fun checkSession(active: Long) = check(active == session && uid != null) {
        "Your session changed. Please reopen this screen."
    }

    fun directory(search: String = ""): PublicCollectionFeed<List<SharedLibrarySummary>> {
        requireSession()
        val normalized = CollectionValidation.search(search)
        var query: Query = db.collection("sharedLibraries").whereEqualTo("published", true)
        if (normalized.isNotEmpty()) query = query.whereArrayContains("searchPrefixes", normalized)
        query = query.orderBy("updatedAt", Query.Direction.DESCENDING).limit(20)
        val feed = newFeed<List<SharedLibrarySummary>>()
        var cursor: DocumentSnapshot? = null
        val baseQuery = query
        feed.load = { append, request ->
            val page = (if (append && cursor != null) baseQuery.startAfter(cursor!!) else baseQuery)
                .get(Source.SERVER).await()
            val mapped = coroutineScope {
                page.documents.map { document ->
                    async {
                        try { directoryPreview(librarySummary(document)) }
                        catch (_: PublicationUnavailable) { null }
                        catch (error: com.google.firebase.firestore.FirebaseFirestoreException) {
                            if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                                null else throw error
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            val previous = if (append) (feed.state as? LoadState.Ready)?.value.orEmpty() else emptyList()
            if (feed.isCurrent(request)) {
                cursor = page.documents.lastOrNull()
                feed.hasMore = page.size() == 20
            }
            (previous + mapped).distinctBy { it.ownerUid }
        }
        feed.watchers += baseQuery.addSnapshotListener(MetadataChanges.INCLUDE) { _, error ->
            if (error != null) feed.fail(error) else feed.refresh()
        }
        feed.refresh()
        return feed
    }

    private suspend fun directoryPreview(summary: SharedLibrarySummary): SharedLibrarySummary {
        val epoch = activeEpoch(summary.ownerUid)
        if (epoch != summary.publicationEpoch) throw PublicationUnavailable()
        val page = db.collection("sharedLibraries").document(summary.ownerUid).collection("books")
            .whereEqualTo("publicationEpoch", epoch)
            .orderBy("addedAt", Query.Direction.DESCENDING).limit(4).get(Source.SERVER).await()
        ensureEpoch(summary.ownerUid, epoch)
        return summary.copy(previewBooks = page.documents.map { collectionBook(it, epoch) })
    }

    fun sharedLibrary(ownerUid: String): PublicCollectionFeed<SharedLibraryPage> {
        val parent = db.collection("sharedLibraries").document(ownerUid)
        val feed = newFeed<SharedLibraryPage>()
        var cursor: DocumentSnapshot? = null
        feed.load = { append, request ->
            val epoch = activeEpoch(ownerUid)
            val summary = librarySummary(parent.get(Source.SERVER).await().active(epoch))
            var query = parent.collection("books").whereEqualTo("publicationEpoch", epoch)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(60)
            if (append && cursor != null) query = query.startAfter(cursor!!)
            val page = query.get(Source.SERVER).await()
            ensureEpoch(ownerUid, epoch)
            val old = if (append) (feed.state as? LoadState.Ready)?.value?.books.orEmpty() else emptyList()
            val books = page.documents.map { sharedLibraryBook(it, epoch) }
            val more = page.size() == 60
            if (feed.isCurrent(request)) {
                cursor = page.documents.lastOrNull()
                feed.hasMore = more
            }
            SharedLibraryPage(summary, (old + books).distinctBy { it.catalogBookId }, more)
        }
        watch(feed, ownerUid, listOf(parent.path))
        return feed
    }

    fun readerProfile(ownerUid: String): PublicCollectionFeed<PublicReaderPage> {
        val parent = db.collection("sharedReaders").document(ownerUid)
        val feed = newFeed<PublicReaderPage>()
        var cursor: DocumentSnapshot? = null
        feed.load = { append, request ->
            val epoch = activeEpoch(ownerUid)
            val reader = reader(parent.get(Source.SERVER).await().active(epoch))
            val library = try {
                librarySummary(db.collection("sharedLibraries").document(ownerUid)
                    .get(Source.SERVER).await().active(epoch))
            } catch (error: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    null else throw error
            } catch (_: PublicationUnavailable) { null }
            var query = parent.collection("collections").whereEqualTo("published", true)
                .whereEqualTo("publicationEpoch", epoch)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(60)
            if (append && cursor != null) query = query.startAfter(cursor!!)
            val page = query.get(Source.SERVER).await()
            ensureEpoch(ownerUid, epoch)
            val old = if (append) (feed.state as? LoadState.Ready)?.value?.collections.orEmpty() else emptyList()
            val more = page.size() == 60
            if (feed.isCurrent(request)) {
                cursor = page.documents.lastOrNull()
                feed.hasMore = more
            }
            PublicReaderPage(reader, library,
                (old + page.documents.map { collectionSummary(it.active(epoch), ownerUid) })
                    .distinctBy { it.id }, more)
        }
        watch(feed, ownerUid, listOf(parent.path, "sharedLibraries/$ownerUid"))
        return feed
    }

    fun sharedCollection(ownerUid: String, collectionId: String): PublicCollectionFeed<SharedCollectionPage> {
        val readerRef = db.collection("sharedReaders").document(ownerUid)
        val parent = readerRef.collection("collections").document(collectionId)
        val feed = newFeed<SharedCollectionPage>()
        var cursor: DocumentSnapshot? = null
        feed.load = { append, request ->
            val epoch = activeEpoch(ownerUid)
            val profile = reader(readerRef.get(Source.SERVER).await().active(epoch))
            val summary = collectionSummary(parent.get(Source.SERVER).await().active(epoch), ownerUid)
            var query = parent.collection("books").whereEqualTo("publicationEpoch", epoch)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(60)
            if (append && cursor != null) query = query.startAfter(cursor!!)
            val page = query.get(Source.SERVER).await()
            ensureEpoch(ownerUid, epoch)
            val old = if (append) (feed.state as? LoadState.Ready)?.value?.books.orEmpty() else emptyList()
            val more = page.size() == 60
            if (feed.isCurrent(request)) {
                cursor = page.documents.lastOrNull()
                feed.hasMore = more
            }
            SharedCollectionPage(summary, profile,
                (old + page.documents.map { collectionBook(it, epoch) })
                    .distinctBy { it.catalogBookId }, more)
        }
        watch(feed, ownerUid, listOf(readerRef.path, parent.path))
        return feed
    }

    private fun <T> newFeed(): PublicCollectionFeed<T> {
        requireSession()
        return PublicCollectionFeed<T> { publicFeeds.remove(it) }.also { publicFeeds += it }
    }

    private fun <T> watch(feed: PublicCollectionFeed<T>, owner: String, paths: List<String>) {
        // Only SERVER reads in load() grant authorization. Cached listeners can revoke, never grant.
        (listOf("publicLibraryState/$owner") + paths).forEach { path ->
            feed.watchers += db.document(path).addSnapshotListener(MetadataChanges.INCLUDE) { _, error ->
                when {
                    error != null -> {
                        // An absent optional whole-library mirror is normal for list-only readers.
                        if (path == "sharedLibraries/$owner" && paths.first() != path) feed.refresh()
                        else feed.fail(error)
                    }
                    else -> feed.refresh()
                }
            }
        }
        feed.refresh()
    }

    private suspend fun activeEpoch(owner: String): Long {
        val gate = db.collection("publicLibraryState").document(owner).get(Source.SERVER).await()
        if (gate.getBoolean("published") != true) throw PublicationUnavailable()
        return gate.getLong("publicationEpoch") ?: throw PublicationUnavailable()
    }

    private suspend fun ensureEpoch(owner: String, epoch: Long) {
        if (activeEpoch(owner) != epoch) throw PublicationUnavailable()
    }

    private fun DocumentSnapshot.active(epoch: Long): DocumentSnapshot {
        if (!exists() || getBoolean("published") != true || getLong("publicationEpoch") != epoch)
            throw PublicationUnavailable()
        return this
    }

    internal fun settings(data: Map<*, *>): LibrarySharingSettings = LibrarySharingSettings(
        data["libraryShared"] as? Boolean ?: false,
        data["libraryName"] as? String ?: "",
        (data["hiddenCatalogIds"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
        (data["publicationEpoch"] as? Number)?.toLong() ?: 0
    )

    private fun customCollection(doc: DocumentSnapshot) = CustomCollection(
        doc.id, doc.text("name"), doc.strings("catalogIds"), doc.getBoolean("shared") == true,
        doc.time("createdAt"), doc.time("updatedAt")
    )

    private fun librarySummary(doc: DocumentSnapshot) = SharedLibrarySummary(
        doc.text("ownerUid"), doc.text("libraryName"), doc.text("ownerDisplayName"),
        doc.getString("ownerPhotoUrl").orEmpty(), doc.count("bookCount"),
        doc.strings("coverUrls"), doc.getLong("publicationEpoch") ?: throw PublicationUnavailable()
    )

    private fun reader(doc: DocumentSnapshot) = PublicReaderProfile(
        doc.text("ownerUid"), doc.text("ownerDisplayName"), doc.getString("ownerPhotoUrl").orEmpty(),
        doc.count("collectionCount"),
        doc.getLong("publicationEpoch") ?: throw PublicationUnavailable()
    )

    private fun collectionSummary(doc: DocumentSnapshot, owner: String) = SharedCollectionSummary(
        doc.id, owner, doc.text("name"), doc.count("bookCount"),
        doc.strings("coverUrls"), doc.getLong("publicationEpoch") ?: throw PublicationUnavailable()
    )

    private fun collectionBook(doc: DocumentSnapshot, epoch: Long): SharedCollectionBook {
        return CollectionWireMapping.collectionBook(doc.data.orEmpty(), epoch)
    }

    private fun sharedLibraryBook(doc: DocumentSnapshot, epoch: Long): SharedLibraryBook {
        val book = collectionBook(doc, epoch)
        val availability = when (doc.getString("availability")) {
            "available" -> SharedAvailability.AVAILABLE
            "on_loan" -> SharedAvailability.ON_LOAN
            else -> error("This book's availability could not be verified.")
        }
        return SharedLibraryBook(book.catalogBookId, book.title, book.author, book.coverUrl,
            book.format, availability, book.addedAt)
    }

    private fun DocumentSnapshot.text(key: String) =
        getString(key)?.takeIf { it.isNotBlank() } ?: error("A shared record is incomplete.")
    private fun DocumentSnapshot.strings(key: String) =
        (get(key) as? List<*>)?.filterIsInstance<String>().orEmpty()
    private fun DocumentSnapshot.time(key: String) = (get(key) as? Timestamp)?.toDate()?.time
    private fun DocumentSnapshot.count(key: String): Int {
        val value = getLong(key) ?: error("A shared count could not be verified.")
        check(value in 0..Int.MAX_VALUE.toLong()) { "A shared count is invalid." }
        return value.toInt()
    }
}

class PublicationUnavailable : IllegalStateException("This content is private, publishing, or no longer shared.")

class PublicCollectionFeed<T> internal constructor(
    private val onClose: (PublicCollectionFeed<T>) -> Unit
) {
    var state: LoadState<T> by mutableStateOf(LoadState.Loading)
        private set
    var hasMore by mutableStateOf(false)
        internal set
    var loadingMore by mutableStateOf(false)
        private set
    internal val watchers = mutableListOf<ListenerRegistration>()
    internal var load: (suspend (Boolean, Long) -> T)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var generation = 0L
    private var closed = false

    fun refresh() = request(false)
    internal fun isCurrent(request: Long) = !closed && request == generation
    fun loadMore() {
        if (hasMore && !loadingMore && state is LoadState.Ready) request(true)
    }

    private fun request(append: Boolean) {
        if (closed) return
        val active = ++generation
        loadingMore = append
        if (!append) state = LoadState.Loading
        scope.launch {
            try {
                val result = checkNotNull(load).invoke(append, active)
                if (!closed && active == generation) state = LoadState.Ready(result)
            } catch (error: Exception) {
                if (!closed && active == generation) {
                    hasMore = false
                    state = if (error is PublicationUnavailable) LoadState.NotFound else LoadState.Error(error)
                }
            } finally {
                if (active == generation) loadingMore = false
            }
        }
    }

    internal fun invalidate() {
        generation++
        loadingMore = false
        hasMore = false
        state = LoadState.Loading
    }

    internal fun fail(error: Throwable) {
        invalidate()
        state = LoadState.Error(error)
    }

    fun close() {
        if (closed) return
        closed = true
        invalidate()
        watchers.forEach { it.remove() }
        watchers.clear()
        scope.cancel()
        onClose(this)
    }
}
