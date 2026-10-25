package com.weekplan.reminders.data

import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.RepeatFrequency
import com.weekplan.reminders.data.model.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Demo events relative to [today], so every screen state (overlap, long title, recurring) shows up. */
object SampleData {

    fun events(today: LocalDate): List<Event> = listOf(
        Event(
            id = "sample-study",
            title = "Study session",
            startDate = today,
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(12, 0),
            notes = "Mobile computing revision",
            reminderOffsetMinutes = 15,
        ),
        Event( // overlaps "Study session" on purpose
            id = "sample-sync",
            title = "Project sync",
            startDate = today,
            startTime = LocalTime.of(11, 30),
            endTime = LocalTime.of(12, 30),
        ),
        Event(
            id = "sample-review",
            title = "Morning review",
            startDate = today.minusDays(3),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(8, 30),
            reminderOffsetMinutes = 5,
            repeatRule = RepeatRule(RepeatFrequency.DAILY),
        ),
        Event(
            id = "sample-gym",
            title = "Gym",
            startDate = today.minusWeeks(2),
            startTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(19, 0),
            reminderOffsetMinutes = 0,
            repeatRule = RepeatRule(
                RepeatFrequency.SELECTED_WEEKDAYS,
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
        ),
        Event(
            id = "sample-planning",
            title = "Weekly planning",
            startDate = today.minusWeeks(1),
            startTime = LocalTime.of(20, 0),
            endTime = LocalTime.of(20, 30),
            repeatRule = RepeatRule(RepeatFrequency.WEEKLY),
        ),
        Event(
            id = "sample-long",
            title = "Prepare the final presentation slides for the mobile computing demo day",
            startDate = today.plusDays(1),
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(16, 30),
        ),
    )
}
