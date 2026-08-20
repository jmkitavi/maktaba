package com.maktaba.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class LoanTimeFormatterTest {
    private val now = Instant.parse("2026-08-20T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun formatsRemainingAndOverdueTime() {
        assertEquals(
            "3 days remaining",
            LoanTimeFormatter.remaining(Instant.parse("2026-08-23T12:00:00Z"), clock)
        )
        assertEquals(
            "Overdue by 2 days",
            LoanTimeFormatter.remaining(Instant.parse("2026-08-18T12:00:00Z"), clock)
        )
    }

    @Test
    fun convertsPickerDateToLocalEndOfDay() {
        val pickerUtc = Instant.parse("2026-09-03T00:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-09-03T20:59:59.999999999Z").toEpochMilli(),
            LoanTimeFormatter.localDateToEndOfDayMillis(pickerUtc, ZoneId.of("Africa/Nairobi"))
        )
    }
}
