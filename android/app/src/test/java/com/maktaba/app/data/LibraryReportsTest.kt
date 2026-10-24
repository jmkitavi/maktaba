package com.maktaba.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryReportsTest {
    @Test
    fun requiresReasonButNotDetails() {
        assertEquals("Choose a reason for this report.", reportValidationError(null, ""))
        assertNull(reportValidationError(LibraryReportReason.SPAM, ""))
    }

    @Test
    fun acceptsExactlyFiveHundredCharactersAndRejectsMore() {
        assertNull(reportValidationError(LibraryReportReason.OTHER, "x".repeat(500)))
        assertEquals(
            "Keep the details to 500 characters or fewer.",
            reportValidationError(LibraryReportReason.OTHER, "x".repeat(501))
        )
    }
}
