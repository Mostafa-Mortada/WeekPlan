package com.weekplan.reminders.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.weekplan.reminders.data.model.OccurrenceWithEvent
import com.weekplan.reminders.data.repository.EventRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

enum class DashboardStatus { LOADING, CONTENT, ERROR }

data class DashboardUiState(
    val today: LocalDate,
    val status: DashboardStatus,
    val occurrences: List<OccurrenceWithEvent> = emptyList(),
    val nextUp: OccurrenceWithEvent? = null,
    val isHappeningNow: Boolean = false,
    val finishedOccurrenceKeys: Set<String> = emptySet(),
)

class DashboardViewModel(
    private val repository: EventRepository,
    private val clockNow: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    private var loadJob: Job? = null
    private var boundaryJob: Job? = null
    private var isSchedulerEnabled = true
    private var lastDate: LocalDate = clockNow().toLocalDate()

    private val _state = MutableStateFlow(
        DashboardUiState(
            today = lastDate,
            status = DashboardStatus.LOADING,
        )
    )
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        load(showLoading = true)
        // Auto-refresh when any event is created, edited, or deleted
        viewModelScope.launch {
            repository.changes.collect {
                load(showLoading = false)
            }
        }
    }

    fun startScheduling() {
        isSchedulerEnabled = true
        tick()
    }

    fun stopScheduling() {
        isSchedulerEnabled = false
        boundaryJob?.cancel()
    }

    fun tick() {
        val currentDate = clockNow().toLocalDate()
        if (currentDate != lastDate) {
            lastDate = currentDate
            load(showLoading = false)
        } else {
            updateNextUpState()
            scheduleNextBoundary()
        }
    }

    fun refresh() {
        load(showLoading = true)
    }

    fun onResume() {
        startScheduling()
    }

    private fun load(showLoading: Boolean) {
        loadJob?.cancel()
        boundaryJob?.cancel()
        val today = clockNow().toLocalDate()
        lastDate = today

        if (showLoading) {
            _state.value = _state.value.copy(
                today = today,
                status = DashboardStatus.LOADING,
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val now = clockNow()
                val occurrences = repository.getOccurrences(today, today)
                val (nextUp, isHappeningNow) = resolveNextUp(occurrences, now)
                val finishedKeys = resolveFinishedKeys(occurrences, now)

                _state.value = DashboardUiState(
                    today = today,
                    status = DashboardStatus.CONTENT,
                    occurrences = occurrences,
                    nextUp = nextUp,
                    isHappeningNow = isHappeningNow,
                    finishedOccurrenceKeys = finishedKeys,
                )
                scheduleNextBoundary()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = DashboardUiState(
                    today = today,
                    status = DashboardStatus.ERROR,
                )
            }
        }
    }

    private fun scheduleNextBoundary() {
        boundaryJob?.cancel()
        if (!isSchedulerEnabled) return

        val current = _state.value
        if (current.status != DashboardStatus.CONTENT) return

        val now = clockNow()
        val nextBoundary = getNextBoundary(current.occurrences, now)
        val delayMillis = Duration.between(now, nextBoundary).toMillis().coerceAtLeast(0L)

        boundaryJob = viewModelScope.launch {
            // Buffer with 50ms so clockNow() has definitively crossed the threshold
            delay(delayMillis + 50L)
            tick()
        }
    }

    private fun getNextBoundary(
        occurrences: List<OccurrenceWithEvent>,
        now: LocalDateTime,
    ): LocalDateTime {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val upcomingTimes = mutableListOf<LocalDateTime>()
        upcomingTimes.add(midnight)

        for (item in occurrences) {
            val start = item.occurrence.startDateTime
            if (start.isAfter(now)) {
                upcomingTimes.add(start)
            }
            val end = item.occurrence.endDateTime
            if (end.isAfter(now)) {
                upcomingTimes.add(end)
            }
        }

        return upcomingTimes.minOrNull() ?: midnight
    }

    private fun updateNextUpState() {
        val current = _state.value
        if (current.status != DashboardStatus.CONTENT) return

        val now = clockNow()
        val (nextUp, isHappeningNow) = resolveNextUp(current.occurrences, now)
        val finishedKeys = resolveFinishedKeys(current.occurrences, now)

        if (nextUp != current.nextUp ||
            isHappeningNow != current.isHappeningNow ||
            finishedKeys != current.finishedOccurrenceKeys
        ) {
            _state.value = current.copy(
                nextUp = nextUp,
                isHappeningNow = isHappeningNow,
                finishedOccurrenceKeys = finishedKeys,
            )
        }
    }

    private fun resolveFinishedKeys(
        occurrences: List<OccurrenceWithEvent>,
        now: LocalDateTime,
    ): Set<String> {
        return occurrences.filter {
            now.isAfter(it.occurrence.endDateTime)
        }.map { "${it.occurrence.eventId}_${it.occurrence.occurrenceDate}" }.toSet()
    }

    private fun resolveNextUp(
        occurrences: List<OccurrenceWithEvent>,
        now: LocalDateTime,
    ): Pair<OccurrenceWithEvent?, Boolean> {
        val happeningNow = occurrences.firstOrNull {
            !it.occurrence.startDateTime.isAfter(now) && it.occurrence.endDateTime.isAfter(now)
        }
        val nextUp = happeningNow ?: occurrences.firstOrNull {
            it.occurrence.startDateTime.isAfter(now)
        }
        return Pair(nextUp, happeningNow != null)
    }

    class Factory(private val repository: EventRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(repository) as T
    }
}
