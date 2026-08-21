package com.maktaba.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.maktaba.app.data.Book

/**
 * @param decorative pass true when the surrounding card already announces the book to
 * screen readers, so the cover is not read out a second time.
 */
@Composable
fun BookCoverImage(
    book: Book,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    decorative: Boolean = false
) {
    val fallback = painterResource(book.coverRes)
    AsyncImage(
        model = book.coverUrl.takeIf { it.isNotBlank() },
        contentDescription = if (decorative) null else "Cover of ${book.title}",
        placeholder = fallback,
        error = fallback,
        fallback = fallback,
        contentScale = contentScale,
        modifier = modifier
    )
}
