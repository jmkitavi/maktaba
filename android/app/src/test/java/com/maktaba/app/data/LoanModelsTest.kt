package com.maktaba.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
