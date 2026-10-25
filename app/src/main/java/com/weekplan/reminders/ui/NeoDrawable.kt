package com.weekplan.reminders.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * The website's "neo-brutalist" box: flat fill, solid black border, and a hard offset shadow
 * (CSS: border: 3px solid #000; box-shadow: 4px 4px 0 #000).
 *
 * The shadow is drawn INSIDE the view bounds (bottom-right strip), so layouts need no extra margin.
 * While pressed the box slides onto its shadow, like `.btn:active` on the site.
 */
class NeoDrawable(
    initialFill: Int,
    private val strokeWidthPx: Float,
    private val shadowPx: Float,
    dashed: Boolean = false,
) : Drawable() {

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = initialFill
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = strokeWidthPx
        if (dashed) pathEffect = DashPathEffect(floatArrayOf(strokeWidthPx * 3f, strokeWidthPx * 2f), 0f)
    }

    private var pressed = false

    var fillColor: Int
        get() = fillPaint.color
        set(value) {
            if (fillPaint.color != value) {
                fillPaint.color = value
                invalidateSelf()
            }
        }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val nowPressed = state.contains(android.R.attr.state_pressed)
        if (nowPressed == pressed) return false
        pressed = nowPressed
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        // The face is the bounds minus the shadow strip; pressed, it moves into the strip.
        val shift = if (pressed) shadowPx else 0f
        val left = b.left + shift
        val top = b.top + shift
        val right = b.right - shadowPx + shift
        val bottom = b.bottom - shadowPx + shift

        if (!pressed && shadowPx > 0f) {
            canvas.drawRect(left + shadowPx, top + shadowPx, right + shadowPx, bottom + shadowPx, shadowPaint)
        }
        canvas.drawRect(left, top, right, bottom, fillPaint)
        if (strokeWidthPx > 0f) {
            val half = strokeWidthPx / 2f
            canvas.drawRect(left + half, top + half, right - half, bottom - half, strokePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        shadowPaint.alpha = alpha
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        shadowPaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
