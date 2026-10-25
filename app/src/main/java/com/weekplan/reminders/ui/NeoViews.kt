package com.weekplan.reminders.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.weekplan.reminders.R
import kotlin.math.roundToInt

/**
 * Views that paint themselves as a neo-brutalist box. Use them in XML:
 *
 *   <com.weekplan.reminders.ui.NeoTextView
 *       app:neoFill="@color/wp_yellow" app:neoStroke="3dp" app:neoShadow="4dp" ... />
 *
 * Attributes: neoFill (default white), neoStroke (default 3dp), neoShadow (default 4dp,
 * 0dp = flat), neoDashed (dashed border, used for empty states).
 * The shadow strip is added to the right/bottom padding, so content stays on the face.
 */
private fun View.initNeo(attrs: AttributeSet?): NeoDrawable {
    val density = resources.displayMetrics.density
    val a = context.obtainStyledAttributes(attrs, R.styleable.Neo)
    val fill = a.getColor(R.styleable.Neo_neoFill, Color.WHITE)
    val stroke = a.getDimension(R.styleable.Neo_neoStroke, 3f * density)
    val shadow = a.getDimension(R.styleable.Neo_neoShadow, 4f * density)
    val dashed = a.getBoolean(R.styleable.Neo_neoDashed, false)
    a.recycle()

    val drawable = NeoDrawable(fill, stroke, shadow, dashed)
    background = drawable
    val strip = shadow.roundToInt()
    setPadding(paddingLeft, paddingTop, paddingRight + strip, paddingBottom + strip)
    return drawable
}

class NeoTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private val neo = initNeo(attrs)

    var neoFill: Int
        get() = neo.fillColor
        set(value) {
            neo.fillColor = value
        }
}

class NeoLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val neo = initNeo(attrs)

    var neoFill: Int
        get() = neo.fillColor
        set(value) {
            neo.fillColor = value
        }
}

class NeoFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val neo = initNeo(attrs)

    var neoFill: Int
        get() = neo.fillColor
        set(value) {
            neo.fillColor = value
        }
}
