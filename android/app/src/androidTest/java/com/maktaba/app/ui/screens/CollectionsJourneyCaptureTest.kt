package com.maktaba.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.maktaba.app.BuildConfig
import com.maktaba.app.R
import com.maktaba.app.data.*
import com.maktaba.app.journeys.JourneyFixtures
import com.maktaba.app.journeys.JourneyCapture
import com.maktaba.app.ui.components.*
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CollectionsJourneyCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val references = listOf(
        "collections", "want-to-read", "browse", "shared", "sharing-off", "sharing-enabled", "sharing-on",
        "browse-own", "shared-own"
    ) + (31..40).map { "companion-$it" } + listOf(
        "companion-54", "companion-55", "collections-empty", "collection-hidden"
    )

    @Test
    fun captureApprovedCollectionJourneys() {
        JourneyCapture.prepareWindow()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val themes = when (val selected = arguments.getString("captureTheme") ?: "light") {
            "light", "dark" -> listOf(selected)
            else -> error("Run once per captureTheme: light or dark.")
        }
        val fontScale = JourneyCapture.fontScale
        val expectedWidth = JourneyCapture.width.toFloat()
        require(fontScale.isFinite() && fontScale > 0)
        val fixtures = CaptureCollections()
        runBlocking {
            fixtures.books.map { it.coverUrl }.filter { it.isNotBlank() }.distinct().forEach { url ->
                require(url.startsWith("android.resource://${BuildConfig.APPLICATION_ID}/") ||
                    url.startsWith("file:///android_asset/")) {
                    "Journey fixture jackets must be local debug resources: $url"
                }
                val result = instrumentation.targetContext.imageLoader.execute(
                    ImageRequest.Builder(instrumentation.targetContext).data(url)
                        .size(Size.ORIGINAL).allowHardware(false).build()
                )
                assertTrue("Could not load original fixture jacket $url", result is SuccessResult)
            }
        }
        var reference by mutableStateOf(references.first())
        var theme by mutableStateOf(themes.first())
        compose.setContent {
            val nativeDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(nativeDensity.density, fontScale)) {
                BookHavenTheme(darkTheme = theme == "dark") {
                    key(reference, theme) { fixtures.Render(reference) }
                }
            }
        }
        themes.forEach { nextTheme ->
            references.forEach { nextReference ->
                compose.runOnIdle { reference = nextReference; theme = nextTheme }
                compose.waitForIdle()
                assertScenario(nextReference)
                if (nextReference == "sharing-on") {
                    runBlocking {
                        fixtures.scrollSharing(instrumentation.targetContext.resources.displayMetrics.density)
                    }
                }
                if (nextReference == "companion-40") {
                    compose.onNodeWithText("Report this library").assertExists()
                }
                compose.waitForIdle()
                assertEquals(expectedWidth, compose.onRoot().fetchSemanticsNode().boundsInRoot.width /
                    instrumentation.targetContext.resources.displayMetrics.density, 2f)
                JourneyCapture.save(compose, nextReference)
            }
        }
    }

    private fun assertScenario(reference: String) {
        val heading = when (reference) {
            "collections", "browse", "browse-own", "companion-31", "collections-empty" -> "Collections"
            "want-to-read" -> "Want to Read"
            "shared", "companion-40" -> "Rowan's Reading Room"
            "shared-own" -> "Alex Reader's Library"
            "sharing-off", "sharing-enabled", "sharing-on" -> "Your shelf. Your choice."
            "companion-32" -> "Give your list a name."
            "companion-33", "companion-39" -> "Slow Sunday reads"
            "companion-34" -> "Select books"
            "companion-35" -> "Make it yours."
            "companion-36", "collection-hidden" -> "Your list. Your choice."
            "companion-37" -> "Delete this collection?"
            "companion-38" -> "The Long Way to Sunday"
            "companion-54" -> "Library is private"
            "companion-55" -> "Weekend Pages"
            else -> error("Unmapped capture reference $reference")
        }
        compose.onAllNodesWithText(heading).onFirst().assertExists()
        when (reference) {
            "companion-31" -> {
                compose.onNodeWithText("Slow Sunday reads").assertExists()
                compose.onNodeWithText("3 selected / 2 visible · Private").assertExists()
            }
            "collections-empty" -> {
                compose.onNodeWithText("Grow your books into little collections — a shelf for the book club, one for the ones you'll never lend.")
                    .assertExists()
                compose.onNodeWithText("Want to Read").assertExists()
                compose.onNodeWithText("Slow Sunday reads").assertDoesNotExist()
            }
            "collection-hidden" -> {
                compose.onAllNodesWithText("Shared · signed-in readers").onFirst().assertExists()
                compose.onNodeWithText("3 selected / 0 visible").assertExists()
                compose.onNodeWithText("3 selected titles are hidden from every shared view.").assertExists()
                compose.onNodeWithText("The Paper Garden").assertDoesNotExist()
            }
            "sharing-enabled" -> {
                compose.onNodeWithText("Visible to signed-in readers").assertExists()
                compose.onNodeWithText("0").assertExists()
                compose.onNodeWithText("Preview My Shared Shelf").assertExists()
            }
            "sharing-on" -> {
                compose.onNodeWithTag("hidden-title-paper-garden").assertIsOn()
                compose.onNodeWithTag("hidden-title-winterlight").assertIsOff()
            }
            "shared", "shared-own" -> {
                compose.onNodeWithText("A Field Guide to Small Things").assertDoesNotExist()
                compose.onNodeWithText("The Restless City").assertDoesNotExist()
                if (reference == "shared-own")
                    compose.onNodeWithText("The Paper Garden").assertDoesNotExist()
            }
            "companion-39" -> {
                compose.onNodeWithText("The Paper Garden").assertDoesNotExist()
                compose.onNodeWithText("2 visible titles").assertExists()
            }
            "companion-54" -> {
                compose.onNodeWithText("Want to Read").assertDoesNotExist()
                compose.onNodeWithText("Slow Sunday reads").assertDoesNotExist()
                compose.onNodeWithText("Weekend Pages").assertExists()
            }
            "companion-55" -> {
                compose.onNodeWithText("A Field Guide to Small Things").assertExists()
                compose.onNodeWithText("The Paper Garden").assertExists()
                compose.onNodeWithText("Available").assertDoesNotExist()
                compose.onNodeWithText("On Loan").assertDoesNotExist()
                compose.onNodeWithText("Borrowed").assertDoesNotExist()
            }
        }
    }
}

