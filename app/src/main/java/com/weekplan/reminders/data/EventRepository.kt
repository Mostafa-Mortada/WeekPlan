package com.weekplan.reminders.data

import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Settings
import com.weekplan.reminders.data.model.OccurrenceWithEvent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface EventRepository {
    val changes: Flow<Unit>

    suspend fun createEvent(event: Event): Long
    suspend fun updateEvent(event: Event)
    suspend fun deleteEvent(eventId: Long)
    suspend fun getEvent(eventId: Long): Event?

    suspend fun getOccurrences(
        from: LocalDate,
        to: LocalDate
    ): List<OccurrenceWithEvent>

    suspend fun getSettings(): Settings
    suspend fun saveSettings(settings: Settings)
}
