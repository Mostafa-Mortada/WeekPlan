package com.weekplan.reminders.planner

import com.weekplan.reminders.data.recurrence.Recurrence
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.EventOccurrence
import com.weekplan.reminders.data.model.Frequency
import com.weekplan.reminders.data.model.OccurrenceWithEvent
import com.weekplan.reminders.data.model.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerRowsTest {

    private val monday = LocalDate.of(2026, 10, 19)
    private val wednesday = LocalDate.of(2026, 10, 21)

    private fun occ(id: String, date: LocalDate, start: LocalTime, end: LocalTime, title: String = id): OccurrenceWithEvent {
        val eventId = id.hashCode().toLong()
        val event = Event(eventId, title, date, start, end)
        val occurrence = EventOccurrence(eventId, date, date.atTime(start), date.atTime(end))
        return OccurrenceWithEvent(event, occurrence)
    }

    private fun build(
        occurrences: List<OccurrenceWithEvent>,
        today: LocalDate = wednesday,
        weekStart: LocalDate = monday,
    ) = PlannerRows.build(weekStart, today, PlannerStatus.CONTENT, occurrences)

    private fun PlannerUiState.events() = rows.filterIsInstance<PlannerRow.Event>()

    // ---- day placement ----

    @Test
    fun sevenDayHeadersInMondayToSundayOrder() {
        val headers = build(emptyList()).rows.filterIsInstance<PlannerRow.DayHeader>()
        assertEquals(7, headers.size)
        assertEquals(monday, headers.first().date)
        assertEquals(monday.plusDays(6), headers.last().date)
    }

    @Test
    fun eventAppearsUnderItsOwnDayHeader() {
        val e = occ("a", wednesday, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val rows = build(listOf(e)).rows
        val headerIndex = rows.indexOfFirst { it is PlannerRow.DayHeader && it.date == wednesday }
        assertEquals(PlannerRow.Event(e, overlaps = false), rows[headerIndex + 1])
        val header = rows[headerIndex] as PlannerRow.DayHeader
        assertEquals(1, header.eventCount)
    }

    @Test
    fun daysWithoutEventsGetAnEmptyRow() {
        val e = occ("a", wednesday, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val empties = build(listOf(e)).rows.filterIsInstance<PlannerRow.EmptyDay>().map { it.date }
        assertEquals(6, empties.size)
        assertFalse(wednesday in empties)
    }

    // ---- ordering ----

    @Test
    fun eventsAreSortedByStartTimeWithinADay() {
        val late = occ("late", wednesday, LocalTime.of(18, 0), LocalTime.of(19, 0))
        val early = occ("early", wednesday, LocalTime.of(8, 0), LocalTime.of(9, 0))
        val mid = occ("mid", wednesday, LocalTime.of(12, 0), LocalTime.of(13, 0))
        val order = build(listOf(late, early, mid)).events().map { it.item.event.title }
        assertEquals(listOf("early", "mid", "late"), order)
    }

    @Test
    fun sameStartTime_isOrderedByEndThenTitle() {
        val b = occ("b", wednesday, LocalTime.of(9, 0), LocalTime.of(10, 0), title = "Beta")
        val a = occ("a", wednesday, LocalTime.of(9, 0), LocalTime.of(10, 0), title = "alpha")
        val short = occ("s", wednesday, LocalTime.of(9, 0), LocalTime.of(9, 30), title = "Zed")
        val order = build(listOf(b, a, short)).events().map { it.item.event.title }
        assertEquals(listOf("Zed", "alpha", "Beta"), order)
    }

    // ---- week range ----

    @Test
    fun occurrencesOutsideTheVisibleWeekAreIgnored() {
        val inside = occ("in", wednesday, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val before = occ("before", monday.minusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0))
        val after = occ("after", monday.plusDays(7), LocalTime.of(9, 0), LocalTime.of(10, 0))
        val ids = build(listOf(before, inside, after)).events().map { it.item.event.title }
        assertEquals(listOf("in"), ids)
    }

    @Test
    fun weekCrossingAYearBoundaryKeepsEventsOnBothSides() {
        val weekStart = LocalDate.of(2026, 12, 28)
        val dec31 = occ("dec", LocalDate.of(2026, 12, 31), LocalTime.of(9, 0), LocalTime.of(10, 0))
        val jan2 = occ("jan", LocalDate.of(2027, 1, 2), LocalTime.of(9, 0), LocalTime.of(10, 0))
        val state = build(listOf(dec31, jan2), today = weekStart, weekStart = weekStart)
        assertEquals(listOf("dec", "jan"), state.events().map { it.item.event.title })
    }

    @Test
    fun recurringOccurrencesLandOnTheRightDays() {
        val daily = Event(
            1L, "Daily", monday.minusDays(2), LocalTime.of(8, 0), LocalTime.of(8, 30),
            repeatRule = RepeatRule(Frequency.DAILY),
        )
        val occurrences = Recurrence.expand(daily, monday, monday.plusDays(6)).map {
            OccurrenceWithEvent(daily, it)
        }
        val state = build(occurrences)
        val headers = state.rows.filterIsInstance<PlannerRow.DayHeader>()
        assertTrue(headers.all { it.eventCount == 1 })
        assertEquals(7, state.events().size)
    }

    // ---- today ----

    @Test
    fun todaysHeaderIsMarkedAndIndexed() {
        val state = build(emptyList(), today = wednesday)
        val header = state.rows[state.todayRowIndex!!] as PlannerRow.DayHeader
        assertEquals(wednesday, header.date)
        assertTrue(header.isToday)
        assertEquals(1, state.rows.filterIsInstance<PlannerRow.DayHeader>().count { it.isToday })
    }

    @Test
    fun noTodayIndexWhenTodayIsNotInTheVisibleWeek() {
        val nextWeek = monday.plusWeeks(1)
        val state = build(emptyList(), today = wednesday, weekStart = nextWeek)
        assertNull(state.todayRowIndex)
        assertEquals(1, state.weekOffset)
    }

    // ---- overlap ----

    @Test
    fun overlappingEventsAreBothFlagged_andStayVisible() {
        val a = occ("a", wednesday, LocalTime.of(10, 0), LocalTime.of(12, 0))
        val b = occ("b", wednesday, LocalTime.of(11, 30), LocalTime.of(12, 30))
        val c = occ("c", wednesday, LocalTime.of(15, 0), LocalTime.of(16, 0))
        val events = build(listOf(a, b, c)).events()
        assertEquals(listOf(true, true, false), events.map { it.overlaps })
    }

    @Test
    fun eventsThatOnlyTouchDoNotOverlap() {
        val a = occ("a", wednesday, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val b = occ("b", wednesday, LocalTime.of(10, 0), LocalTime.of(11, 0))
        assertEquals(listOf(false, false), build(listOf(a, b)).events().map { it.overlaps })
    }

    @Test
    fun longEventOverlapsEverythingInsideIt() {
        val long = occ("long", wednesday, LocalTime.of(9, 0), LocalTime.of(17, 0))
        val x = occ("x", wednesday, LocalTime.of(10, 0), LocalTime.of(11, 0))
        val y = occ("y", wednesday, LocalTime.of(13, 0), LocalTime.of(14, 0))
        assertEquals(listOf(true, true, true), build(listOf(long, x, y)).events().map { it.overlaps })
    }

    @Test
    fun overlapInOneDayDoesNotFlagEventsOnAnotherDay() {
        val a = occ("a", wednesday, LocalTime.of(10, 0), LocalTime.of(11, 0))
        val b = occ("b", wednesday.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0))
        assertEquals(listOf(false, false), build(listOf(a, b)).events().map { it.overlaps })
    }

    // ---- empty / loading / error ----

    @Test
    fun emptyWeekShowsTheWeekEmptyCard_preFilledWithToday() {
        val first = build(emptyList(), today = wednesday).rows.first()
        assertEquals(PlannerRow.WeekEmpty(wednesday), first)
    }

    @Test
    fun emptyOtherWeekPreFillsTheAddFormWithItsFirstDay() {
        val nextWeek = monday.plusWeeks(1)
        val first = build(emptyList(), today = wednesday, weekStart = nextWeek).rows.first()
        assertEquals(PlannerRow.WeekEmpty(nextWeek), first)
    }

    @Test
    fun weekWithEventsHasNoWeekEmptyCard() {
        val e = occ("a", wednesday, LocalTime.of(9, 0), LocalTime.of(10, 0))
        assertTrue(build(listOf(e)).rows.none { it is PlannerRow.WeekEmpty })
    }

    @Test
    fun loadingShowsOnlySkeletons() {
        val state = PlannerRows.build(monday, wednesday, PlannerStatus.LOADING)
        assertEquals(PlannerRows.SKELETON_COUNT, state.rows.size)
        assertTrue(state.rows.all { it is PlannerRow.Skeleton })
        assertNull(state.todayRowIndex)
    }

    @Test
    fun errorShowsOnlyTheErrorRow() {
        val state = PlannerRows.build(monday, wednesday, PlannerStatus.ERROR)
        assertEquals(listOf<PlannerRow>(PlannerRow.Error), state.rows)
    }

    @Test
    fun rowKeysAreUniqueSoTheListCanDiffThem() {
        val a = occ("a", wednesday, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val b = occ("b", wednesday, LocalTime.of(9, 0), LocalTime.of(10, 0))
        val keys = build(listOf(a, b)).rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
