package com.weekplan.reminders.data.database

import androidx.room.TypeConverter
import com.weekplan.reminders.data.model.Frequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Converts event date, time, and recurrence values to stable SQLite representations. */
class Converters {

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun frequencyToString(value: Frequency?): String? = value?.name

    @TypeConverter
    fun stringToFrequency(value: String?): Frequency? = value?.let(Frequency::valueOf)

    /** Stores weekdays by enum name in weekday order, producing deterministic values such as `MONDAY,FRIDAY`. */
    @TypeConverter
    fun weekdaysToString(value: Set<DayOfWeek>?): String? =
        value?.sortedBy { it.value }?.joinToString(",") { it.name }

    @TypeConverter
    fun stringToWeekdays(value: String?): Set<DayOfWeek>? {
        if (value == null) return null
        if (value.isEmpty()) return emptySet()
        return value.split(',').mapTo(linkedSetOf()) { DayOfWeek.valueOf(it) }
    }
}
