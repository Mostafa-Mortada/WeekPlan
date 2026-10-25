package com.weekplan.reminders.data

import com.weekplan.reminders.data.model.EventOccurrence
import com.weekplan.reminders.data.model.Event
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The part of Member 4's repository that the weekly planner needs.
 * Member 4 owns the full interface (createEvent, updateEvent, deleteEvent, getEvent, settings...);
 * the real repository just has to provide these two members.
 */
interface EventRepository {

    /** Emits after every create / update / delete so open screens can reload (plan task 4.5). */
    val changes: Flow<Unit>

    /** Occurrences from [from] to [to], both inclusive, recurring events already expanded. */
    suspend fun getOccurrences(from: LocalDate, to: LocalDate): List<EventOccurrence>
    suspend fun createEvent(event: Event)
}







