package com.weekplan.reminders.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

internal class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun exactAlarmsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun schedule(record: StoredReminder): ScheduleStatus {
        val occurrence = requireNotNull(record.nextOccurrence)
        val intent = alarmIntent(record.reminder.eventId).apply {
            putExtra(EXTRA_ALARM_TOKEN, record.alarmToken)
        }
        val operation = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (exactAlarmsAllowed()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence.triggerAt.toEpochMilli(), operation)
                return ScheduleStatus.EXACT
            } catch (_: SecurityException) {
                // Special alarm access can be revoked between checking and scheduling.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence.triggerAt.toEpochMilli(), operation)
        return ScheduleStatus.INEXACT
    }

    fun cancel(eventId: String) {
        val operation = PendingIntent.getBroadcast(
            context, 0, alarmIntent(eventId), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(operation)
        operation.cancel()
    }

    private fun alarmIntent(eventId: String): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DELIVER
            data = Uri.Builder().scheme("weekplan").authority("reminder").appendPath(eventId).build()
            putExtra(ReminderService.EXTRA_EVENT_ID, eventId)
        }

    companion object {
        const val ACTION_DELIVER = "com.weekplan.reminders.DELIVER_REMINDER"
        const val EXTRA_ALARM_TOKEN = "com.weekplan.reminders.REMINDER_GENERATION"
    }
}
