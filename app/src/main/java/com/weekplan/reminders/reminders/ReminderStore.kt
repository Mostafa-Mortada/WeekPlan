package com.weekplan.reminders.reminders

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class StoredReminder(
    val reminder: Reminder,
    val alarmToken: String,
    val nextOccurrence: ReminderOccurrence? = null,
    val lastNotifiedDate: LocalDate? = null,
)

internal class ReminderStore(context: Context) {
    private val preferences = context.getSharedPreferences("reminder_service_v1", Context.MODE_PRIVATE)

    fun get(eventId: String): StoredReminder? {
        val value = preferences.getString(eventId, null) ?: return null
        return decode(value)
    }

    fun getAll(): List<StoredReminder> = preferences.all.values.map { decode(it as String) }

    fun save(record: StoredReminder) {
        val reminder = record.reminder
        val json = JSONObject().apply {
            put("eventId", reminder.eventId)
            put("title", reminder.title)
            put("startDate", reminder.startDate.toString())
            put("eventTime", reminder.eventTime.toString())
            put("offsetMinutes", reminder.offsetMinutes)
            put("frequency", reminder.frequency.name)
            put("weekdays", JSONArray(reminder.weekdays.map { it.value }))
            reminder.zoneId?.let { put("zoneId", it.id) }
            put("generation", record.alarmToken)
            record.lastNotifiedDate?.let { put("lastNotifiedDate", it.toString()) }
            record.nextOccurrence?.let {
                put("eventAt", it.eventAt.toString())
                put("triggerAt", it.triggerAt.toString())
            }
        }
        val editor = preferences.edit()
        editor.putString(reminder.eventId, json.toString())
        if (!editor.commit()) throw IOException("Could not save reminder")
    }

    fun delete(eventId: String) {
        val editor = preferences.edit()
        editor.remove(eventId)
        if (!editor.commit()) throw IOException("Could not remove reminder")
    }

    private fun decode(value: String): StoredReminder {
        val json = JSONObject(value)
        val days = json.getJSONArray("weekdays")
        val reminder = Reminder(
            eventId = json.getString("eventId"),
            title = json.getString("title"),
            startDate = LocalDate.parse(json.getString("startDate")),
            eventTime = LocalTime.parse(json.getString("eventTime")),
            offsetMinutes = json.getInt("offsetMinutes"),
            frequency = RepeatFrequency.valueOf(json.getString("frequency")),
            weekdays = (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
            zoneId = if (json.has("zoneId")) ZoneId.of(json.getString("zoneId")) else null,
        )
        val next = if (json.has("triggerAt")) {
            ReminderOccurrence(Instant.parse(json.getString("eventAt")), Instant.parse(json.getString("triggerAt")))
        } else null
        val lastNotifiedDate = if (json.has("lastNotifiedDate")) {
            LocalDate.parse(json.getString("lastNotifiedDate"))
        } else null
        return StoredReminder(reminder, json.getString("generation"), next, lastNotifiedDate)
    }
}
