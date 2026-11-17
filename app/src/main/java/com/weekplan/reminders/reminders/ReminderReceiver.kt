package com.weekplan.reminders.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isAlarm = intent.action == ReminderScheduler.ACTION_DELIVER
        val alarmAccessChanged = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (!isAlarm && !alarmAccessChanged && intent.action !in restoreActions) return

        val application = context.applicationContext as ReminderApplication
        if (isAlarm) {
            val eventId = intent.getStringExtra(ReminderService.EXTRA_EVENT_ID) ?: return
            val alarmToken = intent.getStringExtra(ReminderScheduler.EXTRA_ALARM_TOKEN) ?: return
            val pending = goAsync()
            application.deliverReminder(eventId, alarmToken, pending::finish)
        } else {
            val pending = goAsync()
            val clockChanged = intent.action == Intent.ACTION_TIME_CHANGED ||
                intent.action == Intent.ACTION_TIMEZONE_CHANGED
            application.restoreReminders(clockChanged, pending::finish)
        }
    }

    companion object {
        private val restoreActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
