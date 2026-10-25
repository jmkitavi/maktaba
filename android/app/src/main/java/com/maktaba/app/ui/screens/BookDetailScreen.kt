package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.R
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.CollectionsRepository
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoadState
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.ConfirmationDialog
import com.maktaba.app.ui.components.EmptyState
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.StatusPill
import com.maktaba.app.ui.components.statusVisuals
import com.maktaba.app.ui.components.urgencyColor
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.time.Clock

@Composable
fun BookDetailScreen(
    navController: NavHostController,
    bookId: String,
    catalogueOnly: Boolean = false,
    onEditBook: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var saving by remember(bookId, catalogueOnly) { mutableStateOf(false) }
    var hiding by remember(bookId, catalogueOnly) { mutableStateOf(false) }
    var removing by remember(bookId, catalogueOnly) { mutableStateOf(false) }
    var confirmRemove by remember(bookId, catalogueOnly) { mutableStateOf(false) }
    val selection = selectLibraryDetail(
        bookId, catalogueOnly, LibraryRepository.books, LibraryRepository.maktabaCollection
    )
    val book = selection?.book
    val privateCopy = selection?.privateCopy == true
    val sharing = CollectionsRepository.sharingSettings
    val settings = (sharing as? LoadState.Ready)?.value
    val state = LibraryBookDetailState(
        selection = selection,
        loaded = LibraryRepository.hasLoadedLibrary &&
            (!privateCopy || LibraryRepository.hasLoadedLoans),
        error = LibraryRepository.libraryError ?: if (privateCopy) LibraryRepository.loansError else null,
        loan = if (privateCopy && book != null) LibraryRepository.activeLoanFor(book.id) else null,
        saved = book?.let { LibraryRepository.isOnWishlist(it.catalogId) } == true,
        savedLoaded = LibraryRepository.hasLoadedWishlist,
        savedError = LibraryRepository.wishlistError,
        hasOpenActivity = privateCopy && book != null && LibraryRepository.hasOpenLoanActivity(book.id),
        allowVisibilityManagement = !catalogueOnly,
        hidden = book?.let { settings?.hiddenCatalogIds?.contains(it.catalogId) },
        sharingError = when (sharing) {
            is LoadState.Error -> sharing.cause.localizedMessage ?: "Sharing settings could not load."
            LoadState.NotFound -> "Sharing settings are unavailable."
            else -> null
        }
    )

    if (confirmRemove && state.loaded && state.error == null && book != null &&
        selection?.canRemove == true && !state.hasOpenActivity
    ) {
        ConfirmationDialog(
            title = "Remove from your library?",
            message = "\"${book.title}\" will be taken off your shelf. Its catalogue entry, " +
                "saved status and collection memberships stay unchanged.",
            confirmLabel = "Remove",
            loading = removing,
            onConfirm = {
                if (!removing) {
                    removing = true
                    scope.launch(Dispatchers.Main.immediate) {
                        runCatching { LibraryRepository.removeBook(book.id) }
                            .onSuccess { confirmRemove = false; navController.popBackStack() }
                            .onFailure {
                                snackbar.showSnackbar(it.localizedMessage ?: "We could not remove this book.")
                            }
                        removing = false
                    }
                }
            },
            onDismiss = { if (!removing) confirmRemove = false }
        )
    }
    BookDetailContent(
        state = state,
        snackbarHostState = snackbar,
        saving = saving,
        hiding = hiding,
        onBack = { navController.popBackStack() },
        onRetry = LibraryRepository::retryLoads,
        onSave = {
            if (book != null && !saving && state.savedLoaded && state.savedError == null) {
                saving = true
                scope.launch(Dispatchers.Main.immediate) {
                    runCatching {
                        if (state.saved) LibraryRepository.removeFromWishlist(book.catalogId)
                        else LibraryRepository.addToWishlist(book.catalogId)
                    }.onFailure {
                        snackbar.showSnackbar(it.localizedMessage ?: "We could not update Want to Read.")
                    }
                    saving = false
                }
            }
        },
        onLend = {
            if (privateCopy && book != null && selection?.canLend == true) {
                navController.navigate(Routes.LendBookConfig.createRoute(book.id))
            }
        },
        onLoan = {
            if (privateCopy && book != null) {
                navController.navigate(Routes.ActiveLoan.createRoute(book.id))
            }
        },
        onRemove = {
            if (state.loaded && state.error == null && privateCopy &&
                selection?.canRemove == true && !state.hasOpenActivity
            ) {
                confirmRemove = true
            }
        },
        onAddToCollection = {
            if (book != null) navController.navigate(Routes.AddToCollection.createRoute(book.catalogId))
        },
        onToggleHidden = {
            if (!catalogueOnly && book != null && state.hidden != null && !hiding) {
                hiding = true
                scope.launch(Dispatchers.Main.immediate) {
                    runCatching { CollectionsRepository.setTitleHidden(book.catalogId, !state.hidden) }
                        .onFailure {
                            snackbar.showSnackbar(it.localizedMessage ?: "We could not update title visibility.")
                        }
                    hiding = false
                }
            }
        },
        onManageSharing = { navController.navigate(Routes.LibrarySharing.route) },
        onEditBook = if (!catalogueOnly && selection?.canEdit == true && book != null) {
            onEditBook ?: {
                navController.navigate(Routes.EditOwnedBook.createRoute(book.id))
            }
        } else null
    )
}

