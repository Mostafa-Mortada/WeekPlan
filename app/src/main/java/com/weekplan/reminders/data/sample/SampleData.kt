package com.weekplan.reminders.data.sample

import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Frequency
import com.weekplan.reminders.data.model.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Demo events relative to [today], so every screen state (overlap, long title, recurring) shows up. */
object SampleData {

    fun events(today: LocalDate): List<Event> = listOf(
        Event(
            id = 1L,
            title = "Study session",
            startDate = today,
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(12, 0),
            notes = "Mobile computing revision",
            reminderOffsetMinutes = 15,
        ),
        Event( // overlaps "Study session" on purpose
            id = 2L,
            title = "Project sync",
            startDate = today,
            startTime = LocalTime.of(11, 30),
            endTime = LocalTime.of(12, 30),
        ),
        Event(
            id = 3L,
            title = "Morning review",
            startDate = today.minusDays(3),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(8, 30),
            reminderOffsetMinutes = 5,
            repeatRule = RepeatRule(Frequency.DAILY),
        ),
        Event(
            id = 4L,
            title = "Gym",
            startDate = today.minusWeeks(2),
            startTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(19, 0),
            reminderOffsetMinutes = 0,
            repeatRule = RepeatRule(
                Frequency.WEEKLY,
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
        ),
        Event(
            id = 5L,
            title = "Weekly planning",
            startDate = today.minusWeeks(1),
            startTime = LocalTime.of(20, 0),
            endTime = LocalTime.of(20, 30),
            repeatRule = RepeatRule(Frequency.WEEKLY),
        ),
        Event(
            id = 6L,
            title = "Prepare the final presentation slides for the mobile computing demo day",
            startDate = today.plusDays(1),
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(16, 30),
        ),
    )
}
