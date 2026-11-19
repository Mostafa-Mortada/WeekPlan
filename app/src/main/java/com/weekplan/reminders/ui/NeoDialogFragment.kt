package com.weekplan.reminders.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import android.widget.DatePicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.weekplan.reminders.R
import java.time.LocalDate
import java.time.LocalTime

/** XML dialog shell shared by pickers, reminder choices and delete confirmation.
 * Arguments + saved state retain an unfinished selection across activity recreation.
 * Fragment results avoid holding callbacks into a destroyed form.
 */
class NeoDialogFragment : DialogFragment() {
    private var datePicker: DatePicker? = null
    private var timePicker: TimePicker? = null
    private var selectedChoice = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val dialog = AppCompatDialog(requireContext())
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = layoutInflater.inflate(R.layout.dialog_neo, FrameLayout(requireContext()), false)
        dialog.setContentView(content)
        content.findViewById<TextView>(R.id.dialog_title).setText(args.getInt(TITLE))
        ViewCompat.setAccessibilityHeading(content.findViewById(R.id.dialog_title), true)
        val mode = args.getString(MODE)
        val pickerDate = content.findViewById<DatePicker>(R.id.dialog_date)
        val pickerTime = content.findViewById<TimePicker>(R.id.dialog_time)
        datePicker = pickerDate
        timePicker = pickerTime
        val state = savedInstanceState ?: args
        when (mode) {
            DATE -> {
                val date = LocalDate.parse(state.getString(VALUE))
                pickerDate.visibility = View.VISIBLE
                pickerDate.init(date.year, date.monthValue - 1, date.dayOfMonth, null)
            }
            TIME -> {
                val time = LocalTime.parse(state.getString(VALUE))
                pickerTime.visibility = View.VISIBLE
                pickerTime.setIs24HourView(DateFormat.is24HourFormat(requireContext()))
                pickerTime.hour = time.hour
                pickerTime.minute = time.minute
            }
            CHOICE -> {
                val choices = content.findViewById<RadioGroup>(R.id.dialog_choices)
                choices.visibility = View.VISIBLE
                selectedChoice = state.getInt(INDEX)
                args.getStringArray(LABELS)!!.forEachIndexed { index, label ->
                    val radio = layoutInflater.inflate(R.layout.item_dialog_choice, choices, false) as RadioButton
                    radio.id = View.generateViewId()
                    radio.text = label
                    choices.addView(radio)
                    radio.isChecked = index == selectedChoice
                    radio.setOnCheckedChangeListener { _, checked -> if (checked) selectedChoice = index }
                }
            }
            CONFIRM -> {
                content.findViewById<TextView>(R.id.dialog_message).apply {
                    visibility = View.VISIBLE
                    setText(args.getInt(MESSAGE))
                }
            }
        }
        content.findViewById<View>(R.id.dialog_cancel).setOnClickListener { dismiss() }
        content.findViewById<NeoTextView>(R.id.dialog_confirm).apply {
            if (mode == CONFIRM) {
                setText(R.string.event_details_delete_confirm)
                neoFill = requireContext().getColor(R.color.wp_pink)
            }
            setOnClickListener {
                // Commit typed spinner values before reading them.
                content.clearFocus()
                val result = when (mode) {
                    DATE -> bundleOf(VALUE to LocalDate.of(pickerDate.year, pickerDate.month + 1, pickerDate.dayOfMonth).toString())
                    TIME -> bundleOf(VALUE to LocalTime.of(pickerTime.hour, pickerTime.minute).toString())
                    CHOICE -> bundleOf(INDEX to selectedChoice)
                    else -> Bundle.EMPTY
                }
                parentFragmentManager.setFragmentResult(args.getString(REQUEST)!!, result)
                dismiss()
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val density = resources.displayMetrics.density
        val width = minOf(resources.displayMetrics.widthPixels - (32 * density).toInt(), (420 * density).toInt())
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val maxHeight = (resources.displayMetrics.heightPixels * 0.85f).toInt()
            val content = findViewById<View>(android.R.id.content)
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
            setLayout(width, minOf(content.measuredHeight, maxHeight))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        when (requireArguments().getString(MODE)) {
            DATE -> datePicker?.let { outState.putString(VALUE, LocalDate.of(it.year, it.month + 1, it.dayOfMonth).toString()) }
            TIME -> timePicker?.let { outState.putString(VALUE, LocalTime.of(it.hour, it.minute).toString()) }
            CHOICE -> outState.putInt(INDEX, selectedChoice)
        }
    }

    override fun onDestroyView() {
        datePicker = null
        timePicker = null
        super.onDestroyView()
    }

    companion object {
        const val VALUE = "value"
        const val INDEX = "index"
        private const val MODE = "mode"
        private const val TITLE = "title"
        private const val MESSAGE = "message"
        private const val REQUEST = "request"
        private const val LABELS = "labels"
        private const val DATE = "date"
        private const val TIME = "time"
        private const val CHOICE = "choice"
        private const val CONFIRM = "confirm"

        fun date(request: String, title: Int, date: LocalDate) = new(DATE, request, title).apply {
            requireArguments().putString(VALUE, date.toString())
        }
        fun time(request: String, title: Int, time: LocalTime) = new(TIME, request, title).apply {
            requireArguments().putString(VALUE, time.toString())
        }
        fun choices(request: String, title: Int, labels: Array<String>, index: Int) = new(CHOICE, request, title).apply {
            requireArguments().putStringArray(LABELS, labels)
            requireArguments().putInt(INDEX, index)
        }
        fun confirm(request: String, title: Int, message: Int) = new(CONFIRM, request, title).apply {
            requireArguments().putInt(MESSAGE, message)
        }
        private fun new(mode: String, request: String, title: Int) = NeoDialogFragment().apply {
            arguments = bundleOf(MODE to mode, REQUEST to request, TITLE to title)
        }
    }
}
