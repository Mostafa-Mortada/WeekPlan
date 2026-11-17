package com.weekplan.reminders.reminders

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Frequency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reminderLock = Mutex()
    private lateinit var service: ReminderService

    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
        service = ReminderService(this)

        scope.launch {
            AppGraph.eventDao.observeEventsWithReminders().distinctUntilChanged().collect {
                runReminderOperation { reconcileReminders() }
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                runReminderOperationAsync { reconcileReminders(restore = true) }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    internal fun restoreReminders(onFinished: () -> Unit = {}) {
        runReminderOperationAsync(onFinished) { reconcileReminders(restore = true) }
    }

    internal fun deliverReminder(eventId: String, alarmToken: String, onFinished: () -> Unit) {
        runReminderOperationAsync(onFinished) {
            val current = eventId.toLongOrNull()?.let { AppGraph.eventDao.getEvent(it) }?.toReminder()
            val stored = service.getReminder(eventId)
            when {
                current == null -> service.cancelReminder(eventId)
                current != stored -> service.saveReminder(current)
                else -> service.deliver(eventId, alarmToken)
            }
        }
    }

    private suspend fun reconcileReminders(restore: Boolean = false) {
        val reminders = AppGraph.eventDao.getAllEvents().mapNotNull(Event::toReminder)
        val stored = service.getReminders().associateBy(Reminder::eventId).toMutableMap()
        for (reminder in reminders) {
            val previous = stored.remove(reminder.eventId)
            if (previous != reminder) {
                service.saveReminder(reminder)
            } else if (restore) {
                service.restoreReminder(reminder)
            }
        }
        stored.keys.forEach(service::cancelReminder)
    }

    private fun runReminderOperationAsync(
        onFinished: () -> Unit = {},
        operation: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                runReminderOperation(operation)
            } finally {
                onFinished()
            }
        }
    }

    private suspend fun runReminderOperation(operation: suspend () -> Unit) {
        try {
            reminderLock.withLock { operation() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("ReminderService", "Reminder operation failed", error)
        }
    }
}

internal fun Event.toReminder(): Reminder? {
    val offset = reminderOffsetMinutes ?: return null
    if (offset !in 0..10_080 || title.isBlank()) return null
    val frequency = when (repeatRule.frequency) {
        Frequency.NONE -> RepeatFrequency.ONCE
        Frequency.DAILY -> RepeatFrequency.DAILY
        Frequency.WEEKLY -> if (repeatRule.weekdays.isEmpty()) {
            RepeatFrequency.WEEKLY
        } else {
            RepeatFrequency.SELECTED_WEEKDAYS
        }
    }
    return Reminder(
        eventId = id.toString(),
        title = title,
        startDate = startDate,
        eventTime = startTime.withNano(0),
        offsetMinutes = offset,
        frequency = frequency,
        weekdays = if (frequency == RepeatFrequency.SELECTED_WEEKDAYS) repeatRule.weekdays else emptySet(),
    )
}
