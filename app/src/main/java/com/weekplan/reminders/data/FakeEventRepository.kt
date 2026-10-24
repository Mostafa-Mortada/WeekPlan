package com.weekplan.reminders.data

import com.weekplan.reminders.data.model.Event
import com.weekplan.reminders.data.model.EventOccurrence
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
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun getOccurrences(from: LocalDate, to: LocalDate): List<EventOccurrence> {
        if (latencyMs > 0) delay(latencyMs)
        return events.flatMap { Recurrence.expand(it, from, to) }
    }

    // Helpers to try "the planner refreshes after changes" by hand; Member 2 / 4 do the real ones.
    fun add(event: Event) {
        events = events + event
        _changes.tryEmit(Unit)
    }

    fun delete(eventId: String) {
        events = events.filterNot { it.id == eventId }
        _changes.tryEmit(Unit)
    }
}
