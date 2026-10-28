package com.weekplan.reminders.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.weekplan.reminders.data.dao.EventDao
import com.weekplan.reminders.data.dao.SettingsDao
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Settings

@Database(
    entities = [Event::class, Settings::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    abstract fun settingsDao(): SettingsDao
}
