package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.Book
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.EmptyState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch

/**
 * The wishlist now reads `LibraryRepository.wishlistCatalogIds`, which the repository has
 * been syncing from Firestore all along while this screen rendered a hard-coded
 * "Your wishlist is empty." It also gains the write path it never had, through the
 * catalogue list below and the heart on Book Detail.
 */
@Composable
fun WishlistScreen(navController: NavHostController) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val wishlist = LibraryRepository.wishlistBooks
    val hasLoaded = LibraryRepository.hasLoadedLibrary

    fun toggle(book: Book, add: Boolean) {
        scope.launch {
            runCatching {
                if (add) {
                    LibraryRepository.addToWishlist(book.catalogId)
                } else {
                    LibraryRepository.removeFromWishlist(book.catalogId)
                }
            }.onSuccess {
                snackbarHostState.showSnackbar(
                    if (add) "Added ${book.title} to your wishlist"
                    else "Removed ${book.title} from your wishlist"
                )
            }.onFailure {
                snackbarHostState.showSnackbar(
                    it.localizedMessage ?: "We could not update your wishlist."
                )
            }
        }
    }

    MaktabaScaffold(
        selectedTab = BottomNavTab.WISHLIST,
        onTabSelected = { navController.navigateToTab(it) },
        snackbarHostState = snackbarHostState,
        topBar = { ScreenTopBar(title = "Wishlist") }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            if (hasLoaded && wishlist.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    title = "Nothing saved yet",
                    message = "Star a book from its detail screen and it will wait for you here."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    item {
                        SectionHeading("Saved for later (${wishlist.size})")
                    }
                    items(wishlist, key = { "saved-${it.catalogId}" }) { book ->
                        WishlistRow(
                            book = book,
                            saved = true,
                            onToggle = { toggle(book, add = false) },
                            onClick = {
                                navController.navigate(
                                    Routes.BookDetail.createRoute(book.catalogId)
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
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaktabaTheme.colors.ink,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun WishlistRow(
    book: Book,
    saved: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaktabaShapes.medium)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCoverImage(
            book = book,
            contentScale = ContentScale.Crop,
            decorative = true,
            modifier = Modifier
                .width(44.dp)
                .aspectRatio(0.68f)
                .clip(MaktabaShapes.cover)
        )
        Spacer(Modifier.width(spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                book.author,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (saved) {
                    "Remove ${book.title} from wishlist"
                } else {
                    "Add ${book.title} to wishlist"
                },
                tint = if (saved) colors.primary else colors.inkMuted
            )
        }
    }
}

@Preview(showBackground = true, name = "Wishlist")
@Composable
private fun WishlistScreenPreview() {
    BookHavenTheme { WishlistScreen(navController = rememberNavController()) }
}
