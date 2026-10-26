package com.maktaba.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.data.*
import com.maktaba.app.R
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.SansBody
import com.maktaba.app.ui.theme.SerifDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CollectionsScreen(navController: NavHostController) {
    var browse by rememberSaveable { mutableStateOf(false) }
    CollectionPage(navController, "Collections", root = true) {
        CollectionTabs(browse, onBrowse = { browse = it })
        if (browse) BrowseLibrariesContent { navController.collectionRoute("shared_library", it) }
        else CollectionsYoursContent(
            collections = CollectionsRepository.collections.toList(),
            collectionsState = CollectionsRepository.collectionsState,
            settings = CollectionsRepository.sharingSettings,
            savedCount = if (LibraryRepository.hasLoadedWishlist) LibraryRepository.wishlistCatalogIds.size else null,
            onWishlist = { navController.navigate("wishlist") },
            onCollection = { navController.collectionRoute("collection", it) },
            onCreate = { navController.navigate("collection_create") },
            onSharing = { navController.navigate("library_sharing") },
            onRetry = CollectionsRepository::retryCollections,
            savedError = LibraryRepository.wishlistError,
            savedBooks = LibraryRepository.wishlistBooks,
            catalogue = LibraryRepository.maktabaCollection,
            compact = false,
            onCatalogue = { navController.navigate("home_library") }
        )
    }
}

