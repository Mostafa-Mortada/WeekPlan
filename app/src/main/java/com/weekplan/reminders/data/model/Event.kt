package com.weekplan.reminders.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Shared data contract (plan: "Shared implementation agreement").
 * Member 4 owns these types. They live here so the planner compiles on its own;
 * when Member 4 publishes the real ones, keep the field names and delete this file.
 */

enum class RepeatFrequency { NONE, DAILY, WEEKLY, SELECTED_WEEKDAYS }

/** [weekdays] is only used for [RepeatFrequency.SELECTED_WEEKDAYS]. No end date in v1. */
data class RepeatRule(
    val frequency: RepeatFrequency = RepeatFrequency.NONE,
    val weekdays: Set<DayOfWeek> = emptySet(),
) {
    companion object {
        val None = RepeatRule()
    }
}

/** Device-local date and times; end must be after start on the same day. */
data class Event(
    val id: String,
    val title: String,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val notes: String = "",
    val reminderOffsetMinutes: Int? = null, // null = reminder off, 0 = at start
    val repeatRule: RepeatRule = RepeatRule.None,
)

/**
 * One concrete appearance of an [Event] on a day. The plan lists eventId, occurrenceDate,
 * startDateTime and endDateTime; this version carries the whole [Event] so the planner can
 * show titles without a second lookup, and derives the rest.
 */
data class EventOccurrence(
    val event: Event,
    val occurrenceDate: LocalDate,
) {
    val eventId: String get() = event.id
    val startDateTime: LocalDateTime get() = occurrenceDate.atTime(event.startTime)
    val endDateTime: LocalDateTime get() = occurrenceDate.atTime(event.endTime)
}
