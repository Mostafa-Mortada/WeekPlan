package com.weekplan.reminders.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val notes: String = "",
    val reminderOffsetMinutes: Int? = null,
    @Embedded
    val repeatRule: RepeatRule = RepeatRule()
)