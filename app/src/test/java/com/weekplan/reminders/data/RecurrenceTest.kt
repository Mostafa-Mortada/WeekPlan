package com.weekplan.reminders.data

import com.weekplan.reminders.data.recurrence.Recurrence
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Frequency
import com.weekplan.reminders.data.model.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {

    private fun event(start: LocalDate, rule: RepeatRule = RepeatRule()) = Event(
        id = 1L,
        title = "Test",
        startDate = start,
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(10, 0),
        repeatRule = rule,
    )

    private fun dates(e: Event, from: LocalDate, to: LocalDate) =
        Recurrence.expand(e, from, to).map { it.occurrenceDate }

    @Test
    fun nonRepeatingEvent_hasExactlyOneOccurrence() {
        val e = event(LocalDate.of(2026, 10, 21))
        assertEquals(listOf(LocalDate.of(2026, 10, 21)), dates(e, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)))
        assertTrue(dates(e, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)).isEmpty())
    }

    @Test
    fun rangeIsInclusiveAtBothEnds() {
        val e = event(LocalDate.of(2026, 10, 19), RepeatRule(Frequency.DAILY))
        val result = dates(e, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25))
        assertEquals(7, result.size)
        assertEquals(LocalDate.of(2026, 10, 19), result.first())
        assertEquals(LocalDate.of(2026, 10, 25), result.last())
    }

    @Test
    fun dailyEvent_doesNotAppearBeforeItsStartDate() {
        val e = event(LocalDate.of(2026, 10, 22), RepeatRule(Frequency.DAILY))
        val result = dates(e, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25))
        assertEquals(LocalDate.of(2026, 10, 22), result.first())
        assertEquals(4, result.size)
    }

    @Test
    fun weeklyEvent_usesTheWeekdayOfItsStartDate() {
        val wednesday = LocalDate.of(2026, 10, 14)
        val e = event(wednesday, RepeatRule(Frequency.WEEKLY))
        val result = dates(e, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 8))
        assertEquals(
            listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 4)),
            result,
        )
    }

    @Test
    fun selectedWeekdays_startOnOrAfterTheEventStartDate() {
        val rule = RepeatRule(
            Frequency.WEEKLY,
            setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        )
        val e = event(LocalDate.of(2026, 10, 21), rule) // a Wednesday
        val result = dates(e, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 30))
        // Monday 19th is before the start date, so the first match is Friday 23rd.
        assertEquals(
            listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 30)),
            result,
        )
    }

    @Test
    fun recurrenceContinuesAcrossMonthAndYearBoundaries() {
        val e = event(LocalDate.of(2026, 12, 1), RepeatRule(Frequency.WEEKLY)) // Tuesday
        val result = dates(e, LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3))
        assertEquals(listOf(LocalDate.of(2026, 12, 29)), result)
        val next = dates(e, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10))
        assertEquals(listOf(LocalDate.of(2027, 1, 5)), next)
    }

    @Test
    fun emptyOrReversedRange_givesNothing() {
        val e = event(LocalDate.of(2026, 10, 19), RepeatRule(Frequency.DAILY))
        assertTrue(dates(e, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 19)).isEmpty())
    }

    @Test
    fun occurrenceTimesComeFromTheEventAndTheOccurrenceDate() {
        val e = event(LocalDate.of(2026, 10, 19), RepeatRule(Frequency.DAILY))
        val occ = Recurrence.expand(e, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 21)).single()
        assertEquals(LocalDate.of(2026, 10, 21).atTime(9, 0), occ.startDateTime)
        assertEquals(LocalDate.of(2026, 10, 21).atTime(10, 0), occ.endDateTime)
    }
}