@Composable
fun BookDetailContent(
    state: LibraryBookDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onLend: () -> Unit,
    onLoan: () -> Unit,
    onRemove: () -> Unit,
    onAddToCollection: () -> Unit,
    onToggleHidden: () -> Unit,
    onManageSharing: () -> Unit,
    saving: Boolean = false,
    hiding: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    clock: Clock = Clock.systemDefaultZone(),
    onEditBook: (() -> Unit)? = null
) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    var menuOpen by remember(state.selection?.book?.id) { mutableStateOf(false) }
    val selection = state.selection
    val book = selection?.book
    val privateCopy = selection?.privateCopy == true
    // Even a fixture carrying a loan must never leak that loan into catalogue mode.
    val loan = state.loan.takeIf { privateCopy }
    val urgency = loan?.let { LoanTimeFormatter.urgency(it.dueAt, clock) } ?: LoanUrgency.UNKNOWN

    MaktabaScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            Box(Modifier.statusBarsPadding()) {
                ScreenTopBar(
                    title = "",
                    modifier = Modifier.height(62.dp),
                    onBack = onBack,
                    actions = {
                        if (book != null && state.error == null && state.loaded) {
                            IconButton(
                                onClick = onSave,
                                enabled = state.savedLoaded && state.savedError == null && !saving
                            ) {
                                Icon(
                                    if (state.saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (state.saved) "Remove from Want to Read" else "Save to Want to Read",
                                    tint = if (state.saved) colors.primary else colors.ink
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        when {
            state.error != null -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                title = "This book could not load", message = state.error,
                primaryLabel = "Try again", onPrimary = onRetry,
                secondaryLabel = "Back", onSecondary = onBack
            )
            !state.loaded -> Box(Modifier.fillMaxSize().padding(innerPadding)) {
                LoadingState("Loading this book")
            }
            book == null -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                title = "Book unavailable",
                message = "This book may have been removed or is no longer available.",
                primaryLabel = "Back", onPrimary = onBack
            )
            else -> Column(
                Modifier.fillMaxSize().padding(innerPadding)
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.gutter)
                ) {
                    DetailHero {
                        BookCoverImage(
                            book = book, contentScale = ContentScale.Fit, decorative = true,
                            modifier = Modifier.width(124.dp).aspectRatio(2f / 3f)
                                .shadow(8.dp, MaktabaShapes.cover).clip(MaktabaShapes.cover)
                        )
                        Spacer(Modifier.height(11.dp))
                        Text(
                            book.title, style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 25.sp, lineHeight = 29.sp, letterSpacing = (-0.5).sp
                            ),
                            color = colors.ink, textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            "by ${book.author}", style = MaterialTheme.typography.bodySmall.copy(
                                lineHeight = 19.2.sp, lineHeightStyle = CoreLibraryLineHeight
                            ),
                            color = colors.inkSoft, textAlign = TextAlign.Center
                        )
                        if (privateCopy) {
                            Box(
                                Modifier.heightIn(min = 34.dp).padding(top = 8.dp, bottom = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StatusPill(
                                    visuals = statusVisuals(colors, book.status).copy(
                                        label = when (book.status) {
                                            BookStatus.OWNED -> "On my shelf"
                                            BookStatus.LENT_OUT -> "Lent out"
                                            BookStatus.BORROWED -> "Borrowed"
                                        }
                                    ),
                                    compact = true
                                )
                            }
                        } else {
                            Text(
                                "Catalogue Title",
                                modifier = Modifier.padding(top = 12.dp, bottom = 3.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 14.4.sp),
                                color = colors.inkMuted
                            )
                        }
                    }
                    Spacer(Modifier.height(23.dp))
                    Text(
                        "ABOUT THE BOOK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp, letterSpacing = 0.7.sp, fontWeight = FontWeight.SemiBold
                        ), color = colors.ink
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        book.description.ifBlank { "No description is available for this edition." },
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 21.6.sp, lineHeightStyle = CoreLibraryLineHeight
                        ), color = colors.inkSoft
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp)) {
                        HorizontalDivider(color = colors.divider)
                        Box(
                            Modifier.align(Alignment.Center).requiredSize(11.dp)
                                .background(colors.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(5.dp).graphicsLayer { rotationZ = 45f }
                                    .background(colors.divider)
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        DetailMetaItem("Genre", book.genre.ifBlank { "Unknown" }, Modifier.weight(1f))
                        DetailMetaItem("Published", book.published.ifBlank { "Unknown" }, Modifier.weight(1f))
                        DetailMetaItem("Pages", book.pages.takeIf { it > 0 }?.toString() ?: "Unknown", Modifier.weight(1f))
                    }
                    HorizontalDivider(color = colors.divider)
                    Row(
                        Modifier.padding(top = 14.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook, contentDescription = null,
                            tint = colors.inkMuted, modifier = Modifier.size(14.dp)
                        )
                        Text(
                            when (book.format) {
                                BookFormat.PHYSICAL -> "Physical edition"
                                BookFormat.DIGITAL -> "Digital edition"
                                BookFormat.UNKNOWN -> "Edition format unavailable"
                            },
                            color = colors.inkMuted, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                        )
                    }
                    if (loan != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                            color = colors.ochre,
                            shape = MaktabaShapes.medium,
                            border = BorderStroke(1.dp, colors.divider)
                        ) {
                            Row(
                                Modifier.padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                Box(
                                    Modifier.size(34.dp).background(colors.terracotta, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.PersonOutline, contentDescription = null,
                                        tint = colors.terraAction, modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (if (loan.isLender) "With " else "From ") +
                                            loan.counterpartyName.ifBlank { "another reader" },
                                        color = colors.ink,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row(
                                        Modifier.padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Schedule, contentDescription = null,
                                            tint = urgencyColor(urgency), modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            LoanTimeFormatter.shortRemaining(loan.dueAt, clock) + " · " +
                                                LoanTimeFormatter.formatDate(loan.dueAt, clock.zone),
                                            color = urgencyColor(urgency),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.savedError != null) {
                        Text(state.savedError, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                        TextButton(onClick = onRetry) { Text("Retry saved titles", color = colors.primary) }
                    } else if (!state.savedLoaded) {
                        Text(
                            "Loading saved status…", style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                    Box {
                        Box(
                            Modifier.heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClick = { menuOpen = true }),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                "More book actions", color = colors.terraAction,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (selection?.canEdit == true && onEditBook != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit book details") },
                                    onClick = { menuOpen = false; onEditBook() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Add to collection") },
                                onClick = { menuOpen = false; onAddToCollection() }
                            )
                            if (state.allowVisibilityManagement) {
                                if (state.hidden != null && state.sharingError == null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (state.hidden) "Show in shared views" else "Hide from shared views")
                                        },
                                        enabled = !hiding,
                                        onClick = { menuOpen = false; onToggleHidden() }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Manage shared visibility") },
                                        onClick = { menuOpen = false; onManageSharing() }
                                    )
                                }
                            }
                            if (privateCopy && book.status != BookStatus.BORROWED) {
                                DropdownMenuItem(
                                    text = { Text("Remove from library") },
                                    enabled = selection?.canRemove == true && !state.hasOpenActivity,
                                    onClick = { menuOpen = false; onRemove() }
                                )
                            }
                        }
                    }
                    if (privateCopy && state.hasOpenActivity) {
                        Text(
                            "This copy cannot be removed while a loan or lending invitation is open.",
                            color = colors.inkMuted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (privateCopy && book.format != BookFormat.PHYSICAL && loan == null) {
                        Text(
                            if (book.format == BookFormat.DIGITAL) {
                                "Digital editions cannot be lent through Book Haven." +
                                    book.physicalEditionIsbn13.takeIf(String::isNotBlank)
                                        ?.let { " Physical edition ISBN: $it." }.orEmpty()
                            } else "A confirmed physical edition is needed before this book can be lent.",
                            color = colors.inkMuted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(spacing.lg))
                }
                if (privateCopy && (loan != null || selection?.canLend == true)) {
                    HorizontalDivider(color = colors.divider)
                    Box(
                        Modifier.background(colors.backgroundElevated)
                            .padding(horizontal = spacing.gutter, vertical = spacing.sm)
                    ) {
                        PrimaryButton(
                            text = if (loan != null) "View This Loan"
                            else if (state.hasOpenActivity) "Continue Lending" else "Lend This Book",
                            onClick = if (loan != null) onLoan else onLend,
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.NorthEast, contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHero(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = MaktabaTheme.colors
    Box(
        Modifier.fillMaxWidth().drawBehind {
            val center = Offset(size.width * .5f, size.height * .3f)
            val radius = size.width * .73f
            scale(scaleX = 1f, scaleY = size.height / size.width, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(colors.sage, colors.sage.copy(alpha = 0f)),
                        center = center, radius = radius
                    ),
                    radius = radius, center = center
                )
            }
        }
    ) {
        Image(
            painter = painterResource(R.drawable.illus_book_detail),
            contentDescription = null,
            contentScale = ContentScale.FillHeight,
            alignment = Alignment.TopStart,
            modifier = Modifier.offset(x = (-20).dp).size(width = 56.dp, height = 245.dp)
                .clipToBounds()
                .graphicsLayer {
                    alpha = if (colors.isDark) .33f else .5f
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // Match the reference's elliptical mask without shrinking the full illustration.
                    val radius = size.height * .8f
                    val xScale = size.width / radius
                    scale(scaleX = xScale, scaleY = 1f, pivot = Offset.Zero) {
                        drawRect(
                            brush = Brush.radialGradient(
                                0f to colors.sage,
                                .55f to colors.sage,
                                1f to colors.sage.copy(alpha = 0f),
                                center = Offset(0f, size.height * .2f), radius = radius
                            ),
                            size = Size(size.width / xScale, size.height),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        )
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun DetailMetaItem(label: String, value: String, modifier: Modifier) {
    val colors = MaktabaTheme.colors
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(
            label, style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp, lineHeight = 16.sp, lineHeightStyle = CoreLibraryLineHeight
            ),
            color = colors.inkMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value, style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 19.2.sp, lineHeightStyle = CoreLibraryLineHeight
            ), color = colors.ink
        )
    }
}
