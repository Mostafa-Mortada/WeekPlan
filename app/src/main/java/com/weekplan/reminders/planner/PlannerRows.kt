package com.weekplan.reminders.planner

import com.weekplan.reminders.data.model.EventOccurrence
import java.time.LocalDate

enum class PlannerStatus { LOADING, CONTENT, ERROR }

/** Everything the screen needs to draw one week. */
data class PlannerUiState(
    val weekStart: LocalDate,
    /** 0 = this week, 1 = next week, -1 = last week ... */
    val weekOffset: Int,
    val status: PlannerStatus,
    val rows: List<PlannerRow>,
    /** Index of today's header in [rows], or null when today is not in this week. */
    val todayRowIndex: Int?,
)

object PlannerRows {

    const val SKELETON_COUNT = 5

    fun build(
        weekStart: LocalDate,
        today: LocalDate,
        status: PlannerStatus,
        occurrences: List<EventOccurrence> = emptyList(),
    ): PlannerUiState {
        val rows: List<PlannerRow> = when (status) {
            PlannerStatus.LOADING -> List(SKELETON_COUNT) { PlannerRow.Skeleton(it) }
            PlannerStatus.ERROR -> listOf(PlannerRow.Error)
            PlannerStatus.CONTENT -> contentRows(weekStart, today, occurrences)
        }
        val todayIndex = rows.indexOfFirst { it is PlannerRow.DayHeader && it.isToday }
        return PlannerUiState(
            weekStart = weekStart,
            weekOffset = WeekMath.weekOffset(weekStart, today),
            status = status,
            rows = rows,
            todayRowIndex = todayIndex.takeIf { it >= 0 },
        )
    }

    private fun contentRows(
        weekStart: LocalDate,
        today: LocalDate,
        occurrences: List<EventOccurrence>,
    ): List<PlannerRow> {
        val days = WeekMath.weekDays(weekStart)
        // Only this week's occurrences, even if the repository returns more.
        val byDay = occurrences.filter { it.occurrenceDate in days }.groupBy { it.occurrenceDate }

        val rows = ArrayList<PlannerRow>()
        if (byDay.isEmpty()) {
            rows.add(PlannerRow.WeekEmpty(addDate = if (today in days) today else weekStart))
        }
        for (date in days) {
            val sorted = byDay[date].orEmpty().sortedWith(ORDER)
            rows.add(PlannerRow.DayHeader(date, isToday = date == today, eventCount = sorted.size))
            if (sorted.isEmpty()) {
                rows.add(PlannerRow.EmptyDay(date))
            } else {
                val overlapping = overlapping(sorted)
                sorted.forEachIndexed { i, occ -> rows.add(PlannerRow.Event(occ, overlapping[i])) }
            }
        }
        return rows
    }

    /** By start time, then end time, then title, so the order is stable. */
    private val ORDER = compareBy<EventOccurrence>(
        { it.startDateTime },
        { it.endDateTime },
        { it.event.title.lowercase() },
        { it.eventId },
    )

    /**
     * For events sorted by start: true where an event overlaps another one.
     * Events that only touch (one ends exactly when the next starts) do not overlap.
     */
    fun overlapping(sorted: List<EventOccurrence>): BooleanArray {
        val result = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                if (!sorted[j].startDateTime.isBefore(sorted[i].endDateTime)) break
                result[i] = true
                result[j] = true
            }
        }
        return result
    }
}
