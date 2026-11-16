package com.weekplan.reminders.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import android.widget.RadioGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.weekplan.reminders.R
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.data.model.Settings
import com.weekplan.reminders.ui.NeoTextView
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var permStatusChip: NeoTextView
    private lateinit var reminderGroup: RadioGroup
    private var isProgrammaticCheck = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        reminderGroup = view.findViewById(R.id.settings_reminder_group)
        permStatusChip = view.findViewById(R.id.settings_perm_status)
        val permButton = view.findViewById<NeoTextView>(R.id.settings_perm_action_btn)

        // Load existing settings
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = AppGraph.eventRepository.getSettings()
            isProgrammaticCheck = true
            val radioId = OFFSET_TO_RADIO_ID[settings.defaultReminderOffsetMinutes] ?: R.id.settings_offset_15
            reminderGroup.check(radioId)
            isProgrammaticCheck = false
        }

        reminderGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isProgrammaticCheck) return@setOnCheckedChangeListener

            val offset = if (checkedId in RADIO_ID_TO_OFFSET) {
                RADIO_ID_TO_OFFSET[checkedId]
            } else {
                DEFAULT_FALLBACK_OFFSET
            }

            viewLifecycleOwner.lifecycleScope.launch {
                AppGraph.eventRepository.saveSettings(
                    Settings(defaultReminderOffsetMinutes = offset)
                )
            }
        }

        permButton.setOnClickListener {
            openSystemNotificationSettings()
        }

        updateNotificationStatus()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationStatus()
    }

    private fun updateNotificationStatus() {
        if (!isAdded) return
        val enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        if (enabled) {
            permStatusChip.text = getString(R.string.settings_perm_status_granted)
            permStatusChip.neoFill = requireContext().getColor(R.color.wp_green)
        } else {
            permStatusChip.text = getString(R.string.settings_perm_status_denied)
            permStatusChip.neoFill = requireContext().getColor(R.color.wp_pink)
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
        private const val DEFAULT_FALLBACK_OFFSET = 15
    }
}
