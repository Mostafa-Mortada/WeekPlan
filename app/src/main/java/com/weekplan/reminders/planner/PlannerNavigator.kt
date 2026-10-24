package com.weekplan.reminders.planner

import java.time.LocalDate

/**
 * What the planner needs from the rest of the app. The host activity implements it, so Member 5's
 * navigation can decide where each call goes (Member 2's detail screen and add form).
 */
interface PlannerNavigator {

    /** An event was tapped. [occurrenceDate] says which day of a recurring series was tapped. */
    fun openEventDetails(eventId: String, occurrenceDate: LocalDate)

    /** Open the add form with [date] already filled in. */
    fun openAddEvent(date: LocalDate)
}
