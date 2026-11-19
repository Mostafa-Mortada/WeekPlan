package com.weekplan.reminders.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class EventTimeDefaults(val date: LocalDate, val start: LocalTime, val end: LocalTime) {
    companion object {
        fun nextHour(now: LocalDateTime, requestedDate: LocalDate): EventTimeDefaults {
            val next = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            // Only move today's selection forward at midnight; keep an explicitly chosen day.
            val date = if (requestedDate == now.toLocalDate()) next.toLocalDate() else requestedDate
            val start = next.toLocalTime()
            val end = if (start.hour == 23) LocalTime.of(23, 59) else start.plusHours(1)
            return EventTimeDefaults(date, start, end)
        }
    }
}
