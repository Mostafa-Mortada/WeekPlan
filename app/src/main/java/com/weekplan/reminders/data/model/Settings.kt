package com.weekplan.reminders.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey
    val id: Int = SETTINGS_ROW_ID,
    val defaultReminderOffsetMinutes: Int? = 15,
) {
    companion object {
        const val SETTINGS_ROW_ID = 1
    }
}
