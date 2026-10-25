package com.maktaba.app.data

data class OwnedBookDraft(
    val title: String,
    val author: String,
    val binding: String = "",
    val isbn13: String = "",
    val isbn10: String = "",
    val publisher: String = "",
    val published: String = "",
    val genre: String = "",
    val pages: String = "",
    val description: String = ""
) {
    fun validationError(): String? = when {
        title.isBlank() || title.trim().length > 500 -> "Enter a title of 1–500 characters."
        author.isBlank() || author.trim().length > 500 -> "Enter an author of 1–500 characters."
        binding.length > 100 || publisher.length > 300 || published.length > 40 || genre.length > 200 ->
            "Some edition details are too long. Shorten them and try again."
        isbn13.isNotBlank() && !isValidIsbn13(isbn13) -> "Enter a valid ISBN-13 or leave it blank."
        isbn10.isNotBlank() && !isValidIsbn10(isbn10) -> "Enter a valid ISBN-10 or leave it blank."
        pages.isNotBlank() && (pages.toIntOrNull() == null || pages.toInt() !in 0..100_000) ->
            "Enter a page count between 0 and 100,000."
        description.length > 20_000 -> "Keep the description under 20,000 characters."
        else -> null
    }

    fun toFields(): Map<String, Any> {
        require(validationError() == null) { validationError().orEmpty() }
        return mapOf(
            "title" to title.trim(), "author" to author.trim(), "binding" to binding.trim(),
            "isbn13" to compactIsbn(isbn13), "isbn10" to compactIsbn(isbn10),
            "publisher" to publisher.trim(), "published" to published.trim(), "genre" to genre.trim(),
            "pages" to (pages.toIntOrNull() ?: 0), "description" to description.trim()
        )
    }

    companion object {
        fun from(book: Book) = OwnedBookDraft(
            book.title, book.author, book.binding, book.isbn13, book.isbn10, book.publisher,
            book.published, book.genre, book.pages.takeIf { it > 0 }?.toString().orEmpty(), book.description
        )
    }
}

// Overrides are private copy presentation only; edition eligibility remains canonical.
internal fun applyOwnedBookDetails(book: Book, details: Map<*, *>): Book = book.copy(
    title = details["title"] as? String ?: book.title,
    author = details["author"] as? String ?: book.author,
    binding = details["binding"] as? String ?: book.binding,
    isbn13 = details["isbn13"] as? String ?: book.isbn13,
    isbn10 = details["isbn10"] as? String ?: book.isbn10,
    publisher = details["publisher"] as? String ?: book.publisher,
    published = details["published"] as? String ?: book.published,
    genre = details["genre"] as? String ?: book.genre,
    pages = (details["pages"] as? Number)?.toInt() ?: book.pages,
    description = details["description"] as? String ?: book.description
)
