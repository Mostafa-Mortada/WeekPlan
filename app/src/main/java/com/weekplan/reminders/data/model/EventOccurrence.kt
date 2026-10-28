package com.weekplan.reminders.data.model

import java.time.LocalDate
import java.time.LocalDateTime

data class EventOccurrence(
    val eventId: Long,
    val occurrenceDate: LocalDate,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime
)