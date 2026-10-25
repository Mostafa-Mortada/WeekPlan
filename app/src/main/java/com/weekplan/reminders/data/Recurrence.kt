package com.weekplan.reminders.data

import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.EventOccurrence
import com.weekplan.reminders.data.model.RepeatFrequency
import java.time.LocalDate

/**
 * Expands one event into its occurrences inside [from]..[to] (both inclusive).
 *
 * This is the "shared recurrence helper" Members 3 and 4 own. It is a stand-in so the planner
 * can show recurring events today; replace it with the shared helper and keep the same rules:
 * - none: one occurrence on startDate
 * - daily: every day on or after startDate
 * - weekly: the weekday of startDate, on or after startDate
 * - selected weekdays: the chosen weekdays, on or after startDate
 */
object Recurrence {

    fun expand(event: Event, from: LocalDate, to: LocalDate): List<EventOccurrence> {
        if (to.isBefore(from)) return emptyList()
        val first = if (from.isAfter(event.startDate)) from else event.startDate
        val result = ArrayList<EventOccurrence>()
        var date = first
        while (!date.isAfter(to)) {
            if (occursOn(event, date)) result.add(EventOccurrence(event, date))
            date = date.plusDays(1)
        }
        return result
    }

    private fun occursOn(event: Event, date: LocalDate): Boolean = when (event.repeatRule.frequency) {
        RepeatFrequency.NONE -> date == event.startDate
        RepeatFrequency.DAILY -> true
        RepeatFrequency.WEEKLY -> date.dayOfWeek == event.startDate.dayOfWeek
        RepeatFrequency.SELECTED_WEEKDAYS -> date.dayOfWeek in event.repeatRule.weekdays
    }
}