@Composable
internal fun CollectionTabs(browse: Boolean, onBrowse: (Boolean) -> Unit, segmented: Boolean = false) {
    val colors = MaktabaTheme.colors
    if (segmented) {
        Row(Modifier.fillMaxWidth().background(colors.surfaceAlt, RoundedCornerShape(9.dp)).padding(3.dp)) {
            listOf(false to "Yours", true to "Browse").forEach { (value, label) ->
                Box(Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (browse == value) colors.surface else colors.surfaceAlt)
                    .selectable(browse == value, role = Role.Tab, onClick = { onBrowse(value) })
                    .padding(horizontal = 15.dp, vertical = 13.dp)) {
                    Text(label,
                        color = if (browse == value) colors.primary else colors.ink,
                        style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 18.sp))
                }
            }
        }
    } else Column(Modifier.padding(horizontal =
        if (LocalConfiguration.current.screenWidthDp <= 330) 4.dp else 0.dp)) {
        Row {
            listOf(false to "Yours", true to "Browse").forEach { (value, label) ->
                Box(Modifier.weight(1f).heightIn(min = 46.dp)
                    .selectable(browse == value, role = Role.Tab, onClick = { onBrowse(value) }),
                    contentAlignment = Alignment.Center) {
                    Text(label, Modifier.padding(vertical = 12.dp),
                        color = if (browse == value) colors.primary else colors.inkMuted,
                        style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 18.sp))
                    if (browse == value) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .height(2.dp).background(colors.primary))
                }
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
fun CollectionsYoursContent(
    collections: List<CustomCollection>,
    collectionsState: LoadState<Unit>,
    settings: LoadState<LibrarySharingSettings>,
    savedCount: Int?,
    onWishlist: () -> Unit,
    onCollection: (String) -> Unit,
    onCreate: () -> Unit,
    onSharing: () -> Unit,
    onRetry: () -> Unit,
    savedError: String? = null,
    savedBooks: List<Book> = emptyList(),
    catalogue: List<Book> = emptyList(),
    compact: Boolean = true,
    onCatalogue: (() -> Unit)? = null
) {
    val colors = MaktabaTheme.colors
    val savedLabel = when {
        savedError != null -> "Saved titles unavailable"
        savedCount == null -> "Loading saved titles…"
        else -> "$savedCount books"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (compact) CollectionLink("Want to Read", "Pinned · private · never renamed or deleted", onWishlist)
        else {
            Text("A little order. Room for what comes next.",
                modifier = Modifier.padding(bottom = 5.dp),
                style = TextStyle(fontFamily = SerifDisplay, fontSize = 13.sp, lineHeight = 22.1.sp,
                    fontStyle = FontStyle.Italic, lineHeightStyle = CollectionLineHeight),
                color = colors.inkMuted)
            PrivateCollectionCard("Want to Read", "$savedLabel · Pinned", "Private", savedBooks, onClick = onWishlist)
        }
        Spacer(Modifier.height(if (compact) 0.dp else 1.dp))
        CollectionSection("YOUR COLLECTIONS")
        CollectionLoad(collectionsState, onRetry) {
            if (collections.isEmpty()) {
                if (compact) {
                    CollectionPanel {
                        Image(painterResource(R.drawable.illus_home_icon), null,
                            Modifier.align(Alignment.CenterHorizontally)
                                .padding(vertical = 20.dp).width(150.dp).height(100.dp),
                            contentScale = ContentScale.Fit)
                        CollectionNote("Grow your books into little collections — a shelf for the book club, one for the ones you'll never lend.")
                    }
                    Spacer(Modifier.height(12.dp))
                } else CollectionNote("Make room for your next reading list.")
            }
            val hidden = (settings as? LoadState.Ready)?.value?.hiddenCatalogIds
            collections.forEachIndexed { index, collection ->
                val visible = hidden?.let { collection.visibleCatalogIds(it).size }
                val counts = if (visible == null) "${collection.catalogIds.size} selected · Visibility loading"
                    else "${collection.catalogIds.size} selected / $visible visible"
                if (compact) CollectionLink(collection.name, "$counts · ${if (collection.shared) "Shared" else "Private"}") {
                    onCollection(collection.id)
                } else PrivateCollectionCard(collection.name, counts,
                    if (collection.shared) "Shared" else "Private",
                    collection.catalogIds.mapNotNull { id -> catalogue.find { it.catalogId == id } },
                    position = index + 1) {
                    onCollection(collection.id)
                }
            }
        }
        if (compact) CollectionAction("New collection", onClick = onCreate)
        else {
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp)
                .drawBehind {
                    drawRoundRect(colors.sageLine, cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                }.clickable(onClick = onCreate).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(15.dp), tint = colors.primary)
                Text("New Collection", color = colors.primary,
                    style = TextStyle(fontFamily = SansBody, fontSize = 11.sp, lineHeight = 16.5.sp))
            }
        }
        val sharingLabel = when (settings) {
            is LoadState.Ready -> if (settings.value.libraryShared)
                "Shared · separate from collection sharing" else "Private · separate from collection sharing"
            is LoadState.Error -> "Sharing settings unavailable · Retry"
            LoadState.NotFound -> "Sharing settings unavailable"
            LoadState.Loading -> "Loading sharing settings…"
        }
        if (compact) {
            CollectionLink("Your library", sharingLabel, onSharing)
            if (onCatalogue != null) {
                Spacer(Modifier.height(1.dp))
                CollectionSection("OTHER ENTRY POINTS")
                CollectionLink("Add a catalogue title", "From Library or Book Detail", onCatalogue)
            }
        }
        else {
            Spacer(Modifier.height(1.dp))
            CollectionSection("YOUR LIBRARY")
            Surface(onClick = onSharing, shape = RoundedCornerShape(12.dp),
                color = colors.ochre, border = BorderStroke(1.dp, colors.divider)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(17.dp), color = colors.sage, modifier = Modifier.size(34.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Lock, null, Modifier.size(17.dp), tint = colors.primary)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(when (settings) {
                            is LoadState.Ready -> if (settings.value.libraryShared)
                                "Your library is shared" else "Your library is private"
                            else -> "Your library sharing"
                        },
                            style = TextStyle(fontFamily = SerifDisplay, fontSize = 17.sp, lineHeight = 22.1.sp))
                        Spacer(Modifier.height(6.dp))
                        Text(sharingLabel, color = colors.inkMuted,
                            style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 16.sp))
                        Spacer(Modifier.height(10.dp))
                        Text("Manage library sharing", color = colors.primary,
                            style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 16.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateCollectionCard(title: String, count: String, visibility: String, books: List<Book>,
    position: Int = 0, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = collectionAccent(position),
        border = BorderStroke(1.dp, MaktabaTheme.colors.divider)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CollectionMosaic(emptyList(), title, previewBooks = books.take(4).map {
                SharedCollectionBook(it.catalogId, it.title, it.author, it.coverUrl, it.format)
            }, catalogue = books)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = TextStyle(fontFamily = SerifDisplay, fontSize = 17.sp,
                    lineHeight = 22.1.sp, lineHeightStyle = CollectionLineHeight))
                Text(count, style = TextStyle(fontFamily = SansBody, fontSize = 10.sp,
                    lineHeight = 16.sp, lineHeightStyle = CollectionLineHeight),
                    color = MaktabaTheme.colors.inkMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (visibility == "Private")
                        Icon(Icons.Outlined.Lock, null, Modifier.size(11.dp), tint = MaktabaTheme.colors.inkMuted)
                    Text(visibility, style = TextStyle(fontFamily = SansBody, fontSize = 9.sp,
                        lineHeight = 14.4.sp, lineHeightStyle = CollectionLineHeight),
                        color = MaktabaTheme.colors.inkMuted)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(12.dp),
                tint = MaktabaTheme.colors.inkMuted)
        }
    }
}

