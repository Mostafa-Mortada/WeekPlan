package com.weekplan.reminders.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.weekplan.reminders.data.model.Event

@Dao
interface EventDao {

    @Insert
    suspend fun insertEvent(event: Event): Long

    /** Returns the number of rows updated, so callers can detect a missing event ID. */
    @Update
    suspend fun updateEvent(event: Event): Int

    /** Returns the number of rows deleted, so callers can detect a missing event ID. */
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: Long): Int

    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    suspend fun getEvent(eventId: Long): Event?

    @Query("SELECT * FROM events ORDER BY startDate, startTime, id")
    suspend fun getAllEvents(): List<Event>
}
