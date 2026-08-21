package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.R
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.ConfirmationDialog
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.StatusPill
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.components.catalogueVisuals
import com.maktaba.app.ui.components.statusVisuals
import com.maktaba.app.ui.components.urgencyColor
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency
import kotlinx.coroutines.launch

@Composable
fun BookDetailScreen(navController: NavHostController, bookId: String) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    val book = LibraryRepository.bookById(bookId)
    if (book == null && !LibraryRepository.hasLoadedLibrary) {
        LoadingState("Loading this book")
        return
    }
    if (book == null) {
        UnavailableState(
            title = "Book unavailable",
            message = "We could not find this book. It may have been removed from your shelf.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }

    // A catalogue entry reached from the wishlist is not on the user's shelf, so it must
    // not offer shelf actions such as lending or removal.
    val isOwnedCopy = LibraryRepository.books.any { it.id == book.id }
    val loan = LibraryRepository.activeLoanFor(book.id)
    val urgency = loan?.let { LoanTimeFormatter.urgency(it.dueAt) } ?: LoanUrgency.UNKNOWN
    // A catalogue row reports status OWNED, so its pill has to be chosen by whether the
    // user actually holds a copy - not by the placeholder status on the model.
    val visuals = if (isOwnedCopy) {
        statusVisuals(colors, book.status, urgency)
    } else {
        catalogueVisuals(colors)
    }
    val onWishlist = LibraryRepository.isOnWishlist(book.catalogId)

    if (confirmRemove) {
        ConfirmationDialog(
            title = "Remove from your library?",
            message = "\"${book.title}\" will be taken off your shelf. The catalogue entry " +
                "stays, so you can add it again later.",
            confirmLabel = "Remove",
            loading = removing,
            onConfirm = {
                removing = true
                scope.launch {
                    runCatching { LibraryRepository.removeBook(book.id) }
                        .onSuccess {
                            confirmRemove = false
                            navController.popBackStack()
                        }
                        .onFailure {
                            snackbarHostState.showSnackbar(
                                it.localizedMessage ?: "We could not remove this book."
                            )
                        }
                    removing = false
                }
            },
            onDismiss = { confirmRemove = false }
        )
    }

    MaktabaScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ScreenTopBar(
                title = "Book detail",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    if (onWishlist) {
                                        LibraryRepository.removeFromWishlist(book.catalogId)
                                    } else {
                                        LibraryRepository.addToWishlist(book.catalogId)
                                    }
                                }.onFailure {
                                    snackbarHostState.showSnackbar(
                                        it.localizedMessage ?: "We could not update your wishlist."
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (onWishlist) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (onWishlist) "Remove from wishlist"
                            else "Add to wishlist",
                            tint = if (onWishlist) colors.primary else colors.inkSoft
                        )
                    }
                    if (isOwnedCopy) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "More actions",
                                    tint = colors.ink
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remove from library") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = colors.danger
                                        )
                                    },
                                    enabled = !LibraryRepository.hasOpenLoanActivity(book.id),
                                    onClick = {
                                        menuOpen = false
                                        confirmRemove = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            // Decorative shelf, clipped to the top band. It used to be pinned to the
            // centre-start at 55% of screen height, underneath the description column.
            Image(
                painter = painterResource(R.drawable.illus_book_detail),
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxHeight(0.22f)
                    .width(52.dp)
            )

            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.lg)
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(spacing.xs))
                        BookCoverImage(
                            book = book,
                            contentScale = ContentScale.Crop,
                            decorative = true,
                            modifier = Modifier
                                .fillMaxWidth(0.46f)
                                .aspectRatio(0.68f)
                                .clip(MaktabaShapes.small)
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            book.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        Text(
                            book.author.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(spacing.sm))

                        StatusPill(visuals = visuals)

                        if (loan != null) {
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                buildString {
                                    append(LoanTimeFormatter.shortRemaining(loan.dueAt))
                                    append(" · ")
                                    append(LoanTimeFormatter.formatDate(loan.dueAt))
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = urgencyColor(urgency),
                                textAlign = TextAlign.Center
                            )
                        }
                        if (book.format == BookFormat.DIGITAL) {
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                "Digital edition",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.inkSoft,
                                modifier = Modifier
                                    .clip(MaktabaShapes.pill)
                                    .background(colors.chipUnselected)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(spacing.md))
                    if (book.description.isNotBlank()) {
                        Text(
                            book.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkSoft
                        )
                        Spacer(Modifier.height(spacing.md))
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.divider)
                    )
                    Spacer(Modifier.height(spacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetaItem(
                            Icons.AutoMirrored.Filled.MenuBook,
                            "Genre",
                            book.genre.ifBlank { "Unknown" }
                        )
                        MetaItem(
                            Icons.Filled.CalendarToday,
                            "Published",
                            book.published.ifBlank { "Unknown" }
                        )
                        MetaItem(
                            Icons.Filled.Description,
                            "Pages",
                            book.pages.takeIf { it > 0 }?.toString() ?: "Unknown"
                        )
                    }
                    Spacer(Modifier.height(spacing.md))
                }

                Column(Modifier.padding(horizontal = spacing.gutter, vertical = spacing.xs)) {
                    when {
                        !isOwnedCopy -> {
                            Text(
                                "This is a catalogue entry. Add it to your shelf from the " +
                                    "add-book screen to lend it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        book.format == BookFormat.DIGITAL -> {
                            // The explanation now precedes the action, rather than sitting
                            // underneath a button that looks tappable but is not.
                            Text(
                                buildString {
                                    append("Digital editions cannot be lent through Book Haven.")
                                    if (book.physicalEditionIsbn13.isNotBlank()) {
                                        append(" The physical edition is ISBN ")
                                        append(book.physicalEditionIsbn13)
                                        append(".")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        book.status == BookStatus.OWNED -> PrimaryButton(
                            text = "Lend this book",
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.People,
                                    contentDescription = null,
                                    tint = colors.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                navController.navigate(
                                    Routes.LendBookConfig.createRoute(book.id)
                                )
                            }
                        )

                        else -> PrimaryButton(
                            text = "View this loan",
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.People,
                                    contentDescription = null,
                                    tint = colors.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                navController.navigate(Routes.ActiveLoan.createRoute(book.id))
                            }
                        )
                    }
                    Spacer(Modifier.height(spacing.xs))
                }
            }
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, label: String, value: String) {
    val colors = MaktabaTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {}
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
        Text(value, style = MaterialTheme.typography.labelMedium, color = colors.ink)
    }
}

@Preview(showBackground = true, name = "Book detail")
@Composable
private fun BookDetailScreenPreview() {
    BookHavenTheme {
        BookDetailScreen(navController = rememberNavController(), bookId = "preview")
    }
}
