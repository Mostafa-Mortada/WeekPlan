package com.weekplan.reminders.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.withTranslation
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.CompoundButtonCompat
import com.weekplan.reminders.R

/** Keep Android's checked semantics, keyboard input and RadioGroup behavior;
 * replace just the visual layer with the planner's black outlines and flat fills. */
class NeoCheckBox @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatCheckBox(context, attrs) {
    init {
        CompoundButtonCompat.setButtonTintList(this, null)
        buttonTintList = null
        buttonDrawable = SelectionDrawable(context, SelectionKind.CHECK)
        styleSelectionControl()
    }
}

class NeoRadioButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatRadioButton(context, attrs) {
    init {
        CompoundButtonCompat.setButtonTintList(this, null)
        buttonTintList = null
        buttonDrawable = SelectionDrawable(context, SelectionKind.RADIO)
        styleSelectionControl()
    }
}

class NeoSwitch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SwitchCompat(context, attrs) {
    init {
        thumbTintList = null
        trackTintList = null
        thumbDrawable = SelectionDrawable(context, SelectionKind.THUMB)
        trackDrawable = SelectionDrawable(context, SelectionKind.TRACK)
        showText = false
        splitTrack = false
        switchMinWidth = (56 * resources.displayMetrics.density).toInt()
        switchPadding = (8 * resources.displayMetrics.density).toInt()
        styleSelectionControl()
    }
}

private fun TextView.styleSelectionControl() {
    minHeight = (48 * resources.displayMetrics.density).toInt()
    minWidth = (48 * resources.displayMetrics.density).toInt()
    typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium)
    textSize = 12f
    setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(Color.DKGRAY, context.getColor(R.color.wp_ink))))
    setBackgroundResource(R.drawable.neo_choice_background)
    if (this !is SwitchCompat) {
        // The indicator reserves 4dp for its own label; each choice then ends
        // with 12dp of space so adjacent pairs do not visually run together.
        setPaddingRelative(paddingStart, paddingTop,
            maxOf(paddingEnd, (12 * resources.displayMetrics.density).toInt()), paddingBottom)
    }
}

private enum class SelectionKind { CHECK, RADIO, THUMB, TRACK }

private class SelectionDrawable(context: Context, private val kind: SelectionKind) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val ink = context.getColor(R.color.wp_ink)
    private val paper = context.getColor(R.color.wp_paper)
    private val yellow = context.getColor(R.color.wp_yellow)
    private val green = context.getColor(R.color.wp_green)
    private val muted = context.getColor(R.color.wp_skeleton)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var checked = false
    private var enabled = true
    private var opacity = 255

    override fun getIntrinsicWidth() = ((if (kind == SelectionKind.TRACK) 56 else 24) * density).toInt()
    override fun getIntrinsicHeight() = ((when (kind) { SelectionKind.TRACK -> 28; SelectionKind.THUMB -> 24; else -> 20 }) * density).toInt()
    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        val nextChecked = android.R.attr.state_checked in state
        val nextEnabled = android.R.attr.state_enabled in state
        if (nextChecked == checked && nextEnabled == enabled) return false
        checked = nextChecked
        enabled = nextEnabled
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
            canvas.scale(bounds.width().toFloat() / intrinsicWidth, bounds.height().toFloat() / intrinsicHeight)
            val indicatorScale = if (kind == SelectionKind.CHECK || kind == SelectionKind.RADIO) 20f / 24f else 1f
            canvas.scale(density * indicatorScale, density * indicatorScale)
            paint.alpha = if (enabled) opacity else opacity * 45 / 100
            val track = kind == SelectionKind.TRACK
            val width = if (track) 56f else 24f
            val height = if (track) 28f else 24f
            val fill = when (kind) {
                SelectionKind.CHECK -> if (checked) green else paper
                SelectionKind.RADIO -> if (checked) yellow else paper
                SelectionKind.TRACK -> if (checked) green else muted
                SelectionKind.THUMB -> paper
            }
            paint.style = Paint.Style.FILL
            paint.color = fill
            paint.alpha = if (enabled) opacity else opacity * 45 / 100
            if (kind == SelectionKind.RADIO) {
                canvas.drawCircle(12f, 12f, 10f, paint)
            } else {
                canvas.drawRect(1f, 1f, width - 1f, height - 1f, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = ink
            paint.alpha = if (enabled) opacity else opacity * 45 / 100
            if (kind == SelectionKind.RADIO) canvas.drawCircle(12f, 12f, 10f, paint)
            else canvas.drawRect(1f, 1f, width - 1f, height - 1f, paint)
            if (checked && kind == SelectionKind.CHECK) {
                paint.strokeWidth = 3f
                paint.strokeJoin = Paint.Join.MITER
                val tick = Path().apply { moveTo(5f, 12f); lineTo(10f, 17f); lineTo(19f, 7f) }
                canvas.drawPath(tick, paint)
            } else if (checked && kind == SelectionKind.RADIO) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 12f, 4.5f, paint)
            }
        }
    }
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
