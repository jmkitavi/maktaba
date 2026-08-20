package com.maktaba.app.util

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

object LoanTimeFormatter {
    fun formatDate(
        instant: Instant?,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String = instant?.atZone(zoneId)?.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    ) ?: "Date unavailable"

    fun remaining(
        dueAt: Instant?,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): String {
        dueAt ?: return "Due date unavailable"
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, zoneId)
        val dueDate = LocalDate.ofInstant(dueAt, zoneId)
        if (dueDate == today) return "Due today"

        val duration = Duration.between(now, dueAt)
        if (duration.isNegative) {
            val overdueDays = maxOf(1, abs(Duration.between(dueAt, now).toDays()))
            return "Overdue by $overdueDays ${if (overdueDays == 1L) "day" else "days"}"
        }
        val hours = duration.toHours()
        return if (hours >= 48) {
            val days = maxOf(1, duration.toDays())
            "$days ${if (days == 1L) "day" else "days"} remaining"
        } else {
            val days = duration.toDays()
            val remainingHours = duration.minusDays(days).toHours()
            when {
                days > 0 -> "$days ${if (days == 1L) "day" else "days"} $remainingHours ${if (remainingHours == 1L) "hour" else "hours"} remaining"
                else -> "${maxOf(1, hours)} ${if (hours == 1L) "hour" else "hours"} remaining"
            }
        }
    }

    fun localDateToEndOfDayMillis(
        pickerUtcMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val selectedDate = Instant.ofEpochMilli(pickerUtcMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        return selectedDate.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
    }
}