private class CaptureCollections {
    private var sharingScroll: ScrollState? = null
    suspend fun scrollSharing(density: Float) {
        checkNotNull(sharingScroll).scrollTo((230 * density).toInt())
    }
    val books = (JourneyFixtures.books + JourneyFixtures.catalogue).distinctBy { it.catalogId }
    private val byId = books.associateBy { it.catalogId }
    private fun book(id: String) = checkNotNull(byId[id]) { "JourneyFixtures is missing catalogue ID $id." }
    private fun covers(vararg ids: String) = ids.map { JourneyFixtures.coverUrl(it) }.filter { it.isNotBlank() }
    private val hidden = setOf("paper-garden")
    private val settings = LibrarySharingSettings(false, "Alex Reader's Library", hidden, 1)
    private val collection = CustomCollection("slow-sunday", "Slow Sunday reads",
        listOf("quiet-mornings", "solitude", "paper-garden"), false)
    private val reader = PublicReaderProfile("mira", "Mira Vale", "", 1, 1)
    private val weekend = SharedCollectionSummary("weekend-pages", "mira", "Weekend Pages", 3,
        covers("sunday", "small-things", "paper-garden"), 1)
    private fun summary(owner: String, name: String, displayName: String, ids: List<String>) =
        SharedLibrarySummary(owner, name, displayName, "", ids.size,
            ids.map { JourneyFixtures.coverUrl(it) }.filter { it.isNotBlank() }.take(4), 1,
            ids.take(4).map { id -> book(id).let {
                SharedCollectionBook(id, it.title, it.author, it.coverUrl, it.format)
            } })
    private val rowanIds = listOf("quiet-mornings", "distant-shores", "solitude", "paper-garden")
    private val ownIds = listOf("quiet-mornings", "distant-shores", "stillwater", "solitude", "winterlight")
    private val rowan = summary("rowan", "Rowan's Reading Room", "Rowan Ellis", rowanIds)
    private val own = summary("alex", "Alex Reader's Library", "Alex Reader", ownIds)
    private val directory = listOf(
        rowan,
        summary("mira", "The Sunday Shelf", "Mira Vale", listOf("quiet-mornings", "stillwater", "winterlight")),
        summary("ari", "Books by the Window", "Ari Bell", listOf("solitude", "paper-garden"))
    )

