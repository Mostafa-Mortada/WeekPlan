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

    private val dateFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

    private val timeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dateArgument = arguments?.getString(ARG_DATE)
        selectedDate = dateArgument?.let {
            LocalDate.parse(it)
        } ?: LocalDate.now()
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

            val event = Event(
                title = title,
                startDate = selectedDate,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                notes = notes
            )

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    AppGraph.eventRepository.createEvent(event)
                    parentFragmentManager.popBackStack()
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

        fun newInstance(date: LocalDate): EventFormFragment {
            return EventFormFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DATE, date.toString())
                }
            }
        }
    }
}
