package com.weekplan.reminders

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Frequency
import com.weekplan.reminders.ui.NeoTextView
import com.weekplan.reminders.ui.NeoDialogFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class EventDetailsFragment : Fragment(R.layout.fragment_event_details) {

    private var eventId: Long = -1L
    private lateinit var occurrenceDate: LocalDate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        eventId = requireArguments().getLong(ARG_EVENT_ID)
        occurrenceDate = LocalDate.parse(
            requireArguments().getString(ARG_OCCURRENCE_DATE)
                ?: LocalDate.now().toString()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleText = view.findViewById<TextView>(R.id.details_event_title)
        val dateText = view.findViewById<TextView>(R.id.details_date)
        val timeText = view.findViewById<TextView>(R.id.details_time)
        val notesText = view.findViewById<TextView>(R.id.details_notes)
        val repeatText = view.findViewById<TextView>(R.id.details_repeat)
        val reminderText = view.findViewById<TextView>(R.id.details_reminder)
        val errorText = view.findViewById<TextView>(R.id.details_error)

        val editButton = view.findViewById<NeoTextView>(R.id.details_edit)
        val deleteButton = view.findViewById<NeoTextView>(R.id.details_delete)

        childFragmentManager.setFragmentResultListener("confirm_delete", viewLifecycleOwner) { _, _ ->
            deleteButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    AppGraph.eventRepository.deleteEvent(eventId)
                    parentFragmentManager.popBackStack()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    deleteButton.isEnabled = true
                    errorText.setText(R.string.event_details_delete_error)
                    errorText.visibility = View.VISIBLE
                }
            }
        }
        editButton.isEnabled = false
        deleteButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val event = AppGraph.eventRepository.getEvent(eventId)

                if (event == null) {
                    errorText.text = getString(R.string.event_details_load_error)
                    errorText.visibility = View.VISIBLE
                    return@launch
                }

                titleText.text = event.title

                val dateFormatter =
                    DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())
                val timeFormatter =
                    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

                dateText.text = event.startDate.format(dateFormatter)

                // Show the specific occurrence date if a recurring event was tapped.
                if (event.repeatRule.frequency != Frequency.NONE &&
                    occurrenceDate != event.startDate
                ) {
                    dateText.text = occurrenceDate.format(dateFormatter)
                }

                timeText.text =
                    "${event.startTime.format(timeFormatter)} – ${event.endTime.format(timeFormatter)}"

                notesText.text = event.notes.ifBlank {
                    getString(R.string.event_details_no_notes)
                }

                repeatText.text = getRepeatDescription(event)

                reminderText.text = getReminderDescription(event)

                editButton.isEnabled = true
                deleteButton.isEnabled = true

                editButton.setOnClickListener {
                    parentFragmentManager.commit {
                        replace(
                            R.id.planner_container,
                            EventFormFragment.newInstance(event.id)
                        )
                        addToBackStack("edit_event")
                    }
                }

                deleteButton.setOnClickListener {
                    showDeleteConfirmation()
                }

            } catch (_: Exception) {
                errorText.text = getString(R.string.event_details_load_error)
                errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun getRepeatDescription(event: Event): String {
        return when (event.repeatRule.frequency) {
            Frequency.NONE -> getString(R.string.event_details_no_repeat)
            Frequency.DAILY -> getString(R.string.event_details_daily)
            Frequency.WEEKLY -> {
                val days = event.repeatRule.weekdays
                    .sortedBy { it.value }
                    .joinToString(", ") {
                        it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    }

                if (days.isBlank()) {
                    getString(R.string.event_details_weekly)
                } else {
                    "${getString(R.string.event_details_weekly)} ($days)"
                }
            }
        }
    }

    private fun getReminderDescription(event: Event): String {
        val minutes = event.reminderOffsetMinutes
            ?: return getString(R.string.event_details_no_reminder)

        return when {
            minutes == 0 ->
                getString(R.string.settings_offset_at_start)

            minutes == 60 ->
                getString(R.string.event_details_hour_before)

            minutes > 60 && minutes % 60 == 0 ->
                getString(
                    R.string.event_details_hours_before,
                    minutes / 60
                )

            else ->
                getString(
                    R.string.event_details_minutes_before,
                    minutes
                )
        }
    }

    private fun showDeleteConfirmation() {
        if (childFragmentManager.findFragmentByTag("delete") != null) return
        NeoDialogFragment.confirm("confirm_delete", R.string.event_details_delete_confirm_title,
            R.string.event_details_delete_confirm_message).showNow(childFragmentManager, "delete")
    }

    companion object {
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_OCCURRENCE_DATE = "occurrence_date"

        fun newInstance(
            eventId: Long,
            occurrenceDate: LocalDate
        ): EventDetailsFragment {
            return EventDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_EVENT_ID, eventId)
                    putString(ARG_OCCURRENCE_DATE, occurrenceDate.toString())
                }
            }
        }
    }
}
