package com.maktaba.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.data.*
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.SansBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun CollectionCreateScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var submittedName by rememberSaveable { mutableStateOf<String?>(null) }
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    fun create() {
        if (busy) return
        busy = true
        error = null
        val retainedName = submittedName ?: name.trim().also { submittedName = it }
        scope.launch {
            try {
                val id = CollectionsRepository.createCollection(retainedName, requestId)
                withContext(Dispatchers.Main.immediate) {
                    navController.popBackStack()
                    navController.collectionRoute("collection_books", id)
                }
            } catch (failure: Exception) { error = failure }
            finally { busy = false }
        }
    }
    CollectionPage(navController, "Create collection") {
        CollectionCreateContent(name, busy, submittedName != null, error, { name = it }, ::create)
    }
}

@Composable
fun CollectionCreateContent(
    name: String,
    busy: Boolean,
    retainedDraft: Boolean,
    error: Throwable?,
    onName: (String) -> Unit,
    onCreate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(1.dp))
        CollectionHeading("Give your list a name.")
        CollectionNote("Start private. Choose titles after creating your collection.")
        CollectionNameField(name, !busy && !retainedDraft, onName)
        CollectionAction(if (busy) "Creating…" else "Create collection",
            !busy && name.trim().length in 1..60, onCreate)
        error?.let { CollectionProblem(it, onCreate) }
        if (retainedDraft && error != null)
            CollectionNote("Retry keeps the same draft so a connection timeout cannot create a duplicate. You can rename the collection after creation.")
        CollectionNote("One catalogue title per list, even if you own multiple copies. Up to 500 titles.",
            Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun CollectionNameField(name: String, enabled: Boolean, onName: (String) -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        CollectionTextField(name, onName, label = "Collection name", limit = 60,
            counter = true, enabled = enabled)
    }
}

@Composable
internal fun OwnCollection(
    collectionId: String,
    content: @Composable (CustomCollection) -> Unit
) {
    CollectionLoad(CollectionsRepository.collectionsState, CollectionsRepository::retryCollections) {
        val collection = CollectionsRepository.collections.find { it.id == collectionId }
        if (collection != null) content(collection)
        else {
            CollectionHeading("Collection unavailable")
            CollectionNote("It may have been deleted, or your collection list is updating.")
            OutlinedButton(onClick = CollectionsRepository::retryCollections) { Text("Retry") }
        }
    }
}

private fun candidates() = collectionCandidates(LibraryRepository.books,
    LibraryRepository.wishlistBooks, LibraryRepository.maktabaCollection)

@Composable
private fun CollectionCatalogueLoad(content: @Composable () -> Unit) {
    when {
        LibraryRepository.libraryError != null -> {
            CollectionNote(LibraryRepository.libraryError.orEmpty())
            OutlinedButton(onClick = LibraryRepository::retryLoads) { Text("Retry titles") }
        }
        !LibraryRepository.hasLoadedLibrary -> {
            CircularProgressIndicator()
            CollectionNote("Loading catalogue titles…")
        }
        else -> content()
    }
}

@Composable
fun CollectionDetailScreen(navController: NavHostController, collectionId: String) {
    CollectionPage(navController, "Collection") {
        OwnCollection(collectionId) { collection ->
            CollectionCatalogueLoad {
                CollectionDetailContent(collection, CollectionsRepository.sharingSettings, candidates(),
                    onBook = { navController.collectionRoute("catalogue_book", it) },
                    onSelect = { navController.collectionRoute("collection_books", collectionId) },
                    onEdit = { navController.collectionRoute("collection_edit", collectionId) },
                    onPrivacy = { navController.collectionRoute("collection_privacy", collectionId) },
                    ownerDisplayName = FirebaseSession.currentUser?.displayName)
            }
        }
    }
}

