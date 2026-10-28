package com.weekplan.reminders.data

import android.content.Context
import androidx.room.Room
import com.weekplan.reminders.data.database.AppDatabase
import com.weekplan.reminders.data.repository.EventRepository
import com.weekplan.reminders.data.repository.RoomEventRepository

object AppGraph {
    @Volatile
    private var database: AppDatabase? = null

    @Volatile
    private var repository: EventRepository? = null

    val eventRepository: EventRepository
        get() = checkNotNull(repository) {
            "AppGraph.initialize(context) must be called before accessing eventRepository"
        }

    @Synchronized
    fun initialize(context: Context) {
        if (repository != null) return

        val appContext = context.applicationContext
        val db = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "week_plan_reminders.db",
        ).build()
        database = db
        repository = RoomEventRepository(db.eventDao(), db.settingsDao())
    }
}