    private fun library(owner: Boolean) = SharedLibraryPage(if (owner) own else rowan,
        (if (owner) ownIds else rowanIds).map { id ->
            val source = book(id)
            SharedLibraryBook(id, source.title, source.author, source.coverUrl, source.format,
                if (id in setOf("distant-shores", "stillwater")) SharedAvailability.ON_LOAN
                else SharedAvailability.AVAILABLE)
        }, false)

    private fun sharedList() = SharedCollectionPage(weekend, reader,
        listOf("sunday", "small-things", "paper-garden").map { id ->
            val source = book(id)
            SharedCollectionBook(id, source.title, source.author, source.coverUrl, source.format)
        }, false)

    @Composable
    fun Render(reference: String) {
        val root = reference in setOf("collections", "browse", "browse-own", "companion-31", "collections-empty")
        val compactRoot = reference in setOf("companion-31", "collections-empty")
        val sheet = reference in setOf("sharing-off", "sharing-enabled", "sharing-on")
        val originalNested = reference in setOf("want-to-read", "shared", "shared-own") || sheet
        val title = when (reference) {
            "collections", "browse", "browse-own", "companion-31", "collections-empty" -> "Collections"
            "want-to-read" -> "Want to Read"
            "shared", "shared-own" -> ""
            "companion-40" -> "Shared library"
            "sharing-off", "sharing-enabled", "sharing-on" -> "Library Sharing"
            "companion-32" -> "Create collection"
            "companion-33", "companion-55" -> "Collection"
            "companion-34" -> "Select books"
            "companion-35" -> "Edit collection"
            "companion-36", "collection-hidden" -> "Privacy & sharing"
            "companion-37" -> "Delete collection"
            "companion-38" -> "Add to collection"
            "companion-39" -> "Owner reader preview"
            "companion-54" -> "Reader Profile"
            else -> error("Unmapped collection reference $reference")
        }
        MaktabaScaffold(
            selectedTab = if (originalNested) null else BottomNavTab.COLLECTIONS,
            topBar = {
                if (compactRoot) CollectionCompactTopBar(title)
                else if (root) CollectionIllustratedTopBar(title)
                else ScreenTopBar(title,
                    onBack = if (root) null else ({}), actions = {
                        when (reference) {
                            "companion-32", "companion-35" -> CollectionHeaderArtwork(R.drawable.illus_cup_books)
                            "companion-33", "companion-36", "companion-38", "companion-39",
                            "companion-54", "companion-55", "collection-hidden" -> CollectionHeaderArtwork(R.drawable.illus_loan_shelf)
                        }
                        if (reference in setOf("shared", "shared-own", "companion-40"))
                            IconButton(onClick = {}) { Icon(Icons.Outlined.MoreHoriz, "Open library menu") }
                    })
            }
        ) { padding ->
            val scroll = rememberScrollState()
            SideEffect { sharingScroll = if (sheet) scroll else null }
            val gutter = when {
                !originalNested && (!root || compactRoot) -> 18.dp
                !sheet && LocalConfiguration.current.screenWidthDp <= 330 -> 16.dp
                else -> 20.dp
            }
            val top = if (sheet) 10.dp else if (compactRoot) 7.dp else if (root) 0.dp else if (originalNested) 6.dp else 7.dp
            Surface(Modifier.fillMaxSize().padding(padding),
                color = if (sheet) MaktabaTheme.colors.background else androidx.compose.ui.graphics.Color.Transparent,
                shape = if (sheet) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(0.dp),
                border = null) {
            Column(Modifier.fillMaxSize()
                .then(if (reference == "companion-34") Modifier else Modifier.verticalScroll(scroll))
                .padding(start = gutter, end = gutter, top = top, bottom = if (reference == "companion-34") 10.dp else 28.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactRoot) 0.dp else 12.dp)) {
                if (root) {
                    val browse = reference == "browse" || reference == "browse-own"
                    CollectionTabs(browse, {}, segmented = compactRoot)
                }
                when (reference) {
                    "collections", "companion-31", "collections-empty" -> CollectionsYoursContent(
                        collections = if (reference == "collections-empty") emptyList()
                            else if (compactRoot) listOf(collection) else listOf(
                            CustomCollection("quiet", "Quiet Weekend Reads",
                                listOf("quiet-mornings", "solitude", "winterlight"), false),
                            CustomCollection("club", "For the Book Club",
                                listOf("distant-shores", "stillwater"), false)),
                        collectionsState = LoadState.Ready(Unit), settings = LoadState.Ready(settings),
                        savedCount = 3, onWishlist = {}, onCollection = {}, onCreate = {},
                        onSharing = {}, onRetry = {},
                        savedBooks = listOf("sunday", "orchard", "light-falls").map(::book),
                        catalogue = books, compact = compactRoot, onCatalogue = if (compactRoot) ({}) else null)
                    "want-to-read" -> WantToReadContent(listOf("sunday", "orchard", "light-falls").map(::book),
                        true, null, onRetry = {}, onBook = {}, onUnsave = {})
                    "browse", "browse-own" -> {
                        Column {
                            BrowseLibrariesSearch("", {})
                            BrowseLibrariesPresentation(LoadState.Ready(
                                if (reference == "browse-own") listOf(own) + directory else directory),
                                false, {}, {}, viewerUid = "alex", catalogue = books)
                        }
                    }
                    "shared", "shared-own", "companion-40" ->
                        SharedLibraryContent(library(reference == "shared-own"), {}, {},
                            isOwner = reference == "shared-own", showMenu = reference == "companion-40", catalogue = books)
                    "sharing-off", "sharing-enabled", "sharing-on" -> LibrarySharingContent(
                        settings.copy(libraryShared = reference != "sharing-off",
                            hiddenCatalogIds = if (reference == "sharing-on") hidden else emptySet()),
                        listOf("quiet-mornings", "distant-shores", "stillwater", "solitude", "paper-garden", "winterlight").map(::book),
                        onSave = { _, _ -> }, onHide = { _, _ -> },
                        initialHiddenExpanded = reference == "sharing-on", onPreview = {})
                    "companion-32" -> CollectionCreateContent("", false, false, null, {}, {})
                    "companion-33" -> CollectionDetailContent(collection, LoadState.Ready(settings),
                        books, {}, {}, {}, {}, ownerDisplayName = "Alex Reader")
                    "companion-34" -> CollectionBookSelection(collection, LoadState.Ready(settings), books) {}
                    "companion-35" -> CollectionEditContent(collection, {}, {}, {})
                    "companion-36", "companion-39", "collection-hidden" -> CollectionPrivacyContent(
                        collection.copy(shared = reference == "collection-hidden"),
                        if (reference == "collection-hidden")
                            settings.copy(hiddenCatalogIds = collection.catalogIds.toSet()) else settings,
                        books, {}, {}, {}, initialPreview = reference == "companion-39",
                        ownerDisplayName = "Alex Reader")
                    "companion-37" -> CollectionDeleteContent(collection, false, null, {}, {})
                    "companion-38" -> {
                        AddToCollectionContent(book("sunday").title, "sunday", listOf(collection),
                            LoadState.Ready(Unit), null, {}, {}, book = book("sunday"))
                        CollectionOutline("New collection") {}
                        CollectionNote("Want to Read keeps its separate save action.")
                    }
                    "companion-54" -> ReaderProfileContent(
                        PublicReaderPage(reader, null, listOf(weekend), false), {}, {})
                    "companion-55" -> SharedCollectionContent(sharedList(), {}, {}, catalogue = books)
                }
                }
            }
        }
    }
}
