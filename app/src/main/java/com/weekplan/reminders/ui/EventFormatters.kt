package com.weekplan.reminders.ui

import android.content.Context
import com.weekplan.reminders.R
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Frequency
import java.time.format.TextStyle
import java.util.Locale

/**
 * Shared formatting utilities for events across dashboard and planner.
 */
object EventFormatters {

    /**
     * Formats recurrence rule and reminder offset into a human-readable string:
     * e.g. "↻ Daily · Reminder: 15 min before" or "↻ Mon, Wed · Reminder: at start".
     * Returns an empty string if neither repeat rule nor reminder is set.
     */
    fun formatMeta(context: Context, event: Event, locale: Locale = Locale.getDefault()): String {
        val parts = ArrayList<String>(2)

        when (event.repeatRule.frequency) {
            Frequency.NONE, Frequency.ONCE -> Unit
            Frequency.DAILY -> parts.add(context.getString(R.string.planner_repeat_daily))
            Frequency.WEEKLY -> {
                val days = event.repeatRule.weekdays
                    .sortedBy { it.value }
                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
                if (days.isNotEmpty()) {
                    parts.add(context.getString(R.string.planner_repeat_days, days))
                } else {
                    parts.add(context.getString(R.string.planner_repeat_weekly))
                }
            }
        }

        event.reminderOffsetMinutes?.let { minutes ->
            parts.add(
                if (minutes == 0) context.getString(R.string.planner_reminder_at_start)
                else context.getString(R.string.planner_reminder_before, minutes)
            )
        }

        return parts.joinToString(" · ")
    }
}

/** Convenience extension function for [EventFormatters.formatMeta]. */
fun Event.formatMeta(context: Context, locale: Locale = Locale.getDefault()): String =
    EventFormatters.formatMeta(context, this, locale)
