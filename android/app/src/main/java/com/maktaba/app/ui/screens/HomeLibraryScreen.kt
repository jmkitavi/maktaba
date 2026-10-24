package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.R
import com.maktaba.app.data.Book
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverCard
import com.maktaba.app.ui.components.BookGridSkeleton
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.EmptyState
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import java.time.Clock

@Composable
fun HomeLibraryScreen(navController: NavHostController) {
    HomeLibraryContent(
        books = LibraryRepository.books,
        loans = LibraryRepository.activeLoans,
        hasLoaded = LibraryRepository.hasLoadedLibrary,
        error = LibraryRepository.libraryError,
        loansLoaded = LibraryRepository.hasLoadedLoans,
        loansError = LibraryRepository.loansError,
        unreadCount = LibraryRepository.unreadNotificationCount,
        onBook = { navController.navigate(Routes.BookDetail.createRoute(it.id)) },
        onAdd = { navController.navigate(Routes.AddBook.route) },
        onBorrow = { navController.navigate(Routes.ScanEnterCode.route) },
        onNotifications = { navController.navigate(Routes.Notifications.route) },
        onTab = { navController.navigateToTab(it) },
        onRetry = LibraryRepository::retryLoads
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLibraryContent(
    books: List<Book>,
    loans: List<ActiveLoan>,
    hasLoaded: Boolean,
    error: String?,
    loansLoaded: Boolean,
    loansError: String?,
    unreadCount: Int,
    onBook: (Book) -> Unit,
    onAdd: () -> Unit,
    onBorrow: () -> Unit,
    onNotifications: () -> Unit,
    onTab: (BottomNavTab) -> Unit,
    onRetry: () -> Unit,
    initialFilter: LibraryFilter = LibraryFilter.ALL,
    initialQuery: String = "",
    clock: Clock = Clock.systemDefaultZone()
) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val narrowLayout = LocalConfiguration.current.screenWidthDp <= 339
    val columns = if (narrowLayout) 2 else 3
    val gutter = if (narrowLayout) 16.dp else spacing.gutter
    val focusManager = LocalFocusManager.current

    var selectedFilter by rememberSaveable { mutableStateOf(initialFilter) }
    var showAddSheet by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf(initialQuery) }
    val sheetState = rememberModalBottomSheetState()

    val normalizedQuery = searchQuery.trim().lowercase()
    val filteredBooks = filterLibraryBooks(books, loans, selectedFilter, searchQuery, clock)
    val feedError = error ?: loansError
    val phase = libraryFeedPhase(
        hasLoaded && loansLoaded, feedError, books.size, filteredBooks.size
    )

    MaktabaScaffold(
        selectedTab = BottomNavTab.LIBRARY,
        onTabSelected = onTab,
        topBar = {
            ScreenTopBar(
                title = "My Library",
                branded = true,
                illustrationRes = R.drawable.illus_home_icon,
                actions = {
                    BadgedBox(badge = {
                        if (unreadCount > 0) {
                            Badge(
                                modifier = Modifier.height(13.dp).widthIn(min = 13.dp),
                                containerColor = colors.primary, contentColor = colors.onPrimary
                            ) {
                                Text(
                                    unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp)
                                )
                            }
                        }
                    }) {
                        IconButton(
                            onClick = onNotifications,
                            modifier = Modifier.size(44.dp).border(1.dp, colors.divider, CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.NotificationsNone,
                                contentDescription = if (unreadCount > 0) {
                                    "Notifications, $unreadCount unread"
                                } else "Notifications",
                                tint = colors.ink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add or borrow a book")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = gutter)
                    .heightIn(min = 44.dp)
                    .clip(MaktabaShapes.small)
                    .border(1.dp, colors.divider, MaktabaShapes.small)
                    .background(colors.backgroundElevated)
                    .semantics { contentDescription = "Search books, authors, genres" },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { focusManager.clearFocus() }
                ),
                decorationBox = { innerField ->
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Search, contentDescription = null,
                            tint = colors.inkMuted, modifier = Modifier.size(18.dp)
                        )
                        Box(Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search books, authors, genres",
                                    style = MaterialTheme.typography.bodySmall, color = colors.inkMuted
                                )
                            }
                            innerField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, "Clear search", tint = colors.inkMuted)
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                lazyRowItems(LibraryFilter.values().toList()) { filter ->
                    val isSelected = filter == selectedFilter
                    Column(
                        Modifier.width(IntrinsicSize.Max).selectable(
                            selected = isSelected, role = Role.Tab,
                            onClick = { selectedFilter = filter }
                        ).padding(top = 10.dp)
                    ) {
                        Text(
                            filter.label, style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) colors.primary else colors.inkSoft
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier.fillMaxWidth().height(2.dp)
                                .background(if (isSelected) colors.primary else androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = colors.divider)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "THE COLLECTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.ink
                )
                Text(
                    if (feedError != null) "Unavailable" else if (!hasLoaded) "Loading…" else if (normalizedQuery.isNotEmpty() ||
                        selectedFilter != LibraryFilter.ALL) {
                        "${filteredBooks.size} of ${books.size} books"
                    } else "${books.size} books",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp, fontWeight = FontWeight.Normal
                    ),
                    color = colors.inkMuted
                )
            }

            Spacer(Modifier.height(17.dp))

            when {
                phase == LibraryFeedPhase.ERROR -> EmptyState(
                    modifier = Modifier.weight(1f),
                    title = "Your library could not load",
                    message = feedError.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = onRetry
                )

                phase == LibraryFeedPhase.LOADING -> BookGridSkeleton(
                    modifier = Modifier.weight(1f),
                    columns = columns,
                    contentPadding = PaddingValues(
                        start = gutter,
                        end = gutter,
                        top = 0.dp,
                        bottom = 96.dp
                    )
                )

                phase == LibraryFeedPhase.EMPTY -> EmptyState(
                    modifier = Modifier.weight(1f),
                    illustrationRes = R.drawable.illus_home_icon,
                    title = "Your shelf is empty",
                    message = "Scan a barcode to catalogue a book you own, or enter a lending " +
                        "code to claim one a friend is passing to you.",
                    primaryLabel = "Add your first book",
                    onPrimary = onAdd,
                    secondaryLabel = "Borrow with a code",
                    onSecondary = onBorrow
                )

                phase == LibraryFeedPhase.NO_RESULTS -> EmptyState(
                    modifier = Modifier.weight(1f),
                    title = if (normalizedQuery.isNotEmpty()) "No matches" else "Nothing here yet",
                    message = if (normalizedQuery.isNotEmpty()) {
                        "No book on your shelf matches \"${searchQuery.trim()}\"."
                    } else {
                        when (selectedFilter) {
                            LibraryFilter.LENT_OUT -> "You have not lent any book out right now."
                            LibraryFilter.BORROWED -> "You are not borrowing anything at the moment."
                            LibraryFilter.OVERDUE -> "Nothing is overdue. Everything is on schedule."
                            else -> "No books match this filter."
                        }
                    },
                    primaryLabel = if (normalizedQuery.isNotEmpty()) "Clear search" else null,
                    onPrimary = if (normalizedQuery.isNotEmpty()) {
                        { searchQuery = "" }
                    } else {
                        null
                    }
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = gutter,
                        end = gutter,
                        top = 0.dp,
                        // Clears the floating action button, which used to sit on top of
                        // the last row of covers.
                        bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (narrowLayout) 14.dp else 11.dp),
                    verticalArrangement = Arrangement.spacedBy(if (narrowLayout) 24.dp else 22.dp)
                ) {
                    items(filteredBooks, key = { it.id }) { book ->
                        BookCoverCard(
                            book = book,
                            loan = loans.firstOrNull { it.bookId == book.id },
                            clock = clock,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onBook(book) }
                        )
                    }
                }
            }
        }

        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                sheetState = sheetState,
                containerColor = colors.backgroundElevated,
                shape = MaktabaShapes.sheet
            ) {
                Column(Modifier.padding(bottom = spacing.lg)) {
                    Text(
                        "What would you like to do?",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.ink,
                        modifier = Modifier
                            .padding(horizontal = spacing.gutter)
                            .padding(bottom = spacing.sm)
                            .semantics { heading() }
                    )
                    ListItem(
                        headlineContent = { Text("Add a book", color = colors.ink) },
                        supportingContent = {
                            Text("Scan the barcode or enter an ISBN", color = colors.inkMuted)
                        },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = colors.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = colors.backgroundElevated),
                        modifier = Modifier.clickable {
                            showAddSheet = false
                            onAdd()
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Borrow a book", color = colors.ink) },
                        supportingContent = {
                            Text("Scan or enter a lending code", color = colors.inkMuted)
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = null,
                                tint = colors.accent
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = colors.backgroundElevated),
                        modifier = Modifier.clickable {
                            showAddSheet = false
                            onBorrow()
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "My library")
@Composable
private fun HomeLibraryScreenPreview() {
    BookHavenTheme {
        HomeLibraryScreen(navController = rememberNavController())
    }
}
