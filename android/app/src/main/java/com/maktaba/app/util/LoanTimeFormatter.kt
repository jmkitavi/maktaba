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
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

enum class LoanUrgency { UPCOMING, DUE_SOON, DUE_TODAY, OVERDUE, UNKNOWN }

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

    /**
     * How urgent a loan is. Drives semantic colour so that "3 days remaining" and
     * "Overdue by 3 days" can never be typeset identically.
     */
    fun urgency(
        dueAt: Instant?,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): LoanUrgency {
        dueAt ?: return LoanUrgency.UNKNOWN
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, zoneId)
        val dueDate = LocalDate.ofInstant(dueAt, zoneId)
        return when {
            dueDate.isBefore(today) -> LoanUrgency.OVERDUE
            dueDate == today -> LoanUrgency.DUE_TODAY
            Duration.between(now, dueAt).toDays() <= 2 -> LoanUrgency.DUE_SOON
            else -> LoanUrgency.UPCOMING
        }
    }

    /** Whole days until the due date; negative when overdue, null when there is no date. */
    fun daysUntilDue(
        dueAt: Instant?,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): Long? {
        dueAt ?: return null
        val today = LocalDate.ofInstant(clock.instant(), zoneId)
        val dueDate = LocalDate.ofInstant(dueAt, zoneId)
        return ChronoUnit.DAYS.between(today, dueDate)
    }

    /**
     * A compact form for book cards and list rows, where [remaining] is too long to fit.
     */
    fun shortRemaining(
        dueAt: Instant?,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): String {
        val days = daysUntilDue(dueAt, clock, zoneId) ?: return "No due date"
        return when {
            days < 0 -> {
                val overdue = abs(days)
                "Overdue by $overdue ${if (overdue == 1L) "day" else "days"}"
            }
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            else -> "Due in $days days"
        }
    }

    /**
     * Fraction of the loan period already elapsed, clamped to 0..1. Null when either end
     * of the window is unknown.
     */
    fun elapsedFraction(
        startedAt: Instant?,
        dueAt: Instant?,
        clock: Clock = Clock.systemDefaultZone()
    ): Float? {
        if (startedAt == null || dueAt == null) return null
        val total = Duration.between(startedAt, dueAt).toMillis()
        if (total <= 0L) return 1f
        val done = Duration.between(startedAt, clock.instant()).toMillis()
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