@Composable
private fun BrowseLibrariesContent(onLibrary: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(search) { delay(300); debounced = search }
    val feed = rememberCollectionFeed("directory:$debounced") { CollectionsRepository.directory(debounced) }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        BrowseLibrariesSearch(search, { search = it })
        BrowseLibrariesPresentation(feed.state, debounced.isNotBlank(), feed::refresh, onLibrary,
            viewerUid = FirebaseSession.currentUser?.uid, catalogue = LibraryRepository.maktabaCollection)
        if (feed.hasMore) CollectionAction(if (feed.loadingMore) "Loading…" else "Load more libraries",
            !feed.loadingMore, feed::loadMore)
    }
}

@Composable
internal fun BrowseLibrariesSearch(search: String, onSearch: (String) -> Unit) {
    CollectionTextField(search, onSearch, placeholder = "Search libraries or readers", search = true)
    Row(Modifier.padding(top = 13.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(11.dp), tint = MaktabaTheme.colors.inkMuted)
        Text("Shared with signed-in Book Haven readers", color = MaktabaTheme.colors.inkMuted,
            style = TextStyle(fontFamily = SansBody, fontSize = 9.sp, lineHeight = 14.4.sp,
                lineHeightStyle = CollectionLineHeight))
    }
}

@Composable
fun BrowseLibrariesPresentation(
    state: LoadState<List<SharedLibrarySummary>>,
    searching: Boolean,
    onRetry: () -> Unit,
    onLibrary: (String) -> Unit,
    viewerUid: String? = null,
    catalogue: List<Book> = emptyList()
) {
    CollectionLoad(state, onRetry) { libraries ->
        if (libraries.isEmpty()) {
            CollectionHeading(if (searching) "No matching libraries" else "A quiet corner, for now")
            CollectionNote(if (searching) "Try another library or reader name."
                else "Libraries will appear when readers choose to share.")
        }
        BoxWithConstraints {
        val minimumCardHeight = ((maxWidth - 12.dp) / 2 - 25.dp) * 1.5f + 123.4.dp
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            libraries.chunked(2).forEachIndexed { rowIndex, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { columnIndex, library ->
                        Surface(onClick = { onLibrary(library.ownerUid) },
                            modifier = Modifier.weight(1f).heightIn(min = minimumCardHeight),
                            shape = RoundedCornerShape(12.dp), color = collectionAccent(rowIndex * 2 + columnIndex),
                            border = BorderStroke(1.dp, MaktabaTheme.colors.divider)) {
                            Column(Modifier.padding(11.dp)) {
                                CollectionMosaic(library.coverUrls, library.libraryName, Modifier.fillMaxWidth(),
                                    library.previewBooks, catalogue)
                                Spacer(Modifier.height(12.dp))
                                Text(library.libraryName,
                                    style = TextStyle(fontFamily = SerifDisplay, fontSize = 16.sp, lineHeight = 20.sp,
                                        lineHeightStyle = CollectionLineHeight))
                                Spacer(Modifier.height(7.dp))
                                Text(library.ownerDisplayName, color = MaktabaTheme.colors.inkMuted,
                                    style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 16.sp,
                                        lineHeightStyle = CollectionLineHeight))
                                Spacer(Modifier.height(9.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("${library.bookCount} books", color = MaktabaTheme.colors.inkMuted,
                                        style = TextStyle(fontFamily = SansBody, fontSize = 9.sp, lineHeight = 14.4.sp,
                                            lineHeightStyle = CollectionLineHeight))
                                    if (library.ownerUid == viewerUid) Surface(shape = RoundedCornerShape(4.dp),
                                        color = MaktabaTheme.colors.successContainer) {
                                        Text("Yours", Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            color = MaktabaTheme.colors.success,
                                            style = TextStyle(fontFamily = SansBody, fontSize = 8.sp, lineHeight = 12.sp,
                                                lineHeightStyle = CollectionLineHeight))
                                    }
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        }
    }
}

@Composable
fun LibrarySharingScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val referencedIds = CollectionsRepository.collections.flatMap { it.catalogIds }.toSet()
    val choices = collectionCandidates(LibraryRepository.books.filter { it.status != BookStatus.BORROWED },
        emptyList(), LibraryRepository.maktabaCollection.filter { it.catalogId in referencedIds })
    CollectionPage(navController, "Library Sharing", showBottomBar = false, sheet = true) {
        CollectionLoad(CollectionsRepository.sharingSettings,
            { scope.launch { runCatching { CollectionsRepository.refreshSharingSettings() } } }) { settings ->
            LibrarySharingContent(settings, choices, CollectionsRepository::setLibrarySharing,
                CollectionsRepository::setTitleHidden,
                hasIndependentCollections = CollectionsRepository.collections.isNotEmpty() ||
                    CollectionsRepository.collectionsState !is LoadState.Ready,
                candidatesLoading = !LibraryRepository.hasLoadedLibrary,
                candidatesError = LibraryRepository.libraryError,
                onRetryCandidates = LibraryRepository::retryLoads,
                onPreview = FirebaseSession.currentUser?.uid?.let { uid ->
                    { navController.collectionRoute("shared_library", uid) }
                })
        }
    }
}

@Composable
fun LibrarySharingContent(
    settings: LibrarySharingSettings,
    candidates: List<Book>,
    onSave: suspend (Boolean, String) -> Unit,
    onHide: suspend (String, Boolean) -> Unit,
    initialHiddenExpanded: Boolean = false,
    hasIndependentCollections: Boolean = false,
    candidatesLoading: Boolean = false,
    candidatesError: String? = null,
    onRetryCandidates: () -> Unit = {},
    onPreview: (() -> Unit)? = null
) {
    val colors = MaktabaTheme.colors
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(settings.libraryName) { mutableStateOf(settings.libraryName) }
    var enabled by rememberSaveable(settings.libraryShared) { mutableStateOf(settings.libraryShared) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var saved by remember { mutableStateOf(false) }
    var retryAction by remember { mutableStateOf<suspend () -> Unit>({}) }
    var hiddenExpanded by rememberSaveable { mutableStateOf(initialHiddenExpanded) }
    fun mutate(action: suspend () -> Unit) {
        if (busy) return
        retryAction = action; busy = true; error = null; saved = false
        scope.launch {
            try { action(); saved = true }
            catch (failure: Exception) { error = failure }
            finally { busy = false }
        }
    }
    Column {
        Spacer(Modifier.height(8.dp))
        Text("Your shelf. Your choice.", color = colors.ink,
            style = TextStyle(fontFamily = SerifDisplay, fontSize = 27.sp, lineHeight = 31.sp, letterSpacing = (-.5).sp))
        Spacer(Modifier.height(18.dp))
        Text("Sharing shows your library name, display name and owned books — including digital editions — to signed-in Book Haven readers.",
            color = colors.inkMuted, style = TextStyle(fontFamily = SansBody, fontSize = 11.sp, lineHeight = 19.8.sp,
                lineHeightStyle = CollectionLineHeight))
        Spacer(Modifier.height(13.dp))
        Text("Borrowed books stay private. Lent books show only On Loan, never who has them or when they are due. Hidden books are excluded.",
            color = colors.inkMuted, style = TextStyle(fontFamily = SansBody, fontSize = 11.sp, lineHeight = 19.8.sp,
                lineHeightStyle = CollectionLineHeight))
        Spacer(Modifier.height(23.dp))
        Text("LIBRARY NAME", style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = .5.sp))
        Spacer(Modifier.height(9.dp))
        CollectionTextField(name, { name = it; saved = false }, limit = 40, enabled = !busy)
        Spacer(Modifier.height(31.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = colors.sage,
            border = BorderStroke(1.dp, colors.sageLine)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Share My Library", style = TextStyle(fontFamily = SansBody, fontSize = 13.sp,
                    lineHeight = 19.5.sp, fontWeight = FontWeight.Medium))
                Text(if (busy) "Saving sharing settings…" else if (settings.libraryShared)
                    "Visible to signed-in readers" else "Off. Only you can see your shelf.",
                    color = colors.inkMuted, style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 16.sp))
            }
            CollectionSharingSwitch(enabled, enabled = !busy && name.trim().length in 1..40) { next ->
                val previous = enabled; enabled = next
                mutate {
                    try { onSave(next, name.trim()) }
                    catch (failure: Exception) { enabled = previous; throw failure }
                }
            }
        }
        }
        if (name.trim() != settings.libraryName) {
            Spacer(Modifier.height(12.dp))
            CollectionAction(if (busy) "Saving…" else "Save library name", !busy && name.trim().length in 1..40) {
                mutate { onSave(settings.libraryShared, name.trim()) }
            }
        }
        if (saved) CollectionNote("Settings saved. Safe public previews may briefly be unavailable while publishing.")
        error?.let { failure -> CollectionProblem(failure) { mutate(retryAction) } }
        if (enabled || settings.hiddenCatalogIds.isNotEmpty() || hasIndependentCollections) {
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { hiddenExpanded = !hiddenExpanded },
                verticalAlignment = Alignment.CenterVertically) {
                Text("Hidden Books", Modifier.weight(1f),
                    style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 18.sp))
                Surface(color = colors.terracotta, shape = RoundedCornerShape(20.dp)) {
                    Text("${settings.hiddenCatalogIds.size}", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = colors.terraAction, style = TextStyle(fontFamily = SansBody, fontSize = 11.sp))
                }
                Spacer(Modifier.width(9.dp))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(12.dp), tint = colors.inkMuted)
            }
            HorizontalDivider(color = colors.divider)
            if (hiddenExpanded) {
                Spacer(Modifier.height(14.dp))
                Text("Checked books are hidden from your shared views.",
                    color = colors.inkMuted, style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 18.sp))
                Spacer(Modifier.height(8.dp))
                val allIds = (candidates.map { it.catalogId } + settings.hiddenCatalogIds).distinct()
                if (candidatesLoading && candidatesError == null) CollectionNote("Loading title choices…")
                if (candidatesError != null) {
                    CollectionNote(candidatesError)
                    CollectionOutline("Retry titles", onClick = onRetryCandidates)
                }
                if (allIds.isEmpty() && !candidatesLoading && candidatesError == null)
                    CollectionNote("No catalogue titles yet.")
                allIds.forEach { id ->
                    val book = candidates.find { it.catalogId == id }
                    val hidden = id in settings.hiddenCatalogIds
                    Surface(shape = RoundedCornerShape(9.dp),
                        color = if (hidden) colors.terracotta else colors.surface,
                        border = BorderStroke(1.dp, colors.divider)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(horizontal = 9.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (book != null) BookCoverImage(book,
                            Modifier.width(30.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(3.dp)),
                            ContentScale.Fit, decorative = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(book?.title ?: "Saved title reference",
                                style = TextStyle(fontFamily = SerifDisplay, fontSize = 13.sp, lineHeight = 16.9.sp))
                            Text(if (hidden) "Hidden" else "Visible", color = colors.inkMuted,
                                style = TextStyle(fontFamily = SansBody, fontSize = 9.sp, lineHeight = 13.5.sp))
                        }
                        Checkbox(hidden, { next -> mutate { onHide(id, next) } },
                            Modifier.size(24.dp).testTag("hidden-title-$id"),
                            enabled = !busy && (hidden || settings.hiddenCatalogIds.size < 200))
                    }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(14.dp))
                CollectionNote("Hidden titles stay in your private collections. Only your shared grids, cover previews and visible counts change.")
            }
        }
        if (!enabled) {
            Spacer(Modifier.height(18.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = colors.ochre,
                border = BorderStroke(1.dp, colors.divider)) {
            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(11.dp), tint = colors.inkMuted)
                Text("Your shelf stays private until you choose otherwise.", color = colors.inkMuted,
                    style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 17.sp))
            }
            }
        }
        if (hasIndependentCollections) {
            Spacer(Modifier.height(14.dp))
            CollectionNote("Title hiding also applies to independently shared collections, even while your whole library is private.")
        }
        if (settings.libraryShared && onPreview != null) {
            Spacer(Modifier.height(15.dp))
            CollectionOutline("Preview My Shared Shelf", enabled = !busy, onClick = onPreview)
        }
    }
}
