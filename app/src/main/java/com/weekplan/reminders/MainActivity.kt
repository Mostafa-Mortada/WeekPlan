package com.weekplan.reminders

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.planner.PlannerNavigator
import com.weekplan.reminders.planner.WeeklyPlannerFragment
import java.time.LocalDate

class MainActivity : AppCompatActivity(), PlannerNavigator {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The website is light-only; keep the planner's black-on-white look in dark mode too.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        AppGraph.initialize(applicationContext)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.planner_container, WeeklyPlannerFragment())
            }
        }
    }

    override fun openEventDetails(eventId: Long, occurrenceDate: LocalDate) {
        Toast.makeText(this, "TODO details: $eventId on $occurrenceDate", Toast.LENGTH_SHORT).show()
    }


    override fun openAddEvent(date: LocalDate) {
        supportFragmentManager.commit {
            replace(R.id.planner_container, EventFormFragment.newInstance(date))
            addToBackStack("event_form")
        }
    }
}
