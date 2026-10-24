package com.weekplan.reminders.data

/**
 * Tiny service locator. Member 4 / 5: replace [eventRepository] with the real repository
 * (or move this to whatever dependency setup the team picks).
 */
object AppGraph {
    val eventRepository: EventRepository by lazy { FakeEventRepository(latencyMs = 300) }
}
