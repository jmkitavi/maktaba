package com.maktaba.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LoanInviteResolutionTest {
    @Test fun acceptedInvitationUsesCurrentLoanRatherThanBecomingAcceptableAgain() {
        assertEquals(LoanStatus.ACTIVE, invitationLoanStatus("accepted", false, "active"))
        assertEquals(LoanStatus.RETURN_REQUESTED, invitationLoanStatus("accepted", false, "return_requested"))
        assertEquals(LoanStatus.RETURNED, invitationLoanStatus("accepted", false, "returned"))
    }

    @Test fun acceptedInvitationCannotReopenWithoutAnAuthoritativeLoan() {
        assertThrows(InviteUnavailableException::class.java) { invitationLoanStatus("accepted", false) }
        assertThrows(InviteUnavailableException::class.java) { invitationLoanStatus("accepted", false, "pending") }
    }

    @Test fun pendingInvitationMustExplicitlyAllowAcceptance() {
        assertEquals(LoanStatus.PENDING, invitationLoanStatus("pending", true))
        assertThrows(InviteUnavailableException::class.java) { invitationLoanStatus("pending", false) }
    }

    @Test fun terminalOrContradictoryResponsesCannotEnableAcceptance() {
        assertThrows(InviteUnavailableException::class.java) { invitationLoanStatus("cancelled", false) }
        assertThrows(InviteUnavailableException::class.java) { invitationLoanStatus("accepted", true, "active") }
    }
}
