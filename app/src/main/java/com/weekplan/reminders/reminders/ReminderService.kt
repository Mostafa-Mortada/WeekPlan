package com.weekplan.reminders.reminders

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class ScheduleStatus { EXACT, INEXACT, NOTIFIED, NOTIFICATIONS_DISABLED, NO_FUTURE_OCCURRENCE }

// Use these methods from activity lifecycle callbacks and broadcast receivers.
class ReminderService(context: Context) {
    private val store = ReminderStore(context.applicationContext)
    private val scheduler = ReminderScheduler(context.applicationContext)
    private val notifications = ReminderNotifications(context.applicationContext)

    fun saveReminder(reminder: Reminder): ScheduleStatus {
        notifications.cancel(reminder.eventId)
        return scheduleNext(reminder.copy(weekdays = reminder.weekdays.toSet()))
    }

    fun cancelReminder(eventId: String) {
        store.delete(eventId)
        scheduler.cancel(eventId)
        notifications.cancel(eventId)
    }

    fun getReminder(eventId: String): Reminder? = store.get(eventId)?.reminder

    fun getReminders(): List<Reminder> = store.getAll().map { it.reminder }

    fun notificationsEnabled(): Boolean = notifications.enabled()

    fun exactAlarmsAllowed(): Boolean = scheduler.exactAlarmsAllowed()

    internal fun restoreReminder(reminder: Reminder): ScheduleStatus = scheduleNext(reminder)

    internal fun deliver(eventId: String, alarmToken: String) {
        val record = store.get(eventId) ?: return
        val occurrence = record.nextOccurrence ?: return
        if (record.alarmToken != alarmToken) return
        val now = Instant.now()
        if (now >= occurrence.triggerAt &&
            (now < occurrence.eventAt || record.reminder.offsetMinutes == 0)) {
            // At-start alarms are delivered when the event starts, rather than before it.
            notifyOccurrence(record)
        }
        scheduleNext(record.reminder)
    }

    private fun scheduleNext(reminder: Reminder): ScheduleStatus {
        val saved = store.get(reminder.eventId)
        var record = StoredReminder(
            reminder, UUID.randomUUID().toString(), lastNotifiedDate = saved?.takeIf {
                val previous = it.reminder
                previous.startDate == reminder.startDate &&
                    previous.eventTime == reminder.eventTime &&
                    previous.frequency == reminder.frequency &&
                    previous.weekdays == reminder.weekdays
            }?.lastNotifiedDate,
        )
        store.save(record)
        scheduler.cancel(reminder.eventId)
        var notifiedNow = false

        while (true) {
            val now = Instant.now()
            val next = reminder.nextOccurrence(now, record.lastNotifiedDate)
            record = record.copy(nextOccurrence = next)
            store.save(record)
            if (next == null) {
                return if (notifiedNow) ScheduleStatus.NOTIFIED else ScheduleStatus.NO_FUTURE_OCCURRENCE
            }
            if (!notifications.enabled()) {
                return if (notifiedNow) ScheduleStatus.NOTIFIED else ScheduleStatus.NOTIFICATIONS_DISABLED
            }
            if (next.triggerAt > now) {
                val status = scheduler.schedule(record)
                return if (notifiedNow) ScheduleStatus.NOTIFIED else status
            }

            record = notifyOccurrence(record) ?: return ScheduleStatus.NOTIFICATIONS_DISABLED
            notifiedNow = true
            // Continue to schedule the following occurrence, skipping the one just notified.
        }
    }

    private fun notifyOccurrence(record: StoredReminder): StoredReminder? {
        val next = requireNotNull(record.nextOccurrence)
        val notified = record.copy(
            nextOccurrence = null,
            lastNotifiedDate = next.eventAt.atZone(record.reminder.zoneId ?: ZoneId.systemDefault()).toLocalDate(),
        )
        // Remember this event date before notifying, so restore cannot notify it again.
        store.save(notified)
        if (!notifications.show(record.reminder, next)) {
            store.save(record)
            return null
        }
        return notified
    }

    companion object {
        const val EXTRA_EVENT_ID = "com.weekplan.reminders.EVENT_ID"
        const val EXTRA_OCCURRENCE_DATE = "com.weekplan.reminders.OCCURRENCE_DATE"
    }
}
