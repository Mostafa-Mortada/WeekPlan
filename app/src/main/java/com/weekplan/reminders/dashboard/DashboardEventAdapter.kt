package com.weekplan.reminders.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.weekplan.reminders.R
import com.weekplan.reminders.data.model.OccurrenceWithEvent
import com.weekplan.reminders.planner.PlannerRows
import com.weekplan.reminders.ui.EventColors
import com.weekplan.reminders.ui.NeoLinearLayout
import com.weekplan.reminders.ui.NeoTextView
import com.weekplan.reminders.ui.formatMeta
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class DashboardEventAdapter(
    private val onEventClick: (OccurrenceWithEvent) -> Unit,
) : ListAdapter<OccurrenceWithEvent, DashboardEventAdapter.EventViewHolder>(OccurrenceDiff) {

    private val locale: Locale = Locale.getDefault()
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    private var overlapsArray: BooleanArray = BooleanArray(0)
    private var finishedOccurrenceKeys: Set<String> = emptySet()

    fun submitOccurrences(list: List<OccurrenceWithEvent>) {
        overlapsArray = PlannerRows.overlapping(list)
        submitList(list)
    }

    fun updateFinishedKeys(keys: Set<String>) {
        if (finishedOccurrenceKeys != keys) {
            finishedOccurrenceKeys = keys
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount)
            }
        }
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
        private val statusBadge = view.findViewById<NeoTextView>(R.id.event_status_badge)

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

            val key = "${occ.eventId}_${occ.occurrenceDate}"
            val isFinished = finishedOccurrenceKeys.contains(key)
            if (isFinished) {
                statusBadge.visibility = View.VISIBLE
                statusBadge.text = ctx.getString(R.string.dashboard_event_finished)
            } else {
                statusBadge.visibility = View.GONE
            }
            card.alpha = 1.0f

            val metaText = event.formatMeta(ctx, locale)
            meta.text = metaText
            meta.visibility = if (metaText.isEmpty()) View.GONE else View.VISIBLE

            val description = StringBuilder(
                ctx.getString(R.string.planner_cd_event, event.title, startText, endText)
            )
            if (metaText.isNotEmpty()) description.append(", ").append(metaText)
            if (overlaps) description.append(", ").append(ctx.getString(R.string.planner_cd_overlap))
            if (isFinished) description.append(", ").append(ctx.getString(R.string.dashboard_event_finished))
            card.contentDescription = description.toString()

            card.setOnClickListener { onEventClick(item) }
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
        ): Boolean = oldItem == newItem
    }
}
