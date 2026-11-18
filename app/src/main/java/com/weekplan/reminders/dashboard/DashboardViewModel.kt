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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

enum class DashboardStatus { LOADING, CONTENT, ERROR }

data class DashboardUiState(
    val today: LocalDate,
    val status: DashboardStatus,
    val occurrences: List<OccurrenceWithEvent> = emptyList(),
    val nextUp: OccurrenceWithEvent? = null,
    val isHappeningNow: Boolean = false,
    val lastEvaluatedAt: LocalDateTime = LocalDateTime.MIN,
)

class DashboardViewModel(
    private val repository: EventRepository,
    private val clockNow: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    private var loadJob: Job? = null
    private var tickerJob: Job? = null
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
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                tick()
            }
        }
    }

    fun tick() {
        val currentDate = clockNow().toLocalDate()
        if (currentDate != lastDate) {
            lastDate = currentDate
            load(showLoading = false)
        } else {
            updateNextUpState()
        }
    }

    fun refresh() {
        load(showLoading = true)
    }

    fun onResume() {
        tick()
    }

    private fun load(showLoading: Boolean) {
        loadJob?.cancel()
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

                _state.value = DashboardUiState(
                    today = today,
                    status = DashboardStatus.CONTENT,
                    occurrences = occurrences,
                    nextUp = nextUp,
                    isHappeningNow = isHappeningNow,
                    lastEvaluatedAt = now,
                )
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

    private fun updateNextUpState() {
        val current = _state.value
        if (current.status != DashboardStatus.CONTENT) return

        val now = clockNow()
        val (nextUp, isHappeningNow) = resolveNextUp(current.occurrences, now)

        _state.value = current.copy(
            nextUp = nextUp,
            isHappeningNow = isHappeningNow,
            lastEvaluatedAt = now,
        )
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