@Composable
fun CollectionDetailContent(
    collection: CustomCollection,
    settings: LoadState<LibrarySharingSettings>,
    books: List<Book>,
    onBook: (String) -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onPrivacy: () -> Unit,
    ownerDisplayName: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CollectionBadge(if (collection.shared) "Shared · signed-in readers" else "Private · only you", collection.shared)
        CollectionHeading(collection.name)
        val hidden = (settings as? LoadState.Ready)?.value?.hiddenCatalogIds
        Column {
            CollectionNote((if (hidden != null)
                "${collection.catalogIds.size} selected / ${collection.visibleCatalogIds(hidden).size} visible"
                else "${collection.catalogIds.size} selected · Visibility unavailable") +
                ownerDisplayName?.takeIf { it.isNotBlank() }?.let { " · by $it" }.orEmpty())
            CollectionNote("Selected titles stay in your private collection. Visible counts exclude globally hidden titles; a private collection is not published.")
        }
        val selected = collection.catalogIds.mapNotNull { id -> books.find { it.catalogId == id } }
        if (collection.catalogIds.isEmpty()) CollectionNote("No titles selected yet.")
        if (selected.size < collection.catalogIds.size) {
            CollectionNote("${collection.catalogIds.size - selected.size} selected title details are unavailable. Membership is preserved.")
        }
        Box(Modifier.padding(top = 6.dp)) {
            CollectionBookGrid(selected, label = {
                if (hidden != null && it.catalogId in hidden) "Hidden from shared views" else "Catalogue reference"
            }, onBook = onBook, companion = true, labelPills = true)
        }
        CollectionAction("Select books", onClick = onSelect)
        CollectionOutline("Edit collection", onClick = onEdit)
        CollectionOutline("Privacy & sharing", onClick = onPrivacy)
        CollectionNote("Removing a title here only removes it from this collection.", Modifier.padding(top = 4.dp))
    }
}

@Composable
fun CollectionBooksScreen(navController: NavHostController, collectionId: String) {
    CollectionPage(navController, "Select books", scrollable = false) {
        OwnCollection(collectionId) { collection ->
            CollectionCatalogueLoad {
                CollectionBookSelection(collection, CollectionsRepository.sharingSettings, candidates()) { ids ->
                    CollectionsRepository.updateCollection(collectionId, catalogIds = ids)
                    withContext(Dispatchers.Main.immediate) { navController.popBackStack() }
                }
            }
        }
    }
}

