package com.weekplan.reminders.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.weekplan.reminders.R
import kotlin.math.abs

/**
 * Events have no color field in the shared contract, so the planner gives each event one of the
 * website's palette colors based on its id. The same event always gets the same color
 * (across days, weeks and app restarts); nothing is stored.
 */
object EventColors {
    private val palette = intArrayOf(
        R.color.wp_yellow,
        R.color.wp_green,
        R.color.wp_blue,
        R.color.wp_pink,
        R.color.wp_orange,
        R.color.wp_purple,
    )

    fun colorFor(context: Context, eventId: Long): Int {
        val index = abs(eventId.hashCode() % palette.size)
        return ContextCompat.getColor(context, palette[index])
    }
}
