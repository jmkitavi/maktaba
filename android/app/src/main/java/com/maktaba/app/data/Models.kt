package com.maktaba.app.data

import androidx.annotation.DrawableRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.maktaba.app.R

enum class BookStatus { OWNED, LENT_OUT, BORROWED }

data class Book(
    val id: String,
    val title: String,
    val author: String,
    @DrawableRes val coverRes: Int,
    val status: BookStatus,
    val genre: String = "Fiction",
    val published: String = "2020",
    val pages: Int = 320,
    val description: String = ""
)

/** A currently-active lend/borrow, linking a book to the other party and a due date. */
data class ActiveLoan(
    val bookId: String,
    val counterpartyName: String,
    val dueDate: String,
    /** true if the current user is the lender (they own the book); false if they're the borrower. */
    val isLender: Boolean
)

data class NotificationItem(
    val id: String,
    val bookId: String,
    val title: String,
    val message: String,
    val isUnread: Boolean = true
)

object SampleData {
    private val seedBooks = listOf(
        Book(
            id = "night_circus",
            title = "The Night Circus",
            author = "Erin Morgenstern",
            coverRes = R.drawable.cover_the_night_circus,
            status = BookStatus.OWNED,
            genre = "Fantasy",
            published = "2011",
            pages = 387,
            description = "A mesmerizing tale of magic, mystery, and star-crossed love. Behind the black-and-white tents of Le Cirque des Rêves, a timeless competition unfolds—where only one can win, and the price is everything."
        ),
        Book(
            id = "atomic_habits",
            title = "Atomic Habits",
            author = "James Clear",
            coverRes = R.drawable.cover_atomic_habits,
            status = BookStatus.OWNED,
            genre = "Self-Help",
            published = "2018",
            pages = 320,
            description = "An easy and proven way to build good habits and break bad ones, one tiny change at a time."
        ),
        Book(
            id = "hobbit",
            title = "The Hobbit",
            author = "J. R. R. Tolkien",
            coverRes = R.drawable.cover_hobbit,
            status = BookStatus.LENT_OUT,
            genre = "Fantasy",
            published = "2011",
            pages = 387,
            description = "A reluctant hobbit sets out on an unexpected journey to reclaim a lost kingdom, encountering dwarves, dragons, and riddles in the dark along the way."
        ),
        Book(
            id = "crawdads",
            title = "Where the Crawdads Sing",
            author = "Delia Owens",
            coverRes = R.drawable.cover_crawdads,
            status = BookStatus.OWNED,
            genre = "Mystery",
            published = "2018",
            pages = 384,
            description = "A haunting tale of a girl who raised herself in the marshes of the deep South, and the mystery that surrounds her."
        ),
        Book(
            id = "mockingbird",
            title = "To Kill a Mockingbird",
            author = "Harper Lee",
            coverRes = R.drawable.cover_mockingbird,
            status = BookStatus.BORROWED,
            genre = "Classic",
            published = "1960",
            pages = 336,
            description = "A young girl comes of age in the Depression-era South, confronting prejudice and injustice through her father's eyes."
        ),
        Book(
            id = "alchemist",
            title = "The Alchemist",
            author = "Paulo Coelho",
            coverRes = R.drawable.cover_alchemist,
            status = BookStatus.OWNED,
            genre = "Fiction",
            published = "1988",
            pages = 208,
            description = "A shepherd boy travels from Spain to the Egyptian desert in search of a treasure, discovering his personal legend along the way."
        ),
        Book(
            id = "midnight_library",
            title = "The Midnight Library",
            author = "Matt Haig",
            coverRes = R.drawable.cover_midnight_library,
            status = BookStatus.LENT_OUT,
            genre = "Fiction",
            published = "2020",
            pages = 304,
            description = "Between life and death is a library stocked with books representing every possible version of the life you could have lived."
        )
    )

    /** Unfiltered reference list — mutable copies live in LibraryRepository. */
    val books: List<Book> get() = seedBooks

    fun bookById(id: String): Book? = seedBooks.find { it.id == id }

    val seedActiveLoans = listOf(
        ActiveLoan(bookId = "hobbit", counterpartyName = "Maya Chen", dueDate = "Nov 28, 2025", isLender = true),
        ActiveLoan(bookId = "mockingbird", counterpartyName = "Sam Rivera", dueDate = "Dec 3, 2025", isLender = false),
        ActiveLoan(bookId = "midnight_library", counterpartyName = "Alex Kim", dueDate = "Nov 24, 2025", isLender = true)
    )