@Composable
fun CollectionBookSelection(
    collection: CustomCollection,
    settings: LoadState<LibrarySharingSettings>,
    books: List<Book>,
    onSave: suspend (List<String>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selection by rememberSaveable(collection.id) { mutableStateOf(ArrayList(collection.catalogIds)) }
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val hidden = (settings as? LoadState.Ready)?.value?.hiddenCatalogIds
    fun save() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { onSave(selection.toList()) }
            catch (failure: Exception) { error = failure }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CollectionNote("Choose catalogue references, including borrowed titles. This does not imply ownership. Unchecking removes a title from this list, not your Library. Globally hidden titles stay selected privately but never appear in shared views.")
            Spacer(Modifier.height(16.dp))
            CollectionTextField(query, { query = it }, label = "Search title or author", placeholder = "Search your titles")
            Spacer(Modifier.height(6.dp))
            Text("${selection.size}/500 selected" + if (hidden != null)
                " · ${selection.count { it !in hidden }} visible when shared" else " · Visibility loading",
                Modifier.fillMaxWidth(), textAlign = TextAlign.End, color = MaktabaTheme.colors.inkMuted,
                style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 15.sp))
            val matches = books.filter { query.isBlank() || it.title.contains(query, true) ||
                it.author.contains(query, true) }
            if (matches.isEmpty()) CollectionNote(if (query.isBlank()) "No catalogue titles loaded yet."
                else "No titles match your search.")
            matches.forEach { book ->
                val checked = book.catalogId in selection
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 89.dp).padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        BookCoverImage(book, Modifier.width(40.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(5.dp)),
                            ContentScale.Fit, decorative = true, plainFallback = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(book.title, style = TextStyle(fontFamily = SansBody, fontSize = 12.sp,
                                lineHeight = 17.4.sp, lineHeightStyle = CollectionLineHeight))
                            Text(book.author, color = MaktabaTheme.colors.inkMuted,
                                style = TextStyle(fontFamily = SansBody, fontSize = 10.sp,
                                    lineHeight = 15.sp, lineHeightStyle = CollectionLineHeight))
                            val savedOnly = book.ownerId.isBlank() && book.id == book.catalogId
                            val role = when {
                                book.status == BookStatus.BORROWED -> "Borrowed title · catalogue reference"
                                book.status == BookStatus.LENT_OUT -> "Owned · lent out · catalogue reference"
                                book.format == BookFormat.DIGITAL -> "Digital · catalogue reference"
                                savedOnly -> "Catalogue reference · does not imply ownership"
                                else -> "Owned physical · catalogue reference"
                            }
                            Text(role, color = MaktabaTheme.colors.inkMuted,
                                style = TextStyle(fontFamily = SansBody, fontSize = 10.sp,
                                    lineHeight = 15.sp, lineHeightStyle = CollectionLineHeight))
                            if (hidden != null && book.catalogId in hidden)
                                Text("Hidden from all shared views · stays in private lists", color = MaktabaTheme.colors.inkMuted,
                                    style = TextStyle(fontFamily = SansBody, fontSize = 10.sp,
                                        lineHeight = 15.sp, lineHeightStyle = CollectionLineHeight))
                        }
                        Checkbox(checked, onCheckedChange = { next ->
                            selection = ArrayList(if (next) (selection + book.catalogId).distinct()
                                else selection.filterNot { it == book.catalogId })
                        }, modifier = Modifier.size(24.dp), enabled = !busy && (checked || selection.size < 500))
                    }
                    HorizontalDivider(color = MaktabaTheme.colors.divider)
                }
            }
            val unavailable = selection.filter { id -> books.none { it.catalogId == id } }
            unavailable.forEach { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CollectionNote("Unavailable catalogue reference · selected privately")
                    Checkbox(true, onCheckedChange = { selection = ArrayList(selection - id) }, enabled = !busy)
                }
            }
            error?.let { CollectionProblem(it, ::save) }
        }
        HorizontalDivider(color = MaktabaTheme.colors.divider)
        Spacer(Modifier.height(10.dp))
        CollectionAction(if (busy) "Saving…" else "Save ${selection.size} selected titles", !busy, ::save)
    }
}

@Composable
fun CollectionEditScreen(navController: NavHostController, collectionId: String) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    CollectionPage(navController, if (deleting) "Delete collection" else "Edit collection") {
        OwnCollection(collectionId) { collection ->
            CollectionEditContent(collection,
                onSave = {
                    CollectionsRepository.updateCollection(collectionId, name = it)
                    withContext(Dispatchers.Main.immediate) { navController.popBackStack() }
                },
                onPrivacy = { navController.collectionRoute("collection_privacy", collectionId) },
                onDelete = {
                    CollectionsRepository.deleteCollection(collectionId)
                    withContext(Dispatchers.Main.immediate) {
                        if (!navController.popBackStack("collections", false)) navController.navigate("collections")
                    }
                },
                onDeletingChanged = { deleting = it })
        }
    }
}

