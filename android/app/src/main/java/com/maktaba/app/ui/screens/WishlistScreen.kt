package com.maktaba.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.data.*
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.SansBody
import com.maktaba.app.ui.theme.SerifDisplay
import kotlinx.coroutines.launch

@Composable
fun WishlistScreen(navController: NavHostController) = WantToReadScreen(navController)

@Composable
fun WantToReadScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<String?>(null) }
    var mutationError by remember { mutableStateOf<Throwable?>(null) }
    CollectionPage(navController, "Want to Read", showBottomBar = false) {
        WantToReadContent(
            books = LibraryRepository.wishlistBooks,
            loaded = LibraryRepository.hasLoadedWishlist && LibraryRepository.hasLoadedLibrary,
            error = LibraryRepository.wishlistError ?: LibraryRepository.libraryError,
            onRetry = LibraryRepository::retryLoads,
            onBook = { navController.collectionRoute("catalogue_book", it) },
            removingId = removing,
            onUnsave = { catalogId ->
                removing = catalogId
                mutationError = null
                scope.launch {
                    try { LibraryRepository.removeFromWishlist(catalogId) }
                    catch (failure: Exception) { mutationError = failure }
                    finally { removing = null }
                }
            },
            savedReferenceCount = LibraryRepository.wishlistCatalogIds.size
        )
        mutationError?.let { CollectionNote(collectionErrorMessage(it)) }
    }
}

@Composable
fun WantToReadContent(
    books: List<Book>,
    loaded: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onBook: (String) -> Unit,
    removingId: String? = null,
    onUnsave: ((String) -> Unit)? = null,
    savedReferenceCount: Int = books.size
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(11.dp), tint = MaktabaTheme.colors.inkMuted)
            Spacer(Modifier.width(4.dp))
            Text("Private · Pinned", Modifier.weight(1f), color = MaktabaTheme.colors.inkMuted,
                style = TextStyle(fontFamily = SansBody, fontSize = 9.sp, lineHeight = 14.4.sp,
                    lineHeightStyle = CollectionLineHeight))
            if (loaded && error == null) Text("$savedReferenceCount books",
                color = MaktabaTheme.colors.inkMuted,
                style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 12.sp,
                    lineHeightStyle = CollectionLineHeight))
        }
        when {
            error != null -> {
                CollectionNote(error)
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
            !loaded -> {
                CircularProgressIndicator()
                CollectionNote("Loading saved titles…")
            }
            books.isEmpty() && savedReferenceCount > 0 -> {
                CollectionHeading("Saved title details unavailable")
                CollectionNote("Your $savedReferenceCount saved references are preserved. Their catalogue details could not be found.")
                OutlinedButton(onClick = onRetry) { Text("Retry titles") }
            }
            books.isEmpty() -> {
                CollectionHeading("Room for your next read")
                CollectionNote("Save a catalogue title from Book Detail and it will wait for you here. Want to Read is always private.")
            }
            else -> {
                if (savedReferenceCount > books.size)
                    CollectionNote("${savedReferenceCount - books.size} more saved references have unavailable catalogue details. Your saved list is unchanged.")
                books.forEach { book ->
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = MaktabaTheme.colors.surface,
                        border = BorderStroke(1.dp, MaktabaTheme.colors.divider)) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 9.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.weight(1f).clickable { onBook(book.catalogId) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BookCoverImage(book, Modifier.width(60.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(5.dp)),
                                    ContentScale.Fit, decorative = true)
                                Column(Modifier.weight(1f)) {
                                    Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(fontFamily = SerifDisplay, fontSize = 17.sp, lineHeight = 21.25.sp,
                                            lineHeightStyle = CollectionLineHeight))
                                    Spacer(Modifier.height(7.dp))
                                    Text(book.author, style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 15.sp,
                                        lineHeightStyle = CollectionLineHeight),
                                        color = MaktabaTheme.colors.inkMuted)
                                    Spacer(Modifier.height(9.dp))
                                    Text(book.genre.ifBlank { "Saved for later" },
                                        style = TextStyle(fontFamily = SansBody, fontSize = 9.sp, lineHeight = 13.5.sp,
                                            lineHeightStyle = CollectionLineHeight),
                                        color = MaktabaTheme.colors.inkMuted)
                                }
                            }
                            if (onUnsave != null) IconButton(
                                onClick = { onUnsave(book.catalogId) }, enabled = removingId == null,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Filled.Favorite, "Unsave ${book.title}",
                                    Modifier.size(18.dp), tint = MaktabaTheme.colors.terraAction)
                            }
                        }
                    }
                }
            }
        }
    }
}
