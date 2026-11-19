package com.weekplan.reminders

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.RadioGroup
import android.widget.CheckBox
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Frequency
import com.weekplan.reminders.data.model.RepeatRule
import com.weekplan.reminders.ui.EventTimeDefaults
import com.weekplan.reminders.ui.NeoTextView
import com.weekplan.reminders.ui.NeoDialogFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

class EventFormFragment : Fragment(R.layout.fragment_event_form) {

    private var formInitialized = false
    private var initializeForm: (() -> Unit)? = null
    private lateinit var selectedDate: LocalDate
    private var selectedStartTime = LocalTime.MIDNIGHT
    private var selectedEndTime = LocalTime.of(1, 0)
    private var selectedReminderOffset: Int? = 15
    private var editingEventId: Long? = null
    private var existingEvent: Event? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        formInitialized = savedInstanceState?.getBoolean(STATE_INITIALIZED) ?: formInitialized
        editingEventId = arguments?.takeIf {
            it.containsKey(ARG_EVENT_ID)
        }?.getLong(ARG_EVENT_ID)

        if (savedInstanceState != null) {
            val dateArgument = savedInstanceState.getString(STATE_DATE)
            selectedDate = dateArgument?.let {
                LocalDate.parse(it)
            } ?: (arguments?.getString(ARG_DATE)?.let { LocalDate.parse(it) } ?: LocalDate.now())

            savedInstanceState.getString(STATE_START_TIME)?.let {
                selectedStartTime = LocalTime.parse(it)
            }
            savedInstanceState.getString(STATE_END_TIME)?.let {
                selectedEndTime = LocalTime.parse(it)
            }
            val hasReminder = savedInstanceState.getBoolean(STATE_HAS_REMINDER, false)
            selectedReminderOffset = if (hasReminder && savedInstanceState.containsKey(STATE_REMINDER_OFFSET)) {
                savedInstanceState.getInt(STATE_REMINDER_OFFSET)
            } else {
                null
            }
        } else {
            val dateArgument = arguments?.getString(ARG_DATE)

            val now = LocalDateTime.now()
            selectedDate = dateArgument?.let(LocalDate::parse) ?: now.toLocalDate()
            if (editingEventId == null && !formInitialized) {
                val defaults = EventTimeDefaults.nextHour(now, selectedDate)
                selectedDate = defaults.date
                selectedStartTime = defaults.start
                selectedEndTime = defaults.end
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleInput = view.findViewById<EditText>(R.id.event_title)
        val notesInput = view.findViewById<EditText>(R.id.event_notes)
        val titleLimit = resources.getInteger(R.integer.event_title_max_length)
        val notesLimit = resources.getInteger(R.integer.event_notes_max_length)
        fun updateCount(input: EditText, counterId: Int, limit: Int) {
            view.findViewById<TextView>(counterId).apply {
                text = getString(R.string.event_character_count, input.text.length, limit)
                setTextColor(requireContext().getColor(if (input.text.length > limit) R.color.wp_error else R.color.wp_ink))
            }
        }
        titleInput.doAfterTextChanged { updateCount(titleInput, R.id.event_title_count, titleLimit) }
        notesInput.doAfterTextChanged { updateCount(notesInput, R.id.event_notes_count, notesLimit) }
        updateCount(titleInput, R.id.event_title_count, titleLimit)
        updateCount(notesInput, R.id.event_notes_count, notesLimit)
        val dateButton = view.findViewById<NeoTextView>(R.id.event_date)
        val startButton = view.findViewById<NeoTextView>(R.id.event_start_time)
        val endButton = view.findViewById<NeoTextView>(R.id.event_end_time)
        val reminderButton = view.findViewById<NeoTextView>(R.id.event_reminder)
        val errorText = view.findViewById<TextView>(R.id.form_error)
        val saveButton = view.findViewById<NeoTextView>(R.id.event_save)
        val cancelButton = view.findViewById<NeoTextView>(R.id.event_cancel)
        val heading = view.findViewById<TextView>(R.id.form_heading)
        val retryButton = view.findViewById<View>(R.id.form_retry)
        fun enableFields(enabled: Boolean) {
            fun enableTree(node: View) {
                node.isEnabled = enabled
                if (node is ViewGroup) for (i in 0 until node.childCount) enableTree(node.getChildAt(i))
            }
            enableTree(view.findViewById(R.id.event_form_fields))
            saveButton.isEnabled = enabled
        }
        enableFields(false)

        // Repeat views
        val repeatSwitch = view.findViewById<SwitchCompat>(R.id.repeat_switch)
        val repeatOptionsContainer = view.findViewById<LinearLayout>(R.id.repeat_options_container)
        val repeatGroup = view.findViewById<RadioGroup>(R.id.repeat_frequency_group)
        val weeklyDaysContainer = view.findViewById<LinearLayout>(R.id.weekly_days_container)
        // Weekday checkboxes
        val monday = view.findViewById<CheckBox>(R.id.day_monday)
        val tuesday = view.findViewById<CheckBox>(R.id.day_tuesday)
        val wednesday = view.findViewById<CheckBox>(R.id.day_wednesday)
        val thursday = view.findViewById<CheckBox>(R.id.day_thursday)
        val friday = view.findViewById<CheckBox>(R.id.day_friday)
        val saturday = view.findViewById<CheckBox>(R.id.day_saturday)
        val sunday = view.findViewById<CheckBox>(R.id.day_sunday)
        val weekdayCheckBoxes = listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday)

        val isEditing = editingEventId != null
        if (isEditing) {
            heading.text = getString(R.string.event_edit_title)
            saveButton.text = getString(R.string.event_update)
            saveButton.isEnabled = false
        }

        fun updateRepeatVisibility() {
            val isRepeatOn = repeatSwitch.isChecked
            repeatOptionsContainer.visibility =
                if (isRepeatOn) View.VISIBLE else View.GONE

            weeklyDaysContainer.visibility =
                if (isRepeatOn && repeatGroup.checkedRadioButtonId == R.id.repeat_weekly) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }

        // Repeat switch controls visibility of repeat options
        repeatSwitch.setOnCheckedChangeListener { _, _ ->
            updateRepeatVisibility()
        }

        // Show weekdays only when Weekly is selected
        repeatGroup.setOnCheckedChangeListener { _, _ ->
            updateRepeatVisibility()
        }

        if (savedInstanceState != null) {
            repeatSwitch.isChecked = savedInstanceState.getBoolean(STATE_REPEAT_SWITCH, false)
            val checkedFreq = savedInstanceState.getInt(STATE_REPEAT_GROUP, -1)
            if (checkedFreq != -1) {
                repeatGroup.check(checkedFreq)
            }
            val weekdayChecked = savedInstanceState.getBooleanArray(STATE_WEEKDAYS)
            if (weekdayChecked != null && weekdayChecked.size == weekdayCheckBoxes.size) {
                weekdayCheckBoxes.forEachIndexed { i, cb -> cb.isChecked = weekdayChecked[i] }
            }
        }
        updateRepeatVisibility()

        // Read the selected repeat options
        fun getSelectedRepeatRule(): RepeatRule {

            // Switch OFF means no repetition
            if (!repeatSwitch.isChecked) {
                return RepeatRule(
                    frequency = Frequency.NONE,
                    weekdays = emptySet()
                )
            }

            val frequency = when (repeatGroup.checkedRadioButtonId) {
                R.id.repeat_daily -> Frequency.DAILY
                R.id.repeat_weekly -> Frequency.WEEKLY
                else -> Frequency.NONE
            }

            val selectedDays = mutableSetOf<DayOfWeek>()

            if (frequency == Frequency.WEEKLY) {
                if (monday.isChecked) selectedDays.add(DayOfWeek.MONDAY)
                if (tuesday.isChecked) selectedDays.add(DayOfWeek.TUESDAY)
                if (wednesday.isChecked) selectedDays.add(DayOfWeek.WEDNESDAY)
                if (thursday.isChecked) selectedDays.add(DayOfWeek.THURSDAY)
                if (friday.isChecked) selectedDays.add(DayOfWeek.FRIDAY)
                if (saturday.isChecked) selectedDays.add(DayOfWeek.SATURDAY)
                if (sunday.isChecked) selectedDays.add(DayOfWeek.SUNDAY)
            }
            return RepeatRule(frequency = frequency, weekdays = selectedDays)
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

        fun getReminderLabel(offset: Int?): String = when (offset) {
            null -> getString(R.string.settings_offset_none)
            0 -> getString(R.string.settings_offset_at_start)
            5 -> getString(R.string.settings_offset_5)
            15 -> getString(R.string.settings_offset_15)
            30 -> getString(R.string.settings_offset_30)
            60 -> getString(R.string.settings_offset_60)
            else -> resources.getQuantityString(R.plurals.event_details_minutes_before, offset, offset)
        }

        fun updateReminderText() {
            reminderButton.text = getReminderLabel(selectedReminderOffset)
        }

        updateDateText()
        updateStartText()
        updateEndText()
        updateReminderText()

        // Begin only after Android restores the view hierarchy. A saved Bundle does not
        // imply that the previous asynchronous database load actually completed.
        initializeForm = {
            enableFields(false)
            retryButton.visibility = View.GONE
            errorText.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    if (isEditing) {
                        val event = AppGraph.eventRepository.getEvent(requireNotNull(editingEventId))
                            ?: error("Event no longer exists")
                        existingEvent = event
                        if (!formInitialized) {
                            setExistingText(titleInput, event.title)
                            setExistingText(notesInput, event.notes)

                            selectedDate = event.startDate
                            selectedStartTime = event.startTime
                            selectedEndTime = event.endTime
                            selectedReminderOffset = event.reminderOffsetMinutes

                            // Restore repeat settings
                            val rule = event.repeatRule

                            // Switch is ON only if the event has a repeat frequency
                            repeatSwitch.isChecked =
                                rule.frequency != Frequency.NONE

                            // Restore selected frequency
                            when (rule.frequency) {
                                Frequency.NONE -> repeatGroup.clearCheck()
                                Frequency.DAILY -> repeatGroup.check(R.id.repeat_daily)
                                Frequency.WEEKLY -> repeatGroup.check(R.id.repeat_weekly)
                            }

                            // Restore selected weekdays
                            monday.isChecked = DayOfWeek.MONDAY in rule.weekdays
                            tuesday.isChecked = DayOfWeek.TUESDAY in rule.weekdays
                            wednesday.isChecked = DayOfWeek.WEDNESDAY in rule.weekdays
                            thursday.isChecked = DayOfWeek.THURSDAY in rule.weekdays
                            friday.isChecked = DayOfWeek.FRIDAY in rule.weekdays
                            saturday.isChecked = DayOfWeek.SATURDAY in rule.weekdays
                            sunday.isChecked = DayOfWeek.SUNDAY in rule.weekdays

                            updateRepeatVisibility()
                            updateDateText()
                            updateStartText()
                            updateEndText()
                            updateReminderText()
                        }
                    } else if (!formInitialized) {
                        selectedReminderOffset = AppGraph.eventRepository.getSettings().defaultReminderOffsetMinutes
                        updateReminderText()
                    }
                    formInitialized = true
                    enableFields(true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    errorText.setText(R.string.event_form_load_error)
                    errorText.visibility = View.VISIBLE
                    retryButton.visibility = View.VISIBLE
                }
            }
        }
        retryButton.setOnClickListener { initializeForm?.invoke() }

        val reminderOffsets = listOf(null, 0, 5, 15, 30, 60)
        childFragmentManager.setFragmentResultListener("pick_date", viewLifecycleOwner) { _, result ->
            selectedDate = LocalDate.parse(result.getString(NeoDialogFragment.VALUE))
            updateDateText()
            errorText.visibility = View.GONE
        }
        childFragmentManager.setFragmentResultListener("pick_start", viewLifecycleOwner) { _, result ->
            selectedStartTime = LocalTime.parse(result.getString(NeoDialogFragment.VALUE))
            updateStartText()
            errorText.visibility = View.GONE
        }
        childFragmentManager.setFragmentResultListener("pick_end", viewLifecycleOwner) { _, result ->
            selectedEndTime = LocalTime.parse(result.getString(NeoDialogFragment.VALUE))
            updateEndText()
            errorText.visibility = View.GONE
        }
        childFragmentManager.setFragmentResultListener("pick_reminder", viewLifecycleOwner) { _, result ->
            selectedReminderOffset = reminderOffsets[result.getInt(NeoDialogFragment.INDEX)]
            updateReminderText()
        }
        fun showPicker(picker: NeoDialogFragment) {
            if (childFragmentManager.findFragmentByTag("picker") == null) picker.showNow(childFragmentManager, "picker")
        }
        dateButton.setOnClickListener {
            showPicker(NeoDialogFragment.date("pick_date", R.string.event_date_label, selectedDate))
        }
        startButton.setOnClickListener {
            showPicker(NeoDialogFragment.time("pick_start", R.string.event_start_time_label, selectedStartTime))
        }
        endButton.setOnClickListener {
            showPicker(NeoDialogFragment.time("pick_end", R.string.event_end_time_label, selectedEndTime))
        }
        reminderButton.setOnClickListener {
            showPicker(NeoDialogFragment.choices("pick_reminder", R.string.event_details_reminder,
                reminderOffsets.map { getReminderLabel(it) }.toTypedArray(),
                reminderOffsets.indexOf(selectedReminderOffset).coerceAtLeast(0)))
        }

        // Cancel
        cancelButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Save event
        saveButton.setOnClickListener {

            val title = titleInput.text.toString().trim()
            val notes = notesInput.text.toString().trim()

            val error = when {
                title.isBlank() ->
                    getString(R.string.event_error_title_required)

                titleInput.text.length > titleLimit ->
                    resources.getQuantityString(R.plurals.event_error_title_too_long, titleLimit, titleLimit)

                notesInput.text.length > notesLimit ->
                    resources.getQuantityString(R.plurals.event_error_notes_too_long, notesLimit, notesLimit)

                !selectedEndTime.isAfter(selectedStartTime) ->
                    getString(R.string.event_error_time_order)

                repeatSwitch.isChecked &&
                        repeatGroup.checkedRadioButtonId == -1 ->
                    getString(R.string.event_repeat_frequency_required)

                repeatSwitch.isChecked &&
                        repeatGroup.checkedRadioButtonId == R.id.repeat_weekly &&
                        weekdayCheckBoxes.none { it.isChecked } ->
                    getString(R.string.event_repeat_weekdays_required)

                else -> null
            }

            if (error != null) {
                errorText.text = error
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            saveButton.isEnabled = false
            errorText.visibility = View.GONE

            val selectedRepeatRule = getSelectedRepeatRule()

            val event = existingEvent?.copy(
                title = title,
                startDate = selectedDate,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                notes = notes,
                reminderOffsetMinutes = selectedReminderOffset,
                repeatRule = selectedRepeatRule
            ) ?: Event(
                title = title,
                startDate = selectedDate,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                notes = notes,
                reminderOffsetMinutes = selectedReminderOffset,
                repeatRule = selectedRepeatRule
            )

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    if (existingEvent != null) {
                        AppGraph.eventRepository.updateEvent(event)

                        parentFragmentManager.popBackStack(
                            "event_details",
                            FragmentManager.POP_BACK_STACK_INCLUSIVE
                        )
                    } else {
                        AppGraph.eventRepository.createEvent(event)
                        parentFragmentManager.popBackStack()
                    }

                    if (event.reminderOffsetMinutes != null) {
                        (activity as? MainActivity)?.requestReminderPermission()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    saveButton.isEnabled = true
                    errorText.text = getString(R.string.event_error_save)
                    errorText.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (formInitialized && savedInstanceState != null) {
            savedInstanceState.getString(STATE_TITLE)?.let { setExistingText(requireView().findViewById(R.id.event_title), it) }
            savedInstanceState.getString(STATE_NOTES)?.let { setExistingText(requireView().findViewById(R.id.event_notes), it) }
        }
        initializeForm?.invoke()
    }

    // Existing data can predate the limits. Keep it visible and require an explicit
    // edit instead of silently truncating the saved title or notes on load/rotation.
    private fun setExistingText(input: EditText, text: String) {
        val filters = input.filters
        input.filters = emptyArray()
        input.setText(text)
        input.filters = filters
    }

    override fun onDestroyView() {
        initializeForm = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INITIALIZED, formInitialized)
        outState.putString(STATE_DATE, selectedDate.toString())
        outState.putString(STATE_START_TIME, selectedStartTime.toString())
        outState.putString(STATE_END_TIME, selectedEndTime.toString())
        val offset = selectedReminderOffset
        outState.putBoolean(STATE_HAS_REMINDER, offset != null)
        if (offset != null) {
            outState.putInt(STATE_REMINDER_OFFSET, offset)
        }

        view?.let { v ->
            outState.putString(STATE_TITLE, v.findViewById<EditText>(R.id.event_title).text.toString())
            outState.putString(STATE_NOTES, v.findViewById<EditText>(R.id.event_notes).text.toString())
            val repeatSwitch = v.findViewById<SwitchCompat>(R.id.repeat_switch)
            val repeatGroup = v.findViewById<RadioGroup>(R.id.repeat_frequency_group)
            val monday = v.findViewById<CheckBox>(R.id.day_monday)
            val tuesday = v.findViewById<CheckBox>(R.id.day_tuesday)
            val wednesday = v.findViewById<CheckBox>(R.id.day_wednesday)
            val thursday = v.findViewById<CheckBox>(R.id.day_thursday)
            val friday = v.findViewById<CheckBox>(R.id.day_friday)
            val saturday = v.findViewById<CheckBox>(R.id.day_saturday)
            val sunday = v.findViewById<CheckBox>(R.id.day_sunday)

            if (repeatSwitch != null) {
                outState.putBoolean(STATE_REPEAT_SWITCH, repeatSwitch.isChecked)
            }
            if (repeatGroup != null) {
                outState.putInt(STATE_REPEAT_GROUP, repeatGroup.checkedRadioButtonId)
            }
            if (monday != null && tuesday != null && wednesday != null && thursday != null &&
                friday != null && saturday != null && sunday != null
            ) {
                outState.putBooleanArray(
                    STATE_WEEKDAYS,
                    booleanArrayOf(
                        monday.isChecked,
                        tuesday.isChecked,
                        wednesday.isChecked,
                        thursday.isChecked,
                        friday.isChecked,
                        saturday.isChecked,
                        sunday.isChecked
                    )
                )
            }
        }
    }

    companion object {
        private const val ARG_DATE = "event_date"
        private const val ARG_EVENT_ID = "event_id"

        private const val STATE_TITLE = "state_title"
        private const val STATE_NOTES = "state_notes"
        private const val STATE_INITIALIZED = "state_form_initialized"
        private const val STATE_DATE = "state_selected_date"
        private const val STATE_START_TIME = "state_selected_start_time"
        private const val STATE_END_TIME = "state_selected_end_time"
        private const val STATE_HAS_REMINDER = "state_has_reminder"
        private const val STATE_REMINDER_OFFSET = "state_selected_reminder_offset"
        private const val STATE_REPEAT_SWITCH = "state_repeat_switch"
        private const val STATE_REPEAT_GROUP = "state_repeat_group"
        private const val STATE_WEEKDAYS = "state_weekdays"

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