    val seedNotifications = listOf(
        NotificationItem(
            id = "n1",
            bookId = "midnight_library",
            title = "Return reminder",
            message = "\"The Midnight Library\" is due back in 2 days."
        ),
        NotificationItem(
            id = "n2",
            bookId = "hobbit",
            title = "Loan update",
            message = "Maya Chen hasn't returned \"The Hobbit\" yet — due Nov 28."
        ),
        NotificationItem(
            id = "n3",
            bookId = "mockingbird",
            title = "Borrow confirmed",
            message = "You confirmed receipt of \"To Kill a Mockingbird\" from Sam Rivera.",
            isUnread = false
        )
    )
}

/**
 * Simple in-memory, app-lifetime shared repository so screens reflect the same mutable
 * mock-data state (no backend/persistence — resets on process death).
 */
object LibraryRepository {
    val books: SnapshotStateList<Book> = SampleData.books.toMutableStateList()
    val activeLoans: SnapshotStateList<ActiveLoan> = SampleData.seedActiveLoans.toMutableStateList()
    val notifications: SnapshotStateList<NotificationItem> = SampleData.seedNotifications.toMutableStateList()
    /** Mock lending code -> bookId, populated as codes are generated in Lend Book Config /
     * Share Lending Code, and consulted by Scan/Enter Code to resolve the borrow flow. */
    val lendingCodes: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String> =
        androidx.compose.runtime.mutableStateMapOf("BOK-7291" to "hobbit")

    fun bookById(id: String): Book? = books.find { it.id == id }

    fun activeLoanFor(bookId: String): ActiveLoan? = activeLoans.find { it.bookId == bookId }

    /** Deterministic-looking mock code per book, generated (and cached) on demand. */
    fun lendingCodeFor(bookId: String): String {
        lendingCodes.entries.find { it.value == bookId }?.let { return it.key }
        val code = "BOK-" + (1000 + (bookId.hashCode().mod(9000))).toString()
        lendingCodes[code] = bookId
        return code
    }

    fun bookIdForCode(code: String): String? = lendingCodes[code.trim().uppercase()]

    fun addBook(title: String, author: String, coverRes: Int) {
        books.add(
            0,
            Book(
                id = "book_${System.currentTimeMillis()}",
                title = title,
                author = author,
                coverRes = coverRes,
                status = BookStatus.OWNED
            )
        )
    }

    /** Lender starts a loan: flips the book to LENT_OUT and records the active loan. */
    fun startLending(bookId: String, borrowerName: String, dueDate: String): String {
        updateStatus(bookId, BookStatus.LENT_OUT)
        activeLoans.removeAll { it.bookId == bookId }
        activeLoans.add(ActiveLoan(bookId = bookId, counterpartyName = borrowerName, dueDate = dueDate, isLender = true))
        return lendingCodeFor(bookId)
    }

    /** Borrower confirms receipt: creates (or flips) a BORROWED book entry and an active loan. */
    fun confirmBorrow(bookId: String, lenderName: String, dueDate: String) {
        if (books.any { it.id == bookId }) {
            updateStatus(bookId, BookStatus.BORROWED)
        }
        activeLoans.removeAll { it.bookId == bookId }
        activeLoans.add(ActiveLoan(bookId = bookId, counterpartyName = lenderName, dueDate = dueDate, isLender = false))
    }

    /** Either party confirms the book has been returned: clears the loan, book becomes OWNED again. */
    fun confirmReturn(bookId: String) {
        updateStatus(bookId, BookStatus.OWNED)
        activeLoans.removeAll { it.bookId == bookId }
    }

    private fun updateStatus(bookId: String, status: BookStatus) {
        val index = books.indexOfFirst { it.id == bookId }
        if (index >= 0) {
            books[index] = books[index].copy(status = status)
        }
    }

    fun markNotificationRead(id: String) {
        val index = notifications.indexOfFirst { it.id == id }
        if (index >= 0) {
            notifications[index] = notifications[index].copy(isUnread = false)
        }
    }

    val unreadNotificationCount get() = notifications.count { it.isUnread }
}
