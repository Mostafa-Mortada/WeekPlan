package com.weekplan.reminders.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.weekplan.reminders.MainActivity
import com.weekplan.reminders.R
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.data.model.Settings
import com.weekplan.reminders.reminders.ReminderService
import com.weekplan.reminders.ui.NeoTextView
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var permStatusChip: NeoTextView
    private lateinit var alarmStatus: TextView
    private lateinit var alarmAction: NeoTextView
    private lateinit var reminderGroup: RadioGroup
    private var isProgrammaticCheck = false
    private var loadSettingsAfterRestore: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        reminderGroup = view.findViewById(R.id.settings_reminder_group)
        permStatusChip = view.findViewById(R.id.settings_perm_status)
        alarmStatus = view.findViewById(R.id.settings_alarm_status)
        alarmAction = view.findViewById(R.id.settings_alarm_action)
        alarmAction.setOnClickListener { openExactAlarmSettings() }
        val permButton = view.findViewById<NeoTextView>(R.id.settings_perm_action_btn)

        val error = view.findViewById<TextView>(R.id.settings_storage_error)
        val retry = view.findViewById<NeoTextView>(R.id.settings_storage_retry)
        var isLoadingSettings = true
        var savedRadioId = R.id.settings_offset_15
        fun enableChoices(enabled: Boolean) {
            for (i in 0 until reminderGroup.childCount) reminderGroup.getChildAt(i).isEnabled = enabled
        }
        fun checkChoice(id: Int) {
            isProgrammaticCheck = true
            reminderGroup.check(id)
            isProgrammaticCheck = false
        }
        fun loadSettings() {
            isLoadingSettings = true
            enableChoices(false)
            error.visibility = View.GONE
            retry.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val settings = AppGraph.eventRepository.getSettings()
                    savedRadioId = OFFSET_TO_RADIO_ID[settings.defaultReminderOffsetMinutes] ?: R.id.settings_offset_15
                    checkChoice(savedRadioId)
                    isLoadingSettings = false
                    enableChoices(true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error.setText(R.string.settings_load_error)
                    error.visibility = View.VISIBLE
                    retry.visibility = View.VISIBLE
                }
            }
        }
        retry.setOnClickListener { loadSettings() }
        loadSettingsAfterRestore = { loadSettings() }
        reminderGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isLoadingSettings || isProgrammaticCheck || checkedId !in RADIO_ID_TO_OFFSET) return@setOnCheckedChangeListener
            enableChoices(false)
            error.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    AppGraph.eventRepository.saveSettings(Settings(defaultReminderOffsetMinutes = RADIO_ID_TO_OFFSET[checkedId]))
                    savedRadioId = checkedId
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    checkChoice(savedRadioId)
                    error.setText(R.string.settings_save_error)
                    error.visibility = View.VISIBLE
                } finally {
                    enableChoices(true)
                }
            }
        }

        permButton.setOnClickListener {
            val requested = (activity as? MainActivity)?.requestReminderPermission(userInitiated = true) == true
            if (!requested) openSystemNotificationSettings()
        }

        updateNotificationStatus()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        loadSettingsAfterRestore?.invoke()
    }

    override fun onDestroyView() {
        loadSettingsAfterRestore = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationStatus()
    }

    private fun updateNotificationStatus() {
        if (!isAdded) return
        val reminders = ReminderService(requireContext())
        val exact = reminders.exactAlarmsAllowed()
        alarmStatus.setText(if (exact) R.string.settings_alarm_exact else R.string.settings_alarm_approximate)
        alarmAction.visibility = if (exact) View.GONE else View.VISIBLE

        val enabled = reminders.notificationsEnabled()
        if (enabled) {
            permStatusChip.text = getString(R.string.settings_perm_status_granted)
            permStatusChip.neoFill = requireContext().getColor(R.color.wp_green)
        } else {
            permStatusChip.text = getString(R.string.settings_perm_status_denied)
            permStatusChip.neoFill = requireContext().getColor(R.color.wp_pink)
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", requireContext().packageName, null),
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.settings_alarm_access_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun openSystemNotificationSettings() {
        try {
            val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(AndroidSettings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }
    }

    companion object {
        private val OFFSET_TO_RADIO_ID = mapOf(
            null to R.id.settings_offset_none,
            0 to R.id.settings_offset_0,
            5 to R.id.settings_offset_5,
            15 to R.id.settings_offset_15,
            30 to R.id.settings_offset_30,
            60 to R.id.settings_offset_60,
        )
        private val RADIO_ID_TO_OFFSET = OFFSET_TO_RADIO_ID.entries.associate { (k, v) -> v to k }
    }
}
