package com.weekplan.reminders.data.model

import java.time.DayOfWeek

data class RepeatRule(
    val frequency: Frequency = Frequency.NONE,
    val weekdays: Set<DayOfWeek> = emptySet()
)