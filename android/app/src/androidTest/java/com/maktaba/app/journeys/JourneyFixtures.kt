package com.maktaba.app.journeys

import androidx.test.platform.app.InstrumentationRegistry
import com.maktaba.app.BuildConfig
import com.maktaba.app.R
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookJacketTone
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LoanRole
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.data.NotificationItem
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

object JourneyFixtures {
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC)
    private val records: List<JSONObject> by lazy {
        val raw = InstrumentationRegistry.getInstrumentation().context.assets
            .open("journey-manifest.json").bufferedReader().use { it.readText() }
        val array = JSONObject(raw).getJSONArray("fixtures")
        List(array.length()) { array.getJSONObject(it) }
    }

    fun coverUrl(id: String): String {
        val record = records.single { it.getString("id") == id }
        if (record.getJSONObject("artwork").optBoolean("generatedFallback")) return ""
        return "android.resource://${BuildConfig.APPLICATION_ID}/drawable/journey_${id.replace('-', '_')}"
    }

    private fun book(record: JSONObject, privateCopy: Boolean): Book {
        val id = record.getString("id")
        val status = when (record.optString("role")) {
            "lent" -> BookStatus.LENT_OUT
            "borrowed" -> BookStatus.BORROWED
            else -> BookStatus.OWNED
        }
        return Book(
            id = id, catalogId = id,
            title = record.getString("title"), author = record.getString("author"),
            coverRes = R.drawable.ic_book_arch, coverUrl = coverUrl(id),
            jacketTone = if (record.optString("fallback") == "olive") BookJacketTone.OLIVE else BookJacketTone.LINEN,
            status = if (privateCopy) status else BookStatus.OWNED,
            ownerId = if (!privateCopy) "" else if (status == BookStatus.BORROWED) "fixture-other-reader" else "fixture-alex",
            genre = record.getString("genre"),
            published = if (id == "sunday") "2023" else "2021",
            pages = if (id == "sunday") 272 else 224,
            description = when (id) {
                "quiet-mornings" -> "Small rituals, open windows and the pleasure of an unhurried day. " +
                    "A collection of thoughtful essays about making room for the things that matter."
                "sunday" -> "A slow journey through small towns, unlikely friendships and the places that begin to feel like home."
                else -> "A thoughtful story to return to, and to share."
            },
            format = if (record.optString("format") == "Digital") BookFormat.DIGITAL else BookFormat.PHYSICAL,
            binding = if (record.optString("format") == "Digital") "Ebook" else "Paperback"
        )
    }

    val books: List<Book> by lazy { records.filter { it.has("role") }.map { book(it, true) } }
    val catalogue: List<Book> by lazy { records.map { book(it, false) } }
    val loans: List<ActiveLoan> by lazy {
        records.filter { it.optString("role") in setOf("lent", "borrowed") }.map { record ->
            val lender = record.getString("role") == "lent"
            val id = record.getString("id")
            ActiveLoan(
                id = "loan-$id", bookId = id, counterpartyName = record.getString("person"),
                role = if (lender) LoanRole.LENDER else LoanRole.BORROWER,
                status = LoanStatus.ACTIVE,
                dueAt = LocalDate.now(clock).plusDays(record.getLong("days"))
                    .atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC),
                acceptedAt = Instant.parse(if (lender) "2026-09-30T12:00:00Z" else "2026-10-06T12:00:00Z"),
                returnRequestedAt = null, returnedAt = null,
                counterpartyUid = "fixture-${record.getString("person").lowercase()}"
            )
        }
    }

    val notifications = listOf(
        notification("reminder", "A gentle reminder", "A Field Guide to Small Things is due back to Alex in two days.", "loan_reminder", "small-things", true),
        notification("requested", "Confirm your return", "Alex has asked you to confirm handing back A Field Guide to Small Things.", "return_requested", "small-things", true),
        notification("overdue", "Four days overdue", "Letters from Distant Shores is still with Sam.", "lender_overdue", "distant-shores", true),
        notification("returned", "A book is home", "Stillwater & Other Stories has been returned.", "return_confirmed", "stillwater", false),
        notification("accepted", "Loan accepted", "Sam confirmed receiving The Art of Quiet Mornings.", "loan_invite_accepted", "quiet-mornings", false),
        notification("due-soon", "Due in two days", "A Field Guide to Small Things is due back to Alex soon.", "borrower_due_soon", "small-things", false),
        notification("due-today", "Due today", "The Restless City is due back to Kit today.", "borrower_due_today", "restless-city", false)
    )

    private fun notification(id: String, title: String, message: String, type: String, book: String, unread: Boolean) =
        NotificationItem(
            id = id, bookId = book, catalogBookId = book, title = title,
            message = message, isUnread = unread, type = type, loanId = "loan-$book",
            createdAt = clock.instant().minusSeconds(3_600)
        )
}
