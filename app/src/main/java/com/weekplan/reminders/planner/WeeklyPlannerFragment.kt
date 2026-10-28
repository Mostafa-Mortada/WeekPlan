package com.weekplan.reminders.planner

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import android.widget.TextView
import com.weekplan.reminders.R
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.ui.NeoTextView
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Member 1: the weekly planner. Week range + previous / today / next controls, then the week as a
 * day-by-day agenda. The host activity should implement [PlannerNavigator].
 */
class WeeklyPlannerFragment : Fragment(R.layout.fragment_weekly_planner) {

    private val viewModel: PlannerViewModel by viewModels {
        PlannerViewModel.Factory(AppGraph.eventRepository)
    }

    private val navigator: PlannerNavigator? get() = activity as? PlannerNavigator

    /** The week we already scrolled for, so a background refresh doesn't move the list. */
    private var scrolledWeek: LocalDate? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.planner_list)
        val weekRange = view.findViewById<TextView>(R.id.week_range)
        val weekChip = view.findViewById<NeoTextView>(R.id.week_chip)
        val prev = view.findViewById<View>(R.id.week_prev)
        val today = view.findViewById<NeoTextView>(R.id.week_today)
        val next = view.findViewById<View>(R.id.week_next)

        val adapter = PlannerAdapter(
            onEventClick = { row -> navigator?.openEventDetails(row.item.occurrence.eventId, row.item.occurrence.occurrenceDate) },
            onAddClick = { date -> navigator?.openAddEvent(date) },
            onRetry = viewModel::retry,
        )
        val layoutManager = LinearLayoutManager(requireContext())
        list.layoutManager = layoutManager
        list.adapter = adapter
        (list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        prev.setOnClickListener { viewModel.showPreviousWeek() }
        next.setOnClickListener { viewModel.showNextWeek() }
        today.setOnClickListener { viewModel.showToday() }

        val white = requireContext().getColor(R.color.wp_paper)
        val yellow = requireContext().getColor(R.color.wp_yellow)
        val green = requireContext().getColor(R.color.wp_green)

        fun render(state: PlannerUiState) {
            weekRange.text = WeekMath.formatWeekRange(state.weekStart)
            weekChip.text = relativeLabel(state.weekOffset)
            weekChip.neoFill = if (state.weekOffset == 0) green else white
            // "Today" only matters when you are looking at another week.
            today.neoFill = if (state.weekOffset == 0) white else yellow
            today.alpha = if (state.weekOffset == 0) 0.55f else 1f

            adapter.submitList(state.rows) {
                if (state.status != PlannerStatus.LOADING && state.weekStart != scrolledWeek) {
                    scrolledWeek = state.weekStart
                    // Land on today's header in the current week, otherwise on Monday.
                    layoutManager.scrollToPositionWithOffset(state.todayRowIndex ?: 0, 0)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun relativeLabel(offset: Int): String = when {
        offset == 0 -> getString(R.string.planner_rel_this_week)
        offset == 1 -> getString(R.string.planner_rel_next_week)
        offset == -1 -> getString(R.string.planner_rel_last_week)
        offset > 1 -> resources.getQuantityString(R.plurals.planner_rel_future, offset, offset)
        else -> resources.getQuantityString(R.plurals.planner_rel_past, -offset, -offset)
    }
}
