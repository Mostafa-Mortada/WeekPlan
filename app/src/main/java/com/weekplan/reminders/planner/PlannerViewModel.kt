package com.weekplan.reminders.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.weekplan.reminders.data.EventRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlannerViewModel(
    private val repository: EventRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private var weekStart: LocalDate = WeekMath.weekStartOf(today())
    private var shownToday: LocalDate = today()
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(PlannerRows.build(weekStart, shownToday, PlannerStatus.LOADING))
    val state: StateFlow<PlannerUiState> = _state.asStateFlow()

    init {
        load(showSkeleton = true)
        // Any create / edit / delete reloads the visible week, so the planner updates without a restart.
        viewModelScope.launch {
            repository.changes.collect { load(showSkeleton = false) }
        }
    }

    fun showPreviousWeek() = goToWeek(weekStart.minusWeeks(1))

    fun showNextWeek() = goToWeek(weekStart.plusWeeks(1))

    fun showToday() = goToWeek(WeekMath.weekStartOf(today()))

    fun retry() = load(showSkeleton = true)

    /** Call when the screen comes back to the front: the day may have changed while away. */
    fun onResume() {
        if (today() != shownToday) load(showSkeleton = false)
    }

    private fun goToWeek(start: LocalDate) {
        if (start == weekStart && _state.value.status != PlannerStatus.ERROR) return
        weekStart = start
        load(showSkeleton = true)
    }

    private fun load(showSkeleton: Boolean) {
        loadJob?.cancel() // a newer request always wins (fast taps on next / previous)
        val start = weekStart
        val todayNow = today()
        shownToday = todayNow
        if (showSkeleton) _state.value = PlannerRows.build(start, todayNow, PlannerStatus.LOADING)

        loadJob = viewModelScope.launch {
            _state.value = try {
                val occurrences = repository.getOccurrences(start, start.plusDays(6))
                PlannerRows.build(start, todayNow, PlannerStatus.CONTENT, occurrences)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlannerRows.build(start, todayNow, PlannerStatus.ERROR)
            }
        }
    }

    class Factory(private val repository: EventRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PlannerViewModel(repository) as T
    }
}
