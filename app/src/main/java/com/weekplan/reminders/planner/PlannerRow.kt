package com.weekplan.reminders.planner

import com.weekplan.reminders.data.model.OccurrenceWithEvent
import java.time.LocalDate

/** One line of the planner list. [key] identifies the row across refreshes (for DiffUtil). */
sealed interface PlannerRow {
    val key: String

    data class DayHeader(val date: LocalDate, val isToday: Boolean, val eventCount: Int) : PlannerRow {
        override val key get() = "day-$date"
    }

    data class Event(val item: OccurrenceWithEvent, val overlaps: Boolean) : PlannerRow {
        override val key get() = "event-${item.occurrence.eventId}-${item.occurrence.occurrenceDate}"
    }

    data class EmptyDay(val date: LocalDate) : PlannerRow {
        override val key get() = "empty-$date"
    }

    /** Shown above the days when the whole week has no events. [addDate] pre-fills the add form. */
    data class WeekEmpty(val addDate: LocalDate) : PlannerRow {
        override val key get() = "week-empty"
    }

    data class Skeleton(val index: Int) : PlannerRow {
        override val key get() = "skeleton-$index"
    }

    data object Error : PlannerRow {
        override val key get() = "error"
    }
}
