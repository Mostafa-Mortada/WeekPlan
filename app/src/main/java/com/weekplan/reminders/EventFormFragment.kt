
package com.weekplan.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.ui.NeoTextView
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class EventFormFragment : Fragment(R.layout.fragment_event_form) {

    private lateinit var selectedDate: LocalDate
    private var selectedStartTime = LocalTime.of(9, 0)
    private var selectedEndTime = LocalTime.of(10, 0)

    private var editingEventId: Long? = null
    private var existingEvent: Event? = null

    private val dateFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

    private val timeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dateArgument = arguments?.getString(ARG_DATE)
        selectedDate = dateArgument?.let { LocalDate.parse(it) } ?: LocalDate.now()

        editingEventId = arguments?.takeIf {
            it.containsKey(ARG_EVENT_ID)
        }?.getLong(ARG_EVENT_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleInput = view.findViewById<EditText>(R.id.event_title)
        val notesInput = view.findViewById<EditText>(R.id.event_notes)
        val dateButton = view.findViewById<NeoTextView>(R.id.event_date)
        val startButton = view.findViewById<NeoTextView>(R.id.event_start_time)
        val endButton = view.findViewById<NeoTextView>(R.id.event_end_time)
        val errorText = view.findViewById<TextView>(R.id.form_error)
        val saveButton = view.findViewById<NeoTextView>(R.id.event_save)
        val cancelButton = view.findViewById<NeoTextView>(R.id.event_cancel)
        val heading = view.findViewById<TextView>(R.id.form_heading)

        val isEditing = editingEventId != null

        if (isEditing) {
            heading.text = getString(R.string.event_edit_title)
            saveButton.text = getString(R.string.event_update)
            saveButton.isEnabled = false
        }

        fun updateDateText() {
            dateButton.text = selectedDate.format(dateFormatter)
        }

        fun updateStartText() {
            startButton.text = selectedStartTime.format(timeFormatter)
        }

        fun updateEndText() {
            endButton.text = selectedEndTime.format(timeFormatter)
        }

        updateDateText()
        updateStartText()
        updateEndText()

        if (isEditing) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val event = AppGraph.eventRepository.getEvent(editingEventId!!)

                    if (event == null) {
                        errorText.text = getString(R.string.event_details_load_error)
                        errorText.visibility = View.VISIBLE
                        return@launch
                    }

                    existingEvent = event
                    titleInput.setText(event.title)
                    notesInput.setText(event.notes)

                    selectedDate = event.startDate
                    selectedStartTime = event.startTime
                    selectedEndTime = event.endTime

                    updateDateText()
                    updateStartText()
                    updateEndText()

                    saveButton.isEnabled = true

                } catch (_: Exception) {
                    errorText.text = getString(R.string.event_error_save)
                    errorText.visibility = View.VISIBLE
                }
            }
        }

        dateButton.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    selectedDate = LocalDate.of(year, month + 1, day)
                    updateDateText()
                    errorText.visibility = View.GONE
                },
                selectedDate.year,
                selectedDate.monthValue - 1,
                selectedDate.dayOfMonth
            ).show()
        }

        startButton.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    selectedStartTime = LocalTime.of(hour, minute)
                    updateStartText()
                    errorText.visibility = View.GONE
                },
                selectedStartTime.hour,
                selectedStartTime.minute,
                false
            ).show()
        }

        endButton.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    selectedEndTime = LocalTime.of(hour, minute)
                    updateEndText()
                    errorText.visibility = View.GONE
                },
                selectedEndTime.hour,
                selectedEndTime.minute,
                false
            ).show()
        }

        cancelButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        saveButton.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val notes = notesInput.text.toString().trim()

            val error = when {
                title.isBlank() ->
                    getString(R.string.event_error_title_required)

                !selectedEndTime.isAfter(selectedStartTime) ->
                    getString(R.string.event_error_time_order)

                else -> null
            }

            if (error != null) {
                errorText.text = error
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            saveButton.isEnabled = false
            errorText.visibility = View.GONE

            val event = existingEvent?.copy(
                title = title,
                startDate = selectedDate,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                notes = notes
            ) ?: Event(
                title = title,
                startDate = selectedDate,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                notes = notes
            )

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    if (existingEvent != null) {
                        AppGraph.eventRepository.updateEvent(event)
                        parentFragmentManager.popBackStack("event_details", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    } else {
                        AppGraph.eventRepository.createEvent(event)
                        parentFragmentManager.popBackStack()
                    }

                } catch (_: Exception) {
                    saveButton.isEnabled = true
                    errorText.text = getString(R.string.event_error_save)
                    errorText.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        private const val ARG_DATE = "event_date"
        private const val ARG_EVENT_ID = "event_id"

        fun newInstance(date: LocalDate): EventFormFragment {
            return EventFormFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DATE, date.toString())
                }
            }
        }

        fun newInstance(eventId: Long): EventFormFragment {
            return EventFormFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_EVENT_ID, eventId)
                }
            }
        }
    }
}