@Composable
fun CollectionEditContent(
    collection: CustomCollection,
    onSave: suspend (String) -> Unit,
    onPrivacy: () -> Unit,
    onDelete: suspend () -> Unit,
    onDeletingChanged: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(collection.id) { mutableStateOf(collection.name) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var retry by remember { mutableStateOf<suspend () -> Unit>({}) }
    fun mutate(action: suspend () -> Unit) {
        if (busy) return
        retry = action
        busy = true
        error = null
        scope.launch {
            try { action() }
            catch (failure: Exception) { error = failure }
            finally { busy = false }
        }
    }
    if (deleting) {
        CollectionDeleteContent(collection, busy, error,
            onDelete = { mutate(onDelete) }, onKeep = { deleting = false; onDeletingChanged(false) })
    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(1.dp))
        CollectionHeading("Make it yours.")
        CollectionNote("Changing this name does not rename any book or your library.")
        CollectionNameField(name, !busy) { name = it }
        CollectionAction(if (busy) "Saving…" else "Save name", !busy && name.trim().length in 1..60) {
            mutate { onSave(name) }
        }
        CollectionOutline("Privacy & sharing", onClick = onPrivacy)
        CollectionOutline("Delete collection", enabled = !busy, danger = true) {
            deleting = true; onDeletingChanged(true)
        }
        error?.let { CollectionProblem(it) { mutate(retry) } }
        CollectionNote("One catalogue title per list, even if you own multiple copies. Up to 500 titles.",
            Modifier.padding(top = 4.dp))
    }
}

@Composable
fun CollectionDeleteContent(
    collection: CustomCollection,
    busy: Boolean,
    error: Throwable?,
    onDelete: () -> Unit,
    onKeep: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(1.dp))
        CollectionHeading("Delete this collection?")
        CollectionPanel {
            Text(collection.name, style = TextStyle(fontFamily = SansBody, fontSize = 16.sp,
                lineHeight = 19.2.sp, lineHeightStyle = CollectionLineHeight))
            CollectionNote("${collection.catalogIds.size} titles · ${if (collection.shared) "Shared" else "Private"} collection")
        }
        CollectionNote("Only this list is deleted. Your books, catalogue entries and Want to Read remain unchanged. This cannot be undone.")
        CollectionOutline(if (busy) "Deleting…" else "Delete collection", !busy, danger = true, onClick = onDelete)
        CollectionOutline("Keep collection", !busy, onClick = onKeep)
        error?.let { CollectionProblem(it, onDelete) }
    }
}

