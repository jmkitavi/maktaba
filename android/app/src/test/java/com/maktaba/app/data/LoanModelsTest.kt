package com.maktaba.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class LoanModelsTest {
    @Test
    fun classifiesDigitalBindings() {
        assertEquals(BookFormat.DIGITAL, BookFormat.from(null, "eBook"))
        assertEquals(BookFormat.DIGITAL, BookFormat.from(null, "Kindle Edition"))
        assertEquals(BookFormat.PHYSICAL, BookFormat.from(null, "Paperback"))
        assertEquals(BookFormat.UNKNOWN, BookFormat.from(null, ""))
    }

    @Test
    fun validatesInviteCodes() {
        assertEquals("ABCD-2345", LoanInviteCode.parse("abcd-2345")?.value)
        assertNull(LoanInviteCode.parse("Generating..."))
        assertNull(LoanInviteCode.parse(""))
    }

    @Test
    fun mapsCreateInviteExpiryAndRequiresIt() {
        val invite = pendingLoanInviteFromCreateResult(
            "copy_123",
            mapOf(
                "loanId" to "loan_123",
                "inviteCode" to "ABCD-2345",
                "dueAtMillis" to 1_800_000_000_000L,
                "expiresAtMillis" to 1_700_000_000_000L
            )
        )
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), invite.expiresAt)
        assertThrows(IllegalStateException::class.java) {
            pendingLoanInviteFromCreateResult(
                "copy_123",
                mapOf(
                    "loanId" to "loan_123",
                    "inviteCode" to "ABCD-2345",
                    "dueAtMillis" to 1_800_000_000_000L
                )
            )
        }
    }
}
