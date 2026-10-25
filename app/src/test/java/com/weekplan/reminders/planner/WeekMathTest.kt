package com.weekplan.reminders.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekMathTest {

    @Test
    fun weekStartsOnMonday_forEveryDayOfTheWeek() {
        val monday = LocalDate.of(2026, 10, 19)
        for (offset in 0L..6L) {
            assertEquals(monday, WeekMath.weekStartOf(monday.plusDays(offset)))
        }
    }

    @Test
    fun sundayBelongsToTheWeekThatStartedSixDaysEarlier() {
        assertEquals(LocalDate.of(2026, 10, 19), WeekMath.weekStartOf(LocalDate.of(2026, 10, 25)))
        assertEquals(DayOfWeek.MONDAY, WeekMath.weekStartOf(LocalDate.of(2026, 10, 25)).dayOfWeek)
    }

    @Test
    fun weekDaysAreSevenConsecutiveDays() {
        val days = WeekMath.weekDays(LocalDate.of(2026, 12, 28))
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 12, 28), days.first())
        assertEquals(LocalDate.of(2027, 1, 3), days.last()) // crosses the year
    }

    @Test
    fun weekOffsetCountsWholeWeeksFromTheCurrentWeek() {
        val today = LocalDate.of(2026, 10, 24) // Saturday
        val thisWeek = WeekMath.weekStartOf(today)
        assertEquals(0, WeekMath.weekOffset(thisWeek, today))
        assertEquals(1, WeekMath.weekOffset(thisWeek.plusWeeks(1), today))
        assertEquals(-3, WeekMath.weekOffset(thisWeek.minusWeeks(3), today))
    }

    @Test
    fun rangeLabel_sameMonth() {
        assertEquals(
            "Oct 19 – 25, 2026",
            WeekMath.formatWeekRange(LocalDate.of(2026, 10, 19), Locale.ENGLISH),
        )
    }

    @Test
    fun rangeLabel_crossesMonth() {
        assertEquals(
            "Oct 26 – Nov 1, 2026",
            WeekMath.formatWeekRange(LocalDate.of(2026, 10, 26), Locale.ENGLISH),
        )
    }

    @Test
    fun rangeLabel_crossesYear() {
        assertEquals(
            "Dec 28, 2026 – Jan 3, 2027",
            WeekMath.formatWeekRange(LocalDate.of(2026, 12, 28), Locale.ENGLISH),
        )
    }
}
