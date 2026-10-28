package com.weekplan.reminders.data.model

/** An occurrence together with its saved event, for screens that need both schedule and display data. */
data class OccurrenceWithEvent(
    val event: Event,
    val occurrence: EventOccurrence,
)
