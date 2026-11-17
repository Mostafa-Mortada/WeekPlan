package com.weekplan.reminders.reminders

import com.weekplan.reminders.data.database.Converters
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Frequency
import com.weekplan.reminders.data.model.RepeatRule
import com.weekplan.reminders.data.recurrence.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class EventReminderTest {
    private val event = Event(
        id = 42,
        title = "Team meeting",
        startDate = LocalDate.of(2026, 11, 16),
        startTime = LocalTime.of(10, 0),
        endTime = LocalTime.of(11, 0),
        reminderOffsetMinutes = 15,
    )

    @Test
    fun reminderDatesMatchPlannerRecurrence() {
        val rules = listOf(
            RepeatRule(Frequency.NONE),
            RepeatRule(Frequency.DAILY),
            RepeatRule(Frequency.WEEKLY),
            RepeatRule(Frequency.WEEKLY, setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        )
        val windowEnd = event.startDate.plusWeeks(2)
        for (rule in rules) {
            val recurringEvent = event.copy(repeatRule = rule)
            val reminder = requireNotNull(recurringEvent.toReminder()).copy(zoneId = ZoneOffset.UTC)
            val expected = Recurrence.expand(recurringEvent, event.startDate, windowEnd)
            var after = event.startDate.atStartOfDay().toInstant(ZoneOffset.UTC)

            assertEquals("42", reminder.eventId)
            for (occurrence in expected) {
                val actual = requireNotNull(reminder.nextOccurrence(after))
                assertEquals(occurrence.startDateTime.toInstant(ZoneOffset.UTC), actual.eventAt)
                assertEquals(actual.eventAt.minus(Duration.ofMinutes(15)), actual.triggerAt)
                after = actual.eventAt
            }
            if (rule.frequency == Frequency.NONE) assertNull(reminder.nextOccurrence(after))
        }
    }

    @Test
    fun disabledReminderDoesNotSchedule() {
        assertNull(event.copy(reminderOffsetMinutes = null).toReminder())
    }

    @Test
    fun legacyOnceEventsRemainNonRepeating() {
        val frequency = requireNotNull(Converters().stringToFrequency("ONCE"))
        assertEquals(RepeatFrequency.ONCE, event.copy(repeatRule = RepeatRule(frequency)).toReminder()?.frequency)
    }
}
