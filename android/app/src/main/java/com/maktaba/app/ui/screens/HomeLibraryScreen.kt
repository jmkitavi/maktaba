package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.R
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverCard
import com.maktaba.app.ui.components.BookGridSkeleton
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.EmptyState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency

private enum class LibraryFilter(val label: String) {
    ALL("All"),
    OWNED("On my shelf"),
    LENT_OUT("Lent out"),
    BORROWED("Borrowed"),
    OVERDUE("Overdue")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLibraryScreen(navController: NavHostController) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val focusManager = LocalFocusManager.current

    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var showAddSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    val books = LibraryRepository.books
    val hasLoaded = LibraryRepository.hasLoadedLibrary
    val normalizedQuery = searchQuery.trim().lowercase()

    val filteredBooks = books.filter { book ->
        val loan = LibraryRepository.activeLoanFor(book.id)
        val matchesFilter = when (selectedFilter) {
            LibraryFilter.ALL -> true
            LibraryFilter.OWNED -> book.status == BookStatus.OWNED
            LibraryFilter.LENT_OUT -> book.status == BookStatus.LENT_OUT
            LibraryFilter.BORROWED -> book.status == BookStatus.BORROWED
            LibraryFilter.OVERDUE ->
                loan != null && LoanTimeFormatter.urgency(loan.dueAt) == LoanUrgency.OVERDUE
        }
        val matchesSearch = normalizedQuery.isEmpty() ||
            book.title.lowercase().contains(normalizedQuery) ||
            book.author.lowercase().contains(normalizedQuery) ||
            book.genre.lowercase().contains(normalizedQuery)
        matchesFilter && matchesSearch
    }

    val unreadCount = LibraryRepository.unreadNotificationCount

    MaktabaScaffold(
        selectedTab = BottomNavTab.LIBRARY,
        onTabSelected = { navController.navigateToTab(it) },
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gutter)
                    .padding(top = spacing.sm, bottom = spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.illus_home_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(22.dp)
                )
                Spacer(Modifier.width(spacing.sm))
                Text(
                    "My library",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                IconButton(
                    onClick = { navController.navigate(Routes.Notifications.route) }
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                ) { Text(unreadCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.NotificationsNone,
                            contentDescription = if (unreadCount > 0) {
                                "Notifications, $unreadCount unread"
                            } else {
                                "Notifications"
                            },
                            tint = colors.ink
                        )
                    }
                }
            }

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gutter)
                    .heightIn(min = 52.dp)
                    .clip(MaktabaShapes.pill),
                placeholder = {
                    Text("Search books, authors, genres", style = MaterialTheme.typography.bodyMedium)
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.inkMuted)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = colors.inkMuted
                            )
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { focusManager.clearFocus() }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                    cursorColor = colors.primary,
                    focusedPlaceholderColor = colors.inkMuted,
                    unfocusedPlaceholderColor = colors.inkMuted
                )
            )

            Spacer(Modifier.height(spacing.sm))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                lazyRowItems(LibraryFilter.values().toList()) { filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(filter.label, style = MaterialTheme.typography.labelMedium)
                        },
                        shape = MaktabaShapes.pill,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.chipUnselected,
                            labelColor = colors.onChipUnselected,
                            selectedContainerColor = colors.chipSelected,
                            selectedLabelColor = colors.onChipSelected
                        ),
                        border = null
                    )
                }
            }

            if (normalizedQuery.isNotEmpty() && hasLoaded) {
                Spacer(Modifier.height(spacing.xs))
                Text(
                    "${filteredBooks.size} of ${books.size} books",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = spacing.gutter)
                )
            }

            Spacer(Modifier.height(spacing.sm))

            when {
                // Until the first Firestore snapshot lands we genuinely do not know whether
                // the shelf is empty, so we must not claim that it is.
                !hasLoaded -> BookGridSkeleton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = spacing.md,
                        end = spacing.md,
                        top = spacing.xxs,
                        bottom = 96.dp
                    )
                )

                books.isEmpty() -> EmptyState(
                    modifier = Modifier.weight(1f),
                    illustrationRes = R.drawable.illus_onboarding,
                    title = "Your shelf is empty",
                    message = "Scan a barcode to catalogue a book you own, or enter a lending " +
                        "code to claim one a friend is passing to you.",
                    primaryLabel = "Add your first book",
                    onPrimary = { navController.navigate(Routes.AddBook.route) },
                    secondaryLabel = "Borrow with a code",
                    onSecondary = { navController.navigate(Routes.ScanEnterCode.route) }
                )

                filteredBooks.isEmpty() -> EmptyState(
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
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = spacing.md,
                        end = spacing.md,
                        top = spacing.xxs,
                        // Clears the floating action button, which used to sit on top of
                        // the last row of covers.
                        bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredBooks, key = { it.id }) { book ->
                        BookCoverCard(
                            book = book,
                            loan = LibraryRepository.activeLoanFor(book.id),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                navController.navigate(Routes.BookDetail.createRoute(book.id))
                            }
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
                            navController.navigate(Routes.AddBook.route)
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
                            navController.navigate(Routes.ScanEnterCode.route)
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
