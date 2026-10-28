package com.weekplan.reminders.planner

import android.animation.ObjectAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weekplan.reminders.R
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Frequency
import com.weekplan.reminders.ui.EventColors
import com.weekplan.reminders.ui.NeoLinearLayout
import com.weekplan.reminders.ui.NeoTextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Draws [PlannerRow]s: day headers, event cards, empty / loading / error states. */
class PlannerAdapter(
    private val onEventClick: (PlannerRow.Event) -> Unit,
    private val onAddClick: (LocalDate) -> Unit,
    private val onRetry: () -> Unit,
) : ListAdapter<PlannerRow, RecyclerView.ViewHolder>(PlannerRowDiff) {

    private val locale: Locale = Locale.getDefault()
    private val dayNameFormat = DateTimeFormatter.ofPattern("EEEE", locale)
    private val shortDateFormat = DateTimeFormatter.ofPattern("MMM d", locale)
    private val fullDateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is PlannerRow.DayHeader -> TYPE_HEADER
        is PlannerRow.Event -> TYPE_EVENT
        is PlannerRow.EmptyDay -> TYPE_EMPTY_DAY
        is PlannerRow.WeekEmpty -> TYPE_WEEK_EMPTY
        is PlannerRow.Skeleton -> TYPE_SKELETON
        is PlannerRow.Error -> TYPE_ERROR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        fun inflate(layout: Int) = inflater.inflate(layout, parent, false)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflate(R.layout.item_day_header))
            TYPE_EVENT -> EventHolder(inflate(R.layout.item_event))
            TYPE_EMPTY_DAY -> StaticHolder(inflate(R.layout.item_empty_day))
            TYPE_WEEK_EMPTY -> WeekEmptyHolder(inflate(R.layout.item_week_empty))
            TYPE_SKELETON -> SkeletonHolder(inflate(R.layout.item_skeleton))
            else -> ErrorHolder(inflate(R.layout.item_error))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is PlannerRow.DayHeader -> (holder as HeaderHolder).bind(row)
            is PlannerRow.Event -> (holder as EventHolder).bind(row)
            is PlannerRow.WeekEmpty -> (holder as WeekEmptyHolder).bind(row)
            is PlannerRow.Error -> (holder as ErrorHolder).bind()
            is PlannerRow.EmptyDay, is PlannerRow.Skeleton -> Unit
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        (holder as? SkeletonHolder)?.startPulse()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        (holder as? SkeletonHolder)?.stopPulse()
    }

    // ---- view holders ----

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as NeoLinearLayout
        private val dayName = view.findViewById<TextView>(R.id.day_name)
        private val badge = view.findViewById<View>(R.id.today_badge)
        private val subtitle = view.findViewById<TextView>(R.id.day_subtitle)
        private val add = view.findViewById<View>(R.id.day_add)
        private val green = view.context.getColor(R.color.wp_green)
        private val white = view.context.getColor(R.color.wp_paper)

        fun bind(row: PlannerRow.DayHeader) {
            val ctx = itemView.context
            card.neoFill = if (row.isToday) green else white
            dayName.text = row.date.format(dayNameFormat)
            badge.visibility = if (row.isToday) View.VISIBLE else View.GONE

            val count = if (row.eventCount == 0) {
                ctx.getString(R.string.planner_no_events)
            } else {
                ctx.resources.getQuantityString(R.plurals.planner_event_count, row.eventCount, row.eventCount)
            }
            subtitle.text = ctx.getString(R.string.planner_day_subtitle, row.date.format(shortDateFormat), count)

            val fullDate = row.date.format(fullDateFormat)
            card.contentDescription = listOfNotNull(
                fullDate,
                if (row.isToday) ctx.getString(R.string.planner_cd_today) else null,
                count,
            ).joinToString(", ")
            add.contentDescription = ctx.getString(R.string.planner_add_event_cd, fullDate)
            add.setOnClickListener { onAddClick(row.date) }
        }
    }

    private inner class EventHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as NeoLinearLayout
        private val start = view.findViewById<TextView>(R.id.event_start)
        private val end = view.findViewById<TextView>(R.id.event_end)
        private val title = view.findViewById<TextView>(R.id.event_title)
        private val meta = view.findViewById<TextView>(R.id.event_meta)
        private val overlap = view.findViewById<NeoTextView>(R.id.event_overlap)

        fun bind(row: PlannerRow.Event) {
            val ctx = itemView.context
            val item = row.item
            val occ = item.occurrence
            val event = item.event
            val startText = occ.startDateTime.format(timeFormat)
            val endText = occ.endDateTime.format(timeFormat)

            card.neoFill = EventColors.colorFor(ctx, event.id)
            start.text = startText
            end.text = ctx.getString(R.string.planner_time_to, endText)
            title.text = event.title
            overlap.visibility = if (row.overlaps) View.VISIBLE else View.GONE

            val metaText = metaLine(ctx, event)
            meta.text = metaText
            meta.visibility = if (metaText.isEmpty()) View.GONE else View.VISIBLE

            val description = StringBuilder(
                ctx.getString(R.string.planner_cd_event, event.title, startText, endText),
            )
            if (metaText.isNotEmpty()) description.append(", ").append(metaText)
            if (row.overlaps) description.append(", ").append(ctx.getString(R.string.planner_cd_overlap))
            card.contentDescription = description.toString()

            card.setOnClickListener { onEventClick(row) }
        }
    }

    private inner class WeekEmptyHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val button = view.findViewById<View>(R.id.week_empty_add)
        fun bind(row: PlannerRow.WeekEmpty) = button.setOnClickListener { onAddClick(row.addDate) }
    }

    private inner class ErrorHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val button = view.findViewById<View>(R.id.error_retry)
        fun bind() = button.setOnClickListener { onRetry() }
    }

    private class SkeletonHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val pulse = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.45f).apply {
            duration = 700
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
        }

        fun startPulse() = pulse.start()

        fun stopPulse() {
            pulse.cancel()
            itemView.alpha = 1f
        }
    }

    private class StaticHolder(view: View) : RecyclerView.ViewHolder(view)

    // ---- helpers ----

    /** "↻ Daily · Reminder: 15 min before", or empty when the event has neither. */
    private fun metaLine(ctx: Context, event: Event): String {
        val parts = ArrayList<String>(2)
        when (event.repeatRule.frequency) {
            Frequency.NONE, Frequency.ONCE -> Unit
            Frequency.DAILY -> parts.add(ctx.getString(R.string.planner_repeat_daily))
            Frequency.WEEKLY -> {
                val days = event.repeatRule.weekdays
                    .sortedBy { it.value }
                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
                if (days.isNotEmpty()) parts.add(ctx.getString(R.string.planner_repeat_days, days))
                else parts.add(ctx.getString(R.string.planner_repeat_weekly))
            }
        }
        event.reminderOffsetMinutes?.let { minutes ->
            parts.add(
                if (minutes == 0) ctx.getString(R.string.planner_reminder_at_start)
                else ctx.getString(R.string.planner_reminder_before, minutes),
            )
        }
        return parts.joinToString(" · ")
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_EVENT = 1
        const val TYPE_EMPTY_DAY = 2
        const val TYPE_WEEK_EMPTY = 3
        const val TYPE_SKELETON = 4
        const val TYPE_ERROR = 5
    }
}

private object PlannerRowDiff : DiffUtil.ItemCallback<PlannerRow>() {
    override fun areItemsTheSame(oldItem: PlannerRow, newItem: PlannerRow): Boolean = oldItem.key == newItem.key

    override fun areContentsTheSame(oldItem: PlannerRow, newItem: PlannerRow): Boolean = oldItem == newItem
}
