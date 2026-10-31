package com.weekplan.reminders

import com.weekplan.reminders.planner.PlannerNavigator
import java.time.LocalDate

/**
 * Top-level application navigator implemented by MainActivity.
 * Handles both bottom-tab navigation and drill-down into event forms / details.
 */
interface AppNavigator : PlannerNavigator {
    fun openToday()
    fun openPlanner()
    fun openSettings()
}
