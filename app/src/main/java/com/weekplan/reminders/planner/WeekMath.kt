package com.weekplan.reminders.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Date helpers for the planner. Weeks start on Monday (team agreement, plan task 1.2). */
object WeekMath {

    fun weekStartOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekDays(weekStart: LocalDate): List<LocalDate> = (0L..6L).map { weekStart.plusDays(it) }

    /** 0 = the week containing [today], 1 = next week, -1 = last week. */
    fun weekOffset(weekStart: LocalDate, today: LocalDate): Int =
        ChronoUnit.WEEKS.between(weekStartOf(today), weekStart).toInt()

    /**
     * "Oct 19 – 25, 2026", "Oct 26 – Nov 1, 2026" (month boundary),
     * "Dec 28, 2026 – Jan 3, 2027" (year boundary).
     */
    fun formatWeekRange(weekStart: LocalDate, locale: Locale = Locale.getDefault()): String {
        val end = weekStart.plusDays(6)
        val monthDay = DateTimeFormatter.ofPattern("MMM d", locale)
        val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
        return when {
            weekStart.year != end.year -> "${weekStart.format(monthDayYear)} – ${end.format(monthDayYear)}"
            weekStart.month != end.month -> "${weekStart.format(monthDay)} – ${end.format(monthDayYear)}"
            else -> "${weekStart.format(monthDay)} – ${end.format(DateTimeFormatter.ofPattern("d, yyyy", locale))}"
        }
    }
}
