package com.weekplan.reminders.data.repository

import com.weekplan.reminders.data.dao.EventDao
import com.weekplan.reminders.data.dao.SettingsDao
import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.OccurrenceWithEvent
import com.weekplan.reminders.data.model.Settings
import com.weekplan.reminders.data.recurrence.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalDate

class RoomEventRepository(
    private val eventDao: EventDao,
    private val settingsDao: SettingsDao,
) : EventRepository {

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun createEvent(event: Event): Long {
        val id = eventDao.insertEvent(event.copy(id = 0L))
        _changes.tryEmit(Unit)
        return id
    }

    override suspend fun updateEvent(event: Event) {
        require(event.id > 0L) { "An existing event must have a positive ID" }
        check(eventDao.updateEvent(event) > 0) { "Event ${event.id} does not exist" }
        _changes.tryEmit(Unit)
    }

    override suspend fun deleteEvent(eventId: Long) {
        check(eventDao.deleteEvent(eventId) > 0) { "Event $eventId does not exist" }
        _changes.tryEmit(Unit)
    }

    override suspend fun getEvent(eventId: Long): Event? = eventDao.getEvent(eventId)

    override suspend fun getOccurrences(
        from: LocalDate,
        to: LocalDate,
    ): List<OccurrenceWithEvent> {
        if (to.isBefore(from)) return emptyList()

        return eventDao.getAllEvents()
            .flatMap { event ->
                Recurrence.expand(event, from, to).map { occurrence ->
                    OccurrenceWithEvent(event, occurrence)
                }
            }
            .sortedWith(
                compareBy<OccurrenceWithEvent>(
                    { it.occurrence.startDateTime },
                    { it.occurrence.endDateTime },
                    { it.event.title.lowercase() },
                    { it.event.id },
                ),
            )
    }

    override suspend fun getSettings(): Settings =
        settingsDao.getSettings() ?: Settings()

    override suspend fun saveSettings(settings: Settings) {
        settingsDao.saveSettings(settings.copy(id = Settings.SETTINGS_ROW_ID))
        _changes.tryEmit(Unit)
    }
}
