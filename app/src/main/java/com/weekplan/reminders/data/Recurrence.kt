package com.weekplan.reminders.data

import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.EventOccurrence
import com.weekplan.reminders.data.model.Frequency
import java.time.LocalDate

/**
 * Expands one event into its occurrences inside [from]..[to] (both inclusive).
 *
 * This is the "shared recurrence helper" Members 3 and 4 own. It is a stand-in so the planner
 * can show recurring events today; replace it with the shared helper and keep the same rules:
 * - none: one occurrence on startDate
 * - daily: every day on or after startDate
 * - weekly with no selected weekdays: the weekday of startDate
 * - weekly with selected weekdays: the chosen weekdays
 * - both weekly forms begin on or after startDate
 */
object Recurrence {

    fun expand(event: Event, from: LocalDate, to: LocalDate): List<EventOccurrence> {
        if (to.isBefore(from)) return emptyList()
        val first = if (from.isAfter(event.startDate)) from else event.startDate
        val result = ArrayList<EventOccurrence>()
        var date = first
        while (!date.isAfter(to)) {
            if (occursOn(event, date)) {
                result.add(
                    EventOccurrence(
                        eventId = event.id,
                        occurrenceDate = date,
                        startDateTime = date.atTime(event.startTime),
                        endDateTime = date.atTime(event.endTime),
                    ),
                )
            }
            date = date.plusDays(1)
        }
        return result
    }

    private fun occursOn(event: Event, date: LocalDate): Boolean = when (event.repeatRule.frequency) {
        Frequency.NONE, Frequency.ONCE -> date == event.startDate
        Frequency.DAILY -> true
        Frequency.WEEKLY -> {
            val selectedWeekdays = event.repeatRule.weekdays
            if (selectedWeekdays.isEmpty()) date.dayOfWeek == event.startDate.dayOfWeek
            else date.dayOfWeek in selectedWeekdays
        }
        Frequency.MONTHLY, Frequency.YEARLY ->
            throw IllegalArgumentException("${event.repeatRule.frequency} recurrence is not supported yet")
    }
}
