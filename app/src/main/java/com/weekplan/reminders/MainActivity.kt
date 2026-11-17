package com.weekplan.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.weekplan.reminders.dashboard.DashboardFragment
import com.weekplan.reminders.planner.WeeklyPlannerFragment
import com.weekplan.reminders.reminders.ReminderApplication
import com.weekplan.reminders.reminders.ReminderService
import com.weekplan.reminders.settings.SettingsFragment
import com.weekplan.reminders.ui.NeoTextView
import java.time.LocalDate

class MainActivity : AppCompatActivity(), AppNavigator {

    enum class NavTab { TODAY, PLANNER, SETTINGS }

    private var currentTab: NavTab = NavTab.TODAY
    private lateinit var bottomNavBar: View
    private lateinit var tabToday: NeoTextView
    private lateinit var tabPlanner: NeoTextView
    private lateinit var tabSettings: NeoTextView

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        (application as ReminderApplication).restoreReminders()
    }

    internal fun requestReminderPermission(userInitiated: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return false

        val preferences = getPreferences(MODE_PRIVATE)
        val requested = preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
        if (requested && !(userInitiated && shouldShowRequestPermissionRationale(permission))) return false

        preferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        notificationPermission.launch(permission)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the light neobrutalist theme consistent across dark/light modes
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        bottomNavBar = findViewById(R.id.bottom_nav_bar)
        tabToday = findViewById(R.id.nav_tab_today)
        tabPlanner = findViewById(R.id.nav_tab_planner)
        tabSettings = findViewById(R.id.nav_tab_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tabToday.setOnClickListener { selectTab(NavTab.TODAY) }
        tabPlanner.setOnClickListener { selectTab(NavTab.PLANNER) }
        tabSettings.setOnClickListener { selectTab(NavTab.SETTINGS) }

        supportFragmentManager.addOnBackStackChangedListener {
            val hasBackStack = supportFragmentManager.backStackEntryCount > 0
            bottomNavBar.visibility = if (hasBackStack) View.GONE else View.VISIBLE
            if (!hasBackStack) {
                updateTabVisuals()
            }
        }

        if (savedInstanceState != null) {
            val tabName = savedInstanceState.getString(KEY_CURRENT_TAB)
            currentTab = tabName?.let { NavTab.valueOf(it) } ?: NavTab.TODAY
            updateTabVisuals()
        } else {
            selectTab(NavTab.TODAY, addToBackStack = false)
            openReminder(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openReminder(intent)
    }

    private fun openReminder(intent: Intent) {
        val eventId = intent.getStringExtra(ReminderService.EXTRA_EVENT_ID)?.toLongOrNull() ?: return
        val date = intent.getStringExtra(ReminderService.EXTRA_OCCURRENCE_DATE)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return
        openEventDetails(eventId, date)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_TAB, currentTab.name)
    }

    private fun selectTab(tab: NavTab, addToBackStack: Boolean = false) {
        // Clear backstack when switching top-level tabs
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        currentTab = tab
        updateTabVisuals()

        val fragment: Fragment = when (tab) {
            NavTab.TODAY -> DashboardFragment()
            NavTab.PLANNER -> WeeklyPlannerFragment()
            NavTab.SETTINGS -> SettingsFragment()
        }

        supportFragmentManager.commit {
            replace(R.id.planner_container, fragment)
            if (addToBackStack) {
                addToBackStack(tab.name)
            }
        }
    }

    private fun updateTabVisuals() {
        val yellow = ContextCompat.getColor(this, R.color.wp_yellow)
        val paper = ContextCompat.getColor(this, R.color.wp_paper)

        tabToday.neoFill = if (currentTab == NavTab.TODAY) yellow else paper
        tabPlanner.neoFill = if (currentTab == NavTab.PLANNER) yellow else paper
        tabSettings.neoFill = if (currentTab == NavTab.SETTINGS) yellow else paper
    }

    override fun openToday() {
        selectTab(NavTab.TODAY)
    }

    override fun openPlanner() {
        selectTab(NavTab.PLANNER)
    }

    override fun openSettings() {
        selectTab(NavTab.SETTINGS)
    }

    override fun openEventDetails(eventId: Long, occurrenceDate: LocalDate) {
        supportFragmentManager.commit {
            replace(R.id.planner_container, EventDetailsFragment.newInstance(eventId, occurrenceDate))
            addToBackStack("event_details")
        }
    }

    override fun openAddEvent(date: LocalDate) {
        supportFragmentManager.commit {
            replace(R.id.planner_container, EventFormFragment.newInstance(date))
            addToBackStack("event_form")
        }
    }

    companion object {
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val KEY_CURRENT_TAB = "current_nav_tab"
    }
}
