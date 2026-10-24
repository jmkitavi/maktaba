package com.maktaba.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.navigation.NavHostController
import com.maktaba.app.data.*
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.SansBody
import com.maktaba.app.ui.theme.SerifDisplay

@Composable
fun SharedLibraryScreen(navController: NavHostController, ownerUid: String) {
    val feed = rememberCollectionFeed("library:$ownerUid") { CollectionsRepository.sharedLibrary(ownerUid) }
    var menu by remember { mutableStateOf(false) }
    CollectionPage(navController, if (menu) "Shared library" else "", showBottomBar = menu, actions = {
        IconButton(onClick = { menu = !menu }) {
            Icon(Icons.Outlined.MoreHoriz, if (menu) "Close library menu" else "Open library menu")
        }
    }) {
        CollectionLoad(feed.state, feed::refresh) { page ->
            SharedLibraryContent(page,
                onReader = { navController.collectionRoute("reader_profile", ownerUid) },
                onBook = { navController.collectionRoute("catalogue_book", it) },
                isOwner = ownerUid == FirebaseSession.currentUser?.uid,
                showMenu = menu,
                onReport = { navController.collectionRoute("report_library", ownerUid) },
                catalogue = LibraryRepository.maktabaCollection)
            if (feed.hasMore) CollectionAction(if (feed.loadingMore) "Loading…" else "Load more books",
                !feed.loadingMore, feed::loadMore)
        }
    }
}

@Composable
fun SharedLibraryContent(
    page: SharedLibraryPage,
    onReader: () -> Unit,
    onBook: (String) -> Unit,
    isOwner: Boolean = false,
    showMenu: Boolean = false,
    onReport: () -> Unit = {},
    catalogue: List<Book> = emptyList()
) {
    val colors = MaktabaTheme.colors
    Column {
        if (showMenu) {
            Spacer(Modifier.height(13.dp))
            CollectionHeading(page.library.libraryName)
            Spacer(Modifier.height(12.dp))
            CollectionNote("${page.library.ownerDisplayName} · ${page.library.bookCount} visible titles")
            Spacer(Modifier.height(18.dp))
        } else {
            Text(page.library.libraryName, color = colors.ink,
                style = TextStyle(fontFamily = SerifDisplay, fontSize = 30.sp, lineHeight = 34.5.sp, letterSpacing = (-.7).sp))
            Spacer(Modifier.height(14.dp))
            PublicReaderIdentity(page.library.ownerDisplayName, page.library.ownerPhotoUrl, onReader, isOwner)
            Spacer(Modifier.height(13.dp))
            Text("${page.library.bookCount} books · Shared with signed-in readers", color = colors.inkMuted,
                style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 17.sp))
            Spacer(Modifier.height(24.dp))
        }
        if (page.books.isEmpty()) {
            CollectionHeading("A shelf with room to grow")
            CollectionNote("This reader has shared their library, but no titles are currently visible.")
        }
        val booksById = page.books.associateBy { it.catalogBookId }
        CollectionBookGrid(page.books.map { public ->
            public.coverBook(catalogue.find { it.catalogId == public.catalogBookId })
        }, label = { book ->
            val public = booksById.getValue(book.catalogId)
            when {
                public.format == BookFormat.DIGITAL -> if (showMenu) "Digital · cannot be lent" else "Digital · Cannot be lent"
                public.format != BookFormat.PHYSICAL -> "Edition unspecified · Cannot be lent"
                public.availability == SharedAvailability.AVAILABLE -> "Available"
                else -> if (showMenu) "On loan" else "On Loan"
            }
        }, onBook = onBook, companion = showMenu, showAuthors = !showMenu, labelPills = showMenu,
            labelColor = { book ->
                val public = booksById.getValue(book.catalogId)
                when {
                    public.format != BookFormat.PHYSICAL -> if (showMenu) colors.inkMuted else colors.success
                    public.availability == SharedAvailability.AVAILABLE -> if (showMenu) colors.inkMuted else colors.success
                    else -> colors.warning
                }
            }, digitalStatusIcon = !showMenu)
        if (showMenu) {
            Spacer(Modifier.height(23.dp))
            SharedLibraryOverflowContent(onReport)
        }
    }
}

