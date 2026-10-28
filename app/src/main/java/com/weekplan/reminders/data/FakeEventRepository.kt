package com.weekplan.reminders.data

import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.Settings
import com.weekplan.reminders.data.model.OccurrenceWithEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalDate

/**
 * In-memory stand-in for Member 4's repository, so the planner runs before real storage exists.
 * [latencyMs] fakes a slow load so the skeleton state is visible while developing.
 * Not for release: swap it in AppGraph.
 */
class FakeEventRepository(
    seed: List<Event> = SampleData.events(LocalDate.now()),
    private val latencyMs: Long = 0,
) : EventRepository {

    @Volatile
    private var events: List<Event> = seed
        .mapIndexed { index, event ->
            if (event.id <= 0L) event.copy(id = index + 1L) else event
        }

    @Volatile
    private var settings = Settings()

    @Volatile
    private var nextId = (events.maxOfOrNull { it.id } ?: 0L) + 1L
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun getOccurrences(from: LocalDate, to: LocalDate): List<OccurrenceWithEvent> {
        if (latencyMs > 0) delay(latencyMs)
        return events.flatMap { event ->
            Recurrence.expand(event, from, to).map { occurrence ->
                OccurrenceWithEvent(event, occurrence)
            }
        }
    }

    override suspend fun createEvent(event: Event): Long {
        val id = if (event.id > 0L) event.id else nextId++
        events = events.filterNot { it.id == id } + event.copy(id = id)
        _changes.emit(Unit)
        return id
    }

    override suspend fun updateEvent(event: Event) {
        require(event.id > 0L) { "An existing event must have a positive ID" }
        val index = events.indexOfFirst { it.id == event.id }
        require(index >= 0) { "Event ${event.id} does not exist" }
        events = events.toMutableList().also { it[index] = event }
        _changes.emit(Unit)
    }

    override suspend fun deleteEvent(eventId: Long) {
        val updated = events.filterNot { it.id == eventId }
        if (updated.size != events.size) {
            events = updated
            _changes.emit(Unit)
        }
    }

    override suspend fun getEvent(eventId: Long): Event? = events.firstOrNull { it.id == eventId }

    override suspend fun getSettings(): Settings = settings

    override suspend fun saveSettings(settings: Settings) {
        this.settings = settings
        _changes.emit(Unit)
    }
}
