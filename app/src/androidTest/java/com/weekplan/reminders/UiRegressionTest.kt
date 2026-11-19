package com.weekplan.reminders

import android.graphics.Rect
import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import java.io.File
import android.view.View
import android.widget.DatePicker
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.weekplan.reminders.data.AppGraph
import com.weekplan.reminders.data.model.*
import com.weekplan.reminders.data.repository.EventRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class UiRegressionTest {
    private lateinit var previous: EventRepository
    private lateinit var repository: TestRepository
    private var scenario: ActivityScenario<MainActivity>? = null
    private val repositoryField = AppGraph::class.java.getDeclaredField("repository").apply { isAccessible = true }

    @Before fun setup() {
        previous = AppGraph.eventRepository
        repository = TestRepository()
        repositoryField.set(null, repository)
    }
    @After fun cleanup() {
        scenario?.close()
        repositoryField.set(null, previous)
    }
    private fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "ui-regression/$name.png")
        file.parentFile!!.mkdirs()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(600, 4000)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun idle() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    @Test fun plannerRetainsWeekAndScrollAndBackRestoresNavigation() {
        val activity = launch()
        onView(withId(R.id.nav_tab_planner)).perform(click())
        onView(withId(R.id.week_next)).perform(click(), click())
        var range = ""
        var first = -1
        activity.onActivity {
            range = it.findViewById<TextView>(R.id.week_range).text.toString()
            val list = it.findViewById<RecyclerView>(R.id.planner_list)
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(6, 0)
        }
        idle()
        activity.onActivity { first = (it.findViewById<RecyclerView>(R.id.planner_list).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() }
        activity.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        idle()
        onView(withId(R.id.week_range)).check(matches(withText(range)))
        capture("planner-landscape")
        activity.onActivity {
            assertTrue("Landscape agenda must have usable height", it.findViewById<View>(R.id.planner_list).height > 80 * it.resources.displayMetrics.density)
            assertEquals(first, (it.findViewById<RecyclerView>(R.id.planner_list).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
            it.openAddEvent(LocalDate.now())
        }
        onView(withId(R.id.event_title)).check(matches(isDisplayed()))
        pressBack()
        onView(withId(R.id.bottom_nav_bar)).check(matches(isDisplayed()))
        onView(withId(R.id.week_range)).check(matches(withText(range)))
    }

    @Test fun rotatingBeforeEditLoadDoesNotReplaceEventWithDefaults() {
        val gate = CompletableDeferred<Unit>()
        repository.eventGate = gate
        val activity = launch()
        activity.onActivity {
            it.supportFragmentManager.beginTransaction()
                .replace(R.id.planner_container, EventFormFragment.newInstance(1L)).addToBackStack("edit_event").commit()
        }
        idle()
        activity.recreate()
        onView(withId(R.id.event_save)).check(matches(not(isEnabled())))
        gate.complete(Unit)
        idle()
        onView(withId(R.id.event_title)).check(matches(withText("Original event")))
        onView(withId(R.id.event_title)).perform(replaceText("Edited title"))
        activity.recreate()
        onView(withId(R.id.event_title)).check(matches(withText("Edited title")))
        onView(withId(R.id.event_save)).perform(scrollTo(), click())
        idle()
        assertEquals("Edited title", repository.event.title)
        assertEquals(LocalTime.of(14, 30), repository.event.startTime)
    }

    @Test fun settingsReadAndWriteFailuresAreRecoverable() {
        repository.failLoad = true
        launch()
        onView(withId(R.id.nav_tab_settings)).perform(click())
        onView(withId(R.id.settings_storage_error)).check(matches(isDisplayed()))
        repository.failLoad = false
        onView(withId(R.id.settings_storage_retry)).perform(scrollTo(), click())
        onView(withId(R.id.settings_offset_15)).check(matches(isChecked()))
        repository.failSave = true
        onView(withId(R.id.settings_offset_30)).perform(scrollTo(), click())
        onView(withId(R.id.settings_storage_error)).check(matches(isDisplayed()))
        onView(withId(R.id.settings_offset_15)).check(matches(isChecked()))
        repository.failSave = false
        onView(withId(R.id.settings_offset_30)).perform(scrollTo(), click())
        assertEquals(30, repository.settings.defaultReminderOffsetMinutes)
    }

    @Test fun dashboardHidesOldRowsWhenRefreshFails() {
        launch()
        onView(withId(R.id.dashboard_events_list)).check(matches(isDisplayed()))
        repository.failOccurrences = true
        runBlocking { repository.changes.emit(Unit) }
        idle()
        onView(withId(R.id.dashboard_error_card)).check(matches(isDisplayed()))
        onView(withId(R.id.dashboard_events_list)).check(matches(not(isDisplayed())))
    }

    @Test fun datePopupRetainsUnconfirmedSelectionAcrossRecreation() {
        val activity = launch()
        activity.onActivity { it.openAddEvent(LocalDate.now()) }
        onView(withId(R.id.event_date)).perform(scrollTo(), click())
        activity.onActivity {
            val form = it.supportFragmentManager.findFragmentById(R.id.planner_container)!!
            val dialog = form.childFragmentManager.findFragmentByTag("picker") as androidx.fragment.app.DialogFragment
            dialog.requireDialog().findViewById<DatePicker>(R.id.dialog_date).updateDate(2027, 2, 19)
        }
        activity.recreate()
        capture("date-picker")
        onView(withId(R.id.dialog_confirm)).inRoot(isDialog()).perform(click())
        activity.onActivity {
            val text = it.findViewById<TextView>(R.id.event_date).text.toString()
            assertTrue(text.contains("2027"))
            assertTrue(text.contains("19"))
        }
        pressBack()
        onView(withId(R.id.bottom_nav_bar)).check(matches(isDisplayed()))
    }

    @Test fun notesCursorStaysAboveKeyboard() {
        val activity = launch()
        activity.onActivity { it.openAddEvent(LocalDate.now()) }
        onView(withId(R.id.event_notes)).perform(scrollTo(), click(), typeText("Notes remain visible while typing"))
        idle()
        capture("notes-keyboard")
        activity.onActivity {
            val root = it.findViewById<View>(R.id.main)
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(root))
            assertTrue(insets.isVisible(WindowInsetsCompat.Type.ime()))
            val notes = it.findViewById<EditText>(R.id.event_notes)
            val location = IntArray(2)
            notes.getLocationOnScreen(location)
            val visible = Rect()
            assertTrue(notes.getGlobalVisibleRect(visible))
            val keyboardTop = root.rootView.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val cursorLine = notes.layout.getLineForOffset(notes.selectionEnd)
            val cursorBottom = location[1] + notes.totalPaddingTop + notes.layout.getLineBottom(cursorLine) - notes.scrollY
            assertTrue("Notes cursor must be above keyboard", cursorBottom <= keyboardTop)
        }
        pressBack() // dismiss keyboard first
        pressBack() // return to dashboard
        onView(withId(R.id.bottom_nav_bar)).check(matches(isDisplayed()))
    }

    @Test fun timeAndReminderPopupsCommitOnlyOnDone() {
        val activity = launch()
        activity.onActivity { it.openAddEvent(LocalDate.now()) }
        onView(withId(R.id.event_start_time)).perform(scrollTo(), click())
        activity.onActivity {
            val form = it.supportFragmentManager.findFragmentById(R.id.planner_container)!!
            val dialog = form.childFragmentManager.findFragmentByTag("picker") as DialogFragment
            dialog.requireDialog().findViewById<TimePicker>(R.id.dialog_time).apply { hour = 16; minute = 45 }
        }
        activity.recreate()
        capture("time-picker")
        onView(withId(R.id.dialog_confirm)).inRoot(isDialog()).perform(click())
        activity.onActivity {
            val time = LocalTime.of(16, 45).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()))
            assertEquals(time, it.findViewById<TextView>(R.id.event_start_time).text.toString())
        }
        onView(withId(R.id.event_reminder)).perform(scrollTo(), click())
        onView(withText(R.string.settings_offset_30)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.dialog_cancel)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.event_reminder)).check(matches(withText(R.string.settings_offset_15)))
        onView(withId(R.id.event_reminder)).perform(scrollTo(), click())
        onView(withText(R.string.settings_offset_30)).inRoot(isDialog()).perform(click())
        activity.recreate()
        onView(withId(R.id.dialog_confirm)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.event_reminder)).check(matches(withText(R.string.settings_offset_30)))
    }

    @Test fun customDeleteConfirmationSupportsCancelAndRecreation() {
        val activity = launch()
        activity.onActivity { it.openEventDetails(1, LocalDate.now()) }
        onView(withId(R.id.details_delete)).perform(scrollTo(), click())
        capture("delete-confirmation")
        onView(withId(R.id.dialog_cancel)).inRoot(isDialog()).perform(click())
        assertEquals(0, repository.deleteCount)
        onView(withId(R.id.details_delete)).perform(scrollTo(), click())
        activity.recreate()
        onView(withId(R.id.dialog_confirm)).inRoot(isDialog()).perform(click())
        assertEquals(1, repository.deleteCount)
        onView(withId(R.id.bottom_nav_bar)).check(matches(isDisplayed()))
    }

    @Test fun characterLimitsAndCountersSurviveRotation() {
        val activity = launch()
        activity.onActivity { it.openAddEvent(LocalDate.now()) }
        onView(withId(R.id.event_title)).perform(scrollTo(), replaceText("T".repeat(120)))
        onView(withId(R.id.event_title_count)).check(matches(withText("100 / 100")))
        onView(withId(R.id.event_notes)).perform(scrollTo(), replaceText("N".repeat(2100)))
        onView(withId(R.id.event_notes_count)).check(matches(withText("2000 / 2000")))
        activity.recreate()
        activity.onActivity {
            assertEquals(100, it.findViewById<EditText>(R.id.event_title).length())
            assertEquals(2000, it.findViewById<EditText>(R.id.event_notes).length())
            assertEquals("100 / 100", it.findViewById<TextView>(R.id.event_title_count).text.toString())
            assertEquals("2000 / 2000", it.findViewById<TextView>(R.id.event_notes_count).text.toString())
        }
    }

    @Test fun editingOlderOverLimitDataDoesNotSilentlyTruncateIt() {
        repository.event = repository.event.copy(title = "T".repeat(120), notes = "N".repeat(2100))
        val activity = launch()
        activity.onActivity {
            it.supportFragmentManager.beginTransaction().replace(R.id.planner_container,
                EventFormFragment.newInstance(1L)).addToBackStack("edit_event").commit()
        }
        idle()
        activity.recreate()
        activity.onActivity {
            assertEquals(120, it.findViewById<EditText>(R.id.event_title).length())
            assertEquals(2100, it.findViewById<EditText>(R.id.event_notes).length())
        }
        onView(withId(R.id.event_save)).perform(scrollTo(), click())
        onView(withId(R.id.form_error)).check(matches(isDisplayed()))
        assertEquals(120, repository.event.title.length)
        assertEquals(2100, repository.event.notes.length)
    }

    private class TestRepository : EventRepository {
        override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var event = Event(id = 1, title = "Original event", startDate = LocalDate.now(),
            startTime = LocalTime.of(14, 30), endTime = LocalTime.of(16, 0), notes = "Original notes")
        var settings = Settings()
        var eventGate: CompletableDeferred<Unit>? = null
        var failLoad = false
        var failSave = false
        var failOccurrences = false
        var deleteCount = 0
        override suspend fun getEvent(eventId: Long): Event { eventGate?.await(); return event }
        override suspend fun getOccurrences(from: LocalDate, to: LocalDate): List<OccurrenceWithEvent> {
            if (failOccurrences) throw IOException("Test storage failure")
            return if (event.startDate in from..to) listOf(OccurrenceWithEvent(event,
                EventOccurrence(event.id, event.startDate, event.startDate.atTime(event.startTime), event.startDate.atTime(event.endTime)))) else emptyList()
        }
        override suspend fun createEvent(event: Event): Long { this.event = event.copy(id = 1); return 1 }
        override suspend fun updateEvent(event: Event) { this.event = event }
        override suspend fun deleteEvent(eventId: Long) { deleteCount++ }
        override suspend fun getSettings(): Settings { if (failLoad) throw IOException("Test read failure"); return settings }
        override suspend fun saveSettings(settings: Settings) { if (failSave) throw IOException("Test write failure"); this.settings = settings }
    }
}
