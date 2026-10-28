package com.weekplan.reminders.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.weekplan.reminders.data.model.Settings

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: Settings)
}