@Composable
fun SharedLibraryOverflowContent(onReport: () -> Unit) {
    Column {
        CollectionSection("LIBRARY MENU")
        Spacer(Modifier.height(8.dp))
        CollectionLink("Report this library", "Tell us about a concern with this shared library", onReport)
        Spacer(Modifier.height(14.dp))
        CollectionNote("Shared books do not reveal dates or borrowers. Borrowed copies are never included in a whole shared library.")
    }
}

@Composable
fun ReaderProfileScreen(navController: NavHostController, readerUid: String) {
    val feed = rememberCollectionFeed("reader:$readerUid") { CollectionsRepository.readerProfile(readerUid) }
    CollectionPage(navController, "Reader Profile") {
        CollectionLoad(feed.state, feed::refresh) { page ->
            ReaderProfileContent(page,
                onLibrary = { navController.collectionRoute("shared_library", readerUid) },
                onCollection = { navController.collectionRoute("shared_collection", readerUid, it) })
            if (feed.hasMore) CollectionAction(if (feed.loadingMore) "Loading…" else "Load more collections",
                !feed.loadingMore, feed::loadMore)
        }
    }
}

@Composable
fun ReaderProfileContent(
    page: PublicReaderPage,
    onLibrary: () -> Unit,
    onCollection: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(1.dp))
        CollectionHeading(page.reader.ownerDisplayName)
        CollectionNote("Reader")
        if (page.library == null) {
            CollectionPanel {
                Column {
                    CollectionBadge("Library is private")
                    val firstName = page.reader.ownerDisplayName.trim().substringBefore(" ").ifEmpty { "This reader" }
                    CollectionNote("$firstName has not shared their whole library. Their independently shared collections below remain visible.")
                }
            }
        } else {
            CollectionLink(page.library.libraryName, "${page.library.bookCount} visible books", onLibrary)
        }
        Box(Modifier.padding(top = 11.dp)) { CollectionSection("SHARED COLLECTIONS") }
        if (page.collections.isEmpty()) CollectionNote("No shared collections yet.")
        page.collections.forEach { summary ->
            SharedCollectionCard(summary) { onCollection(summary.id) }
        }
        val firstName = page.reader.ownerDisplayName.trim().substringBefore(" ").ifEmpty { "this reader" }
        CollectionNote("Only collections $firstName has chosen to share appear here. ${firstName.replaceFirstChar { it.uppercase() }}’s global title hiding applies to every one of their shared collections.",
            Modifier.padding(top = 6.dp))
    }
}

@Composable
fun SharedCollectionScreen(
    navController: NavHostController,
    ownerUid: String,
    collectionId: String
) {
    val feed = rememberCollectionFeed("collection:$ownerUid:$collectionId") {
        CollectionsRepository.sharedCollection(ownerUid, collectionId)
    }
    CollectionPage(navController, "Collection") {
        CollectionLoad(feed.state, feed::refresh) { page ->
            SharedCollectionContent(page,
                onReader = { navController.collectionRoute("reader_profile", ownerUid) },
                onBook = { navController.collectionRoute("catalogue_book", it) },
                catalogue = LibraryRepository.maktabaCollection)
            if (feed.hasMore) CollectionAction(if (feed.loadingMore) "Loading…" else "Load more titles",
                !feed.loadingMore, feed::loadMore)
        }
    }
}

@Composable
fun SharedCollectionContent(
    page: SharedCollectionPage,
    onReader: () -> Unit,
    onBook: (String) -> Unit,
    catalogue: List<Book> = emptyList()
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CollectionBadge("Shared · signed-in readers", shared = true)
        CollectionHeading(page.collection.name)
        Column {
            CollectionLink("By ${page.reader.ownerDisplayName}", "View reader Profile", onReader)
            CollectionNote("${page.collection.bookCount} visible titles")
        }
        if (page.books.isEmpty()) {
            CollectionHeading("No visible titles yet")
            CollectionNote("This shared list is empty for readers. Check back another time.")
        }
        Box(Modifier.padding(top = 6.dp)) {
            CollectionBookGrid(page.books.map { public ->
                public.coverBook(catalogue.find { it.catalogId == public.catalogBookId })
            }, onBook = onBook, companion = true, showLabels = false)
        }
        CollectionNote("A curated list of catalogue references, not a statement of ownership. No loan information is shown.",
            Modifier.padding(top = 6.dp))
    }
}
