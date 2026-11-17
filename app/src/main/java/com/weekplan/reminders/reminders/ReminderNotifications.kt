package com.weekplan.reminders.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.weekplan.reminders.MainActivity
import com.weekplan.reminders.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class ReminderNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.reminder_channel_description) })
        }
    }

    fun permissionGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun enabled(): Boolean {
        createChannel()
        return permissionGranted() && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE)
    }

    fun show(reminder: Reminder, occurrence: ReminderOccurrence): Boolean {
        if (!enabled()) return false
        val start = occurrence.eventAt.atZone(reminder.zoneId ?: ZoneId.systemDefault())
        val eventTime = start.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        val timeLabel = if (occurrence.eventAt < Instant.now()) {
            R.string.reminder_notification_started
        } else {
            R.string.reminder_notification_time
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder_notification)
            .setContentTitle(reminder.title)
            .setContentText(context.getString(timeLabel, eventTime))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
        val occurrenceDate = start.toLocalDate()
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.Builder().scheme("weekplan").authority("event").appendPath(reminder.eventId).build()
            putExtra(ReminderService.EXTRA_EVENT_ID, reminder.eventId)
            putExtra(ReminderService.EXTRA_OCCURRENCE_DATE, occurrenceDate.toString())
        }
        builder.setContentIntent(PendingIntent.getActivity(
            context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ))
        try {
            manager.notify(reminder.eventId, 0, builder.build())
            return true
        } catch (_: SecurityException) {
            // Notification permission may change after the capability check.
            return false
        }
    }

    fun cancel(eventId: String) = manager.cancel(eventId, 0)

    companion object {
        const val CHANNEL_ID = "event_reminders"
    }
}
