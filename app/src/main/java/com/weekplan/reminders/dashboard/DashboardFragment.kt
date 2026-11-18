package com.weekplan.reminders.dashboard

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.weekplan.reminders.AppNavigator
import com.weekplan.reminders.R
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.ui.EventColors
import com.weekplan.reminders.ui.NeoLinearLayout
import com.weekplan.reminders.ui.NeoTextView
import com.weekplan.reminders.ui.formatMeta
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModel.Factory(AppGraph.eventRepository)
    }

    private val navigator: AppNavigator? get() = activity as? AppNavigator

    private val locale: Locale = Locale.getDefault()
    private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", locale)
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dateText = view.findViewById<TextView>(R.id.dashboard_date)
        val countText = view.findViewById<TextView>(R.id.dashboard_count)
        val addActionBtn = view.findViewById<NeoTextView>(R.id.dashboard_action_add)
        val plannerActionBtn = view.findViewById<NeoTextView>(R.id.dashboard_action_planner)

        val nextUpCard = view.findViewById<NeoLinearLayout>(R.id.next_up_card)
        val nextUpBadge = view.findViewById<NeoTextView>(R.id.next_up_badge)
        val nextUpTitle = view.findViewById<TextView>(R.id.next_up_title)
        val nextUpTime = view.findViewById<TextView>(R.id.next_up_time)
        val nextUpMeta = view.findViewById<TextView>(R.id.next_up_meta)
        val allDoneBanner = view.findViewById<NeoTextView>(R.id.dashboard_all_done_banner)

        val emptyCard = view.findViewById<NeoLinearLayout>(R.id.dashboard_empty_card)
        val emptyAddBtn = view.findViewById<NeoTextView>(R.id.dashboard_empty_add_btn)
        val errorCard = view.findViewById<NeoLinearLayout>(R.id.dashboard_error_card)
        val retryBtn = view.findViewById<NeoTextView>(R.id.dashboard_retry_btn)
        val loadingBar = view.findViewById<ProgressBar>(R.id.dashboard_loading)

        val eventsList = view.findViewById<RecyclerView>(R.id.dashboard_events_list)
        eventsList.layoutManager = LinearLayoutManager(requireContext())
        eventsList.isNestedScrollingEnabled = false
        (eventsList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        val adapter = DashboardEventAdapter(
            onEventClick = { item ->
                navigator?.openEventDetails(item.occurrence.eventId, item.occurrence.occurrenceDate)
            }
        )
        eventsList.adapter = adapter

        // Quick actions
        addActionBtn.setOnClickListener {
            navigator?.openAddEvent(LocalDate.now())
        }
        emptyAddBtn.setOnClickListener {
            navigator?.openAddEvent(LocalDate.now())
        }
        plannerActionBtn.setOnClickListener {
            navigator?.openPlanner()
        }
        retryBtn.setOnClickListener {
            viewModel.refresh()
        }

        fun render(state: DashboardUiState) {
            dateText.text = state.today.format(dayHeaderFormat)

            when (state.status) {
                DashboardStatus.LOADING -> {
                    loadingBar.visibility = View.VISIBLE
                    emptyCard.visibility = View.GONE
                    errorCard.visibility = View.GONE
                    nextUpCard.visibility = View.GONE
                    allDoneBanner.visibility = View.GONE
                    countText.text = getString(R.string.planner_badge_today)
                }
                DashboardStatus.ERROR -> {
                    loadingBar.visibility = View.GONE
                    emptyCard.visibility = View.GONE
                    errorCard.visibility = View.VISIBLE
                    nextUpCard.visibility = View.GONE
                    allDoneBanner.visibility = View.GONE
                    countText.text = getString(R.string.dashboard_error_title)
                }
                DashboardStatus.CONTENT -> {
                    loadingBar.visibility = View.GONE
                    errorCard.visibility = View.GONE

                    val totalEvents = state.occurrences.size
                    if (totalEvents == 0) {
                        countText.text = getString(R.string.planner_no_events)
                        emptyCard.visibility = View.VISIBLE
                        nextUpCard.visibility = View.GONE
                        allDoneBanner.visibility = View.GONE
                        eventsList.visibility = View.GONE
                    } else {
                        countText.text = resources.getQuantityString(
                            R.plurals.planner_event_count,
                            totalEvents,
                            totalEvents
                        ) + " planned today"
                        emptyCard.visibility = View.GONE
                        eventsList.visibility = View.VISIBLE
                        adapter.submitOccurrences(state.occurrences)
                        adapter.updateFinishedKeys(state.finishedOccurrenceKeys)

                        // Next up or Happening now card
                        val next = state.nextUp
                        if (next != null) {
                            nextUpCard.visibility = View.VISIBLE
                            allDoneBanner.visibility = View.GONE

                            val isNow = state.isHappeningNow
                            nextUpBadge.text = if (isNow) {
                                getString(R.string.dashboard_happening_now)
                            } else {
                                getString(R.string.dashboard_next_up)
                            }
                            nextUpBadge.neoFill = if (isNow) {
                                requireContext().getColor(R.color.wp_green)
                            } else {
                                requireContext().getColor(R.color.wp_yellow)
                            }

                            nextUpTitle.text = next.event.title
                            val startStr = next.occurrence.startDateTime.format(timeFormat)
                            val endStr = next.occurrence.endDateTime.format(timeFormat)
                            nextUpTime.text = "$startStr → $endStr"

                            nextUpCard.neoFill = EventColors.colorFor(requireContext(), next.event.id)

                            val meta = next.event.formatMeta(requireContext(), locale)
                            nextUpMeta.text = meta
                            nextUpMeta.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE

                            nextUpCard.setOnClickListener {
                                navigator?.openEventDetails(next.occurrence.eventId, next.occurrence.occurrenceDate)
                            }
                        } else {
                            nextUpCard.visibility = View.GONE
                            allDoneBanner.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startScheduling()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopScheduling()
    }
}
