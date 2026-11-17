package com.weekplan.reminders.reminders

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class RepeatFrequency { ONCE, DAILY, WEEKLY, SELECTED_WEEKDAYS }

data class Reminder(
    val eventId: String,
    val title: String,
    val startDate: LocalDate,
    val eventTime: LocalTime,
    val offsetMinutes: Int = 0,
    val frequency: RepeatFrequency = RepeatFrequency.ONCE,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val zoneId: ZoneId? = null,
) {
    init {
        require(eventId.isNotBlank()) { "Event ID must not be blank" }
        require(title.isNotBlank()) { "Title must not be blank" }
        require(offsetMinutes in 0..10_080) { "Reminder offset must be between 0 and 7 days" }
        require(eventTime.nano == 0) { "Event time must use whole seconds" }
        require(frequency != RepeatFrequency.SELECTED_WEEKDAYS || weekdays.isNotEmpty()) {
            "Select at least one weekday"
        }
        require(frequency == RepeatFrequency.SELECTED_WEEKDAYS || weekdays.isEmpty()) {
            "Weekdays only apply to selected weekday reminders"
        }
    }

    fun nextOccurrence(
        after: Instant = Instant.now(),
        lastNotifiedDate: LocalDate? = null,
    ): ReminderOccurrence? {
        val zone = zoneId ?: ZoneId.systemDefault()
        val offset = Duration.ofMinutes(offsetMinutes.toLong())

        fun occurrence(date: LocalDate): ReminderOccurrence {
            val eventAt = date.atTime(eventTime).atZone(zone).toInstant()
            val plannedReminder = eventAt.minus(offset)
            return ReminderOccurrence(eventAt, maxOf(plannedReminder, after))
        }

        if (frequency == RepeatFrequency.ONCE) {
            if (lastNotifiedDate != null && startDate <= lastNotifiedDate) return null
            val next = occurrence(startDate)
            return if (next.eventAt > after) next else null
        }

        var date = maxOf(startDate, after.atZone(zone).toLocalDate())
        if (lastNotifiedDate != null && date <= lastNotifiedDate) {
            date = lastNotifiedDate.plusDays(1)
        }
        // Start after today's event if it has already begun, so seven dates include next week.
        if (occurrence(date).eventAt <= after) date = date.plusDays(1)

        repeat(7) {
            val matches = when (frequency) {
                RepeatFrequency.DAILY -> true
                RepeatFrequency.WEEKLY -> date.dayOfWeek == startDate.dayOfWeek
                RepeatFrequency.SELECTED_WEEKDAYS -> date.dayOfWeek in weekdays
                else -> false
            }
            if (matches) {
                return occurrence(date)
            }
            date = date.plusDays(1)
        }
        return null
    }
}

data class ReminderOccurrence(val eventAt: Instant, val triggerAt: Instant)
