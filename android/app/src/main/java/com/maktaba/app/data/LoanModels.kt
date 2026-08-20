package com.maktaba.app.data

import java.time.Instant

enum class BookFormat {
    PHYSICAL,
    DIGITAL,
    UNKNOWN;

    companion object {
        fun from(value: String?, binding: String?): BookFormat {
            values().firstOrNull { it.name == value?.uppercase() }?.let { return it }
            val normalized = binding.orEmpty().lowercase()
            return when {
                listOf("ebook", "e-book", "epub", "kindle", "digital", "electronic resource")
                    .any(normalized::contains) -> DIGITAL
                normalized.isBlank() -> UNKNOWN
                else -> PHYSICAL
            }
        }
    }
}

enum class LoanRole { LENDER, BORROWER }

enum class LoanStatus {
    PENDING,
    ACTIVE,
    RETURN_REQUESTED,
    RETURNED;

    companion object {
        fun from(value: String?): LoanStatus = when (value?.lowercase()) {
            "pending" -> PENDING
            "return_requested" -> RETURN_REQUESTED
            "returned" -> RETURNED
            else -> ACTIVE
        }
    }
}

data class ActiveLoan(
    val id: String,
    val bookId: String,
    val counterpartyName: String,
    val role: LoanRole,
    val status: LoanStatus,
    val dueAt: Instant?,
    val acceptedAt: Instant?,
    val returnRequestedAt: Instant?,
    val returnedAt: Instant?,
    val returnRequestedById: String? = null,
    val returnRequestedByCurrentUser: Boolean = false
) {
    val isLender: Boolean get() = role == LoanRole.LENDER
}

@JvmInline
value class LoanInviteCode private constructor(val value: String) {
    companion object {
        private val pattern = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}$")

        fun parse(value: String?): LoanInviteCode? {
            val normalized = value?.trim()?.uppercase() ?: return null
            return normalized.takeIf(pattern::matches)?.let(::LoanInviteCode)
        }
    }
}

data class PendingLoanInvite(
    val id: String,
    val copyId: String,
    val code: LoanInviteCode,
    val dueAt: Instant?,
    val expiresAt: Instant?,
    val status: String = "pending"
)

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data object NotFound : LoadState<Nothing>
    data class Error(val cause: Throwable) : LoadState<Nothing>
}
