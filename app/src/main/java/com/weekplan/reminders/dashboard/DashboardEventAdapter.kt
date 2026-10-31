package com.weekplan.reminders.dashboard

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
import com.weekplan.reminders.data.model.OccurrenceWithEvent
import com.weekplan.reminders.planner.PlannerRows
import com.weekplan.reminders.ui.EventColors
import com.weekplan.reminders.ui.NeoLinearLayout
import com.weekplan.reminders.ui.NeoTextView
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

class DashboardEventAdapter(
    private val onEventClick: (OccurrenceWithEvent) -> Unit,
) : ListAdapter<OccurrenceWithEvent, DashboardEventAdapter.EventViewHolder>(OccurrenceDiff) {

    private val locale: Locale = Locale.getDefault()
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    private var overlapsArray: BooleanArray = BooleanArray(0)

    fun submitOccurrences(list: List<OccurrenceWithEvent>) {
        overlapsArray = PlannerRows.overlapping(list)
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val item = getItem(position)
        val overlaps = if (position < overlapsArray.size) overlapsArray[position] else false
        holder.bind(item, overlaps)
    }

    inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as NeoLinearLayout
        private val start = view.findViewById<TextView>(R.id.event_start)
        private val end = view.findViewById<TextView>(R.id.event_end)
        private val title = view.findViewById<TextView>(R.id.event_title)
        private val meta = view.findViewById<TextView>(R.id.event_meta)
        private val overlap = view.findViewById<NeoTextView>(R.id.event_overlap)

        fun bind(item: OccurrenceWithEvent, overlaps: Boolean) {
            val ctx = itemView.context
            val occ = item.occurrence
            val event = item.event
            val startText = occ.startDateTime.format(timeFormat)
            val endText = occ.endDateTime.format(timeFormat)

            card.neoFill = EventColors.colorFor(ctx, event.id)
            start.text = startText
            end.text = ctx.getString(R.string.planner_time_to, endText)
            title.text = event.title
            overlap.visibility = if (overlaps) View.VISIBLE else View.GONE

            val metaText = formatMeta(ctx, event)
            meta.text = metaText
            meta.visibility = if (metaText.isEmpty()) View.GONE else View.VISIBLE

            val description = StringBuilder(
                ctx.getString(R.string.planner_cd_event, event.title, startText, endText)
            )
            if (metaText.isNotEmpty()) description.append(", ").append(metaText)
            if (overlaps) description.append(", ").append(ctx.getString(R.string.planner_cd_overlap))
            card.contentDescription = description.toString()

            card.setOnClickListener { onEventClick(item) }
        }

        private fun formatMeta(ctx: Context, event: Event): String {
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
                    else ctx.getString(R.string.planner_reminder_before, minutes)
                )
            }
            return parts.joinToString(" · ")
        }
    }

    private object OccurrenceDiff : DiffUtil.ItemCallback<OccurrenceWithEvent>() {
        override fun areItemsTheSame(
            oldItem: OccurrenceWithEvent,
            newItem: OccurrenceWithEvent,
        ): Boolean =
            oldItem.occurrence.eventId == newItem.occurrence.eventId &&
                    oldItem.occurrence.occurrenceDate == newItem.occurrence.occurrenceDate

        override fun areContentsTheSame(
            oldItem: OccurrenceWithEvent,
            newItem: OccurrenceWithEvent,
        ): Boolean =
            oldItem == newItem
    }
}