@Composable
fun CollectionPrivacyScreen(navController: NavHostController, collectionId: String) {
    val scope = rememberCoroutineScope()
    var preview by rememberSaveable { mutableStateOf(false) }
    CollectionPage(navController, if (preview) "Owner reader preview" else "Privacy & sharing") {
        OwnCollection(collectionId) { collection ->
            CollectionLoad(CollectionsRepository.sharingSettings,
                { scope.launch { runCatching { CollectionsRepository.refreshSharingSettings() } } }) { settings ->
                CollectionPrivacyContent(collection, settings, candidates(),
                    onSave = { CollectionsRepository.updateCollection(collectionId, shared = it) },
                    onHiding = { navController.navigate("library_sharing") },
                    onBook = { navController.collectionRoute("catalogue_book", it) },
                    onPreviewChanged = { preview = it },
                    ownerDisplayName = FirebaseSession.currentUser?.displayName)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionPrivacyContent(
    collection: CustomCollection,
    settings: LibrarySharingSettings,
    books: List<Book>,
    onSave: suspend (Boolean) -> Unit,
    onHiding: () -> Unit,
    onBook: (String) -> Unit,
    initialPreview: Boolean = false,
    onPreviewChanged: (Boolean) -> Unit = {},
    ownerDisplayName: String? = null
) {
    val scope = rememberCoroutineScope()
    var shared by rememberSaveable(collection.id, collection.shared) { mutableStateOf(collection.shared) }
    var preview by rememberSaveable { mutableStateOf(initialPreview) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var saved by remember { mutableStateOf(false) }
    val visibleIds = collection.visibleCatalogIds(settings.hiddenCatalogIds)
    fun save() {
        if (busy) return
        busy = true
        error = null
        saved = false
        scope.launch {
            try { onSave(shared); saved = true }
            catch (failure: Exception) { error = failure }
            finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (preview) {
            CollectionBadge("Reader view · catalogue metadata only", shared = true)
            CollectionHeading(collection.name)
            FlowRow {
                ownerDisplayName?.takeIf { it.isNotBlank() }?.let { CollectionNote("$it · ") }
                CollectionNote("${visibleIds.size} visible titles")
            }
            val previewBooks = visibleIds.mapNotNull { id -> books.find { it.catalogId == id } }
            if (previewBooks.size < visibleIds.size)
                CollectionNote("${visibleIds.size - previewBooks.size} catalogue details are unavailable. Their membership is unchanged.")
            Box(Modifier.padding(top = 6.dp)) {
                CollectionBookGrid(previewBooks, onBook = onBook, companion = true, showLabels = false)
            }
            CollectionNote("A list of catalogue references, not a statement of ownership. No loan information is shown.",
                Modifier.padding(top = 6.dp))
            CollectionOutline("Back to privacy") { preview = false; onPreviewChanged(false) }
        } else {
            Spacer(Modifier.height(1.dp))
            CollectionHeading("Your list. Your choice.")
            CollectionBadge(if (collection.shared) "Shared · signed-in readers" else "Private · only you", collection.shared)
            CollectionPanel {
                CollectionNote("Sharing this collection shows its name, your display name and visible catalogue metadata to signed-in Book Haven readers.")
                CollectionNote("No copy ownership, loan dates, availability or borrower names are shown. This does not change your whole-library sharing setting.")
            }
            Column(Modifier.padding(vertical = 5.dp)) {
                Text("Collection visibility", style = TextStyle(fontFamily = SansBody, fontSize = 12.sp,
                    lineHeight = 14.4.sp, lineHeightStyle = CollectionLineHeight))
                Spacer(Modifier.height(9.dp))
                listOf(false to "Private · only you", true to "Shared · signed-in readers").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 47.dp)
                        .clickable(enabled = !busy) { shared = value; saved = false },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RadioButton(shared == value, onClick = { shared = value; saved = false },
                            modifier = Modifier.size(24.dp), enabled = !busy)
                        Text(label, style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 18.sp))
                    }
                    HorizontalDivider(color = MaktabaTheme.colors.divider)
                }
            }
            CollectionNote("${collection.catalogIds.size} selected / ${visibleIds.size} visible")
            Column {
                Surface(shape = RoundedCornerShape(9.dp), color = MaktabaTheme.colors.successContainer,
                    border = BorderStroke(1.dp, MaktabaTheme.colors.divider)) {
                    Text("Global title hiding always applies to all shared views. Collection sharing cannot override it. Hidden titles remain selected privately but are excluded from reader grids, cover previews and visible counts.",
                        Modifier.padding(13.dp), color = MaktabaTheme.colors.success,
                        style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 20.4.sp,
                            lineHeightStyle = CollectionLineHeight))
                }
                val hiddenCount = collection.catalogIds.size - visibleIds.size
                if (hiddenCount > 0) CollectionNote(
                    "$hiddenCount selected ${if (hiddenCount == 1) "title is" else "titles are"} hidden from every shared view.")
                CollectionNote(if (shared)
                    "Sharing publishes this collection on your reader Profile even if whole-library sharing is off. Only visible titles are published."
                    else "Private lists stay visible only to you. Making this collection private does not change your whole-library sharing setting.")
            }
            CollectionLink("Manage hidden titles", "Applies even when whole-library sharing is off", onHiding)
            CollectionOutline("Review reader preview") { preview = true; onPreviewChanged(true) }
            CollectionAction(if (busy) "Saving…" else if (shared) "Share this collection" else "Keep this collection private",
                !busy, ::save)
            if (saved) CollectionNote("Visibility saved. Public previews may briefly be unavailable while publishing.")
            error?.let { CollectionProblem(it, ::save) }
            CollectionNote("Want to Read is separate and always private.")
        }
    }
}

@Composable
fun AddToCollectionScreen(navController: NavHostController, catalogId: String) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var completed by remember { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var createRequestId by rememberSaveable(catalogId) { mutableStateOf(UUID.randomUUID().toString()) }
    var submittedName by rememberSaveable { mutableStateOf<String?>(null) }
    var createdId by rememberSaveable { mutableStateOf<String?>(null) }
    fun add(collection: CustomCollection) {
        if (busy != null) return
        busy = collection.id
        error = null
        scope.launch {
            try {
                CollectionsRepository.updateCollection(collection.id,
                    catalogIds = collection.catalogIds + catalogId)
                completed = collection.name
            } catch (failure: Exception) { error = failure }
            finally { busy = null }
        }
    }
    CollectionPage(navController, "Add to collection") {
        val book = candidates().find { it.catalogId == catalogId }
        AddToCollectionContent(book?.title, catalogId, CollectionsRepository.collections.toList(),
            CollectionsRepository.collectionsState, busy,
            onRetry = CollectionsRepository::retryCollections, onAdd = ::add, book = book)
        error?.let { CollectionNote(collectionErrorMessage(it)) }
        completed?.let { CollectionNote("Added to $it. Your Library was not changed.") }
        if (creating) {
            CollectionNameField(newName, busy == null && submittedName == null) { newName = it }
            CollectionAction(if (busy == "create") "Creating…" else "Create and add title",
                busy == null && newName.trim().length in 1..60) {
                busy = "create"
                error = null
                val retainedName = submittedName ?: newName.trim().also { submittedName = it }
                scope.launch {
                    try {
                        val id = createdId ?: CollectionsRepository.createCollection(retainedName, createRequestId)
                            .also { createdId = it }
                        val existingIds = CollectionsRepository.collections.find { it.id == id }?.catalogIds.orEmpty()
                        CollectionsRepository.updateCollection(id, catalogIds = existingIds + catalogId)
                        completed = retainedName
                        creating = false
                        newName = ""
                        submittedName = null
                        createdId = null
                        createRequestId = UUID.randomUUID().toString()
                    } catch (failure: Exception) { error = failure }
                    finally { busy = null }
                }

            }
        } else CollectionOutline("New collection", busy == null) { creating = true }
        CollectionOutline("Done") { navController.popBackStack() }
        CollectionNote("Want to Read keeps its separate save action.")
    }
}

@Composable
fun AddToCollectionContent(
    bookTitle: String?,
    catalogId: String,
    collections: List<CustomCollection>,
    state: LoadState<Unit>,
    busyId: String?,
    onRetry: () -> Unit,
    onAdd: (CustomCollection) -> Unit,
    book: Book? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if (book != null) BookCoverImage(book, Modifier.width(40.dp).aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(5.dp)), ContentScale.Fit, decorative = true, plainFallback = true)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(bookTitle ?: "Catalogue title",
                    style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 16.2.sp))
                Text("Catalogue reference · does not imply ownership", color = MaktabaTheme.colors.inkMuted,
                    style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 13.5.sp))
            }
        }
        HorizontalDivider(color = MaktabaTheme.colors.divider)
        CollectionHeading("Add to a collection")
        CollectionNote("This saves a catalogue reference, not a copy in your Library. Global title hiding cannot be bypassed by adding to a shared list.")
        CollectionLoad(state, onRetry) {
            if (collections.isEmpty()) CollectionNote("Create a collection to get started.")
            collections.forEach { collection ->
                val already = catalogId in collection.catalogIds
                val full = collection.catalogIds.size >= 500
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(collection.name, style = TextStyle(fontFamily = SansBody, fontSize = 13.sp, lineHeight = 19.5.sp))
                            Text(if (collection.shared) "Shared collection" else "Private collection",
                                color = MaktabaTheme.colors.inkMuted,
                                style = TextStyle(fontFamily = SansBody, fontSize = 11.sp, lineHeight = 16.5.sp))
                        }
                        TextButton(onClick = { onAdd(collection) },
                            enabled = !already && !full && busyId == null) {
                            Text(when {
                                busyId == collection.id -> "Adding…"
                                already -> "Added"
                                full -> "Full"
                                else -> "Add"
                            }, style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 18.sp))
                        }
                    }
                    HorizontalDivider(color = MaktabaTheme.colors.divider)
                }
            }
        }
    }
}
