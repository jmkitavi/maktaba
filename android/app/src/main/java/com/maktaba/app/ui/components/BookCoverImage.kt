package com.maktaba.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.maktaba.app.data.Book

private const val DEFAULT_BOOK_COVER_URL =
    "https://images.isbndb.com/covers/18052683482712.jpg"

@Composable
fun BookCoverImage(
    book: Book,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val fallback = painterResource(book.coverRes)
    AsyncImage(
        model = book.coverUrl.ifBlank { DEFAULT_BOOK_COVER_URL },
        contentDescription = book.title,
        placeholder = fallback,
        error = fallback,
        fallback = fallback,
        contentScale = contentScale,
        modifier = modifier
    )
}
