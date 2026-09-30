package com.ilyaas.einkclock.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.data.Days
import java.time.DayOfWeek

/**
 * E-ink view toolbox. Everything is drawn with plain framework drawables:
 * no CardView, no ripple, no elevation, no state animators, no XML layouts.
 */
object Ui {

    const val BLACK = Color.BLACK
    const val WHITE = Color.WHITE
    const val GRAY = 0xFF808080.toInt()

    fun dp(ctx: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        ctx.resources.displayMetrics,
    ).toInt()

    fun text(
        ctx: Context,
        value: CharSequence,
        sp: Float,
        color: Int = BLACK,
        bold: Boolean = false,
    ): TextView = TextView(ctx).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    /** 8 dp radius, 1.5 dp pure-black stroke, white fill — the stock card look. */
    fun stroke(ctx: Context, fill: Int, strokeColor: Int, strokeDp: Float, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(dp(ctx, strokeDp), strokeColor)
            cornerRadius = dp(ctx, radiusDp).toFloat()
        }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = stroke(ctx, WHITE, BLACK, 1.5f, 8f)
        setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
    }

    fun cardParams(ctx: Context, gapDp: Int = 8): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(ctx, gapDp.toFloat()) }

    /**
     * A "button" is a TextView with a drawn background: the framework Button
     * carries a ripple + state animator, which is exactly what e-ink must not do.
     */
    fun button(
        ctx: Context,
        label: CharSequence,
        filled: Boolean,
        sp: Float = 20f,
        heightDp: Int = 64,
        weight: Float = 0f,
    ): TextView = TextView(ctx).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(if (filled) WHITE else BLACK)
        setTypeface(typeface, Typeface.BOLD)
        background = if (filled) {
            stroke(ctx, BLACK, BLACK, 0f, 4f)
        } else {
            stroke(ctx, WHITE, BLACK, 1.5f, 4f)
        }
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(
            if (weight > 0f) 0 else ViewGroup.LayoutParams.MATCH_PARENT,
            dp(ctx, heightDp.toFloat()),
            if (weight > 0f) weight else 0f,
        )
    }

    /** Small outline pill used for repeat/snooze choices. */
    fun chip(ctx: Context, label: CharSequence, selected: Boolean, heightDp: Int = 44): TextView = TextView(ctx).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(if (selected) WHITE else BLACK)
        background = if (selected) stroke(ctx, BLACK, BLACK, 0f, 4f) else stroke(ctx, WHITE, BLACK, 1.5f, 4f)
        isClickable = true
        isFocusable = true
        val lp = LinearLayout.LayoutParams(dp(ctx, 44f), dp(ctx, heightDp.toFloat()))
        lp.rightMargin = dp(ctx, 6f)
        layoutParams = lp
    }

    fun rowTopBar(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 16f), dp(ctx, 8f), dp(ctx, 8f), dp(ctx, 8f))
    }

    /** 48 dp text button for the top bar (+ / gear). */
    fun iconButton(ctx: Context, label: CharSequence, description: CharSequence, sp: Float = 26f): TextView =
        TextView(ctx).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(BLACK)
            background = ColorDrawable(WHITE)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 48f), dp(ctx, 48f))
        }

    fun vertical(ctx: Context, gapDp: Int = 0): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        if (gapDp > 0) setPadding(0, dp(ctx, gapDp.toFloat()), 0, 0)
    }

    fun horizontal(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun space(ctx: Context, heightDp: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, heightDp.toFloat()))
    }

    fun gap(ctx: Context, widthDp: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(ctx, widthDp.toFloat()), 1)
    }

    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(0x33000000)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1f))
    }

    fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    fun wrapWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    /** Black thumb when on, 50 % gray when off. */
    fun switchThumb(): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(BLACK, GRAY),
    )

    fun switchTrack(): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(GRAY, WHITE),
    )

    fun tabBackground(ctx: Context, selected: Boolean): Drawable {
        if (!selected) return ColorDrawable(WHITE)
        val layers = LayerDrawable(arrayOf(ColorDrawable(WHITE), ColorDrawable(BLACK)))
        layers.setLayerInset(1, 0, 0, 0, dp(ctx, 54f))
        return layers
    }

    // -------------------------------------------------------------- summaries

    fun dayNameRes(day: DayOfWeek): Int = when (day) {
        DayOfWeek.MONDAY -> R.string.day_mon
        DayOfWeek.TUESDAY -> R.string.day_tue
        DayOfWeek.WEDNESDAY -> R.string.day_wed
        DayOfWeek.THURSDAY -> R.string.day_thu
        DayOfWeek.FRIDAY -> R.string.day_fri
        DayOfWeek.SATURDAY -> R.string.day_sat
        DayOfWeek.SUNDAY -> R.string.day_sun
    }

    /** "Every day" / "Weekdays" / "Weekends" / "Once" / "Mon, Tue, Wed" */
    fun repeatSummary(ctx: Context, mask: Int): String = when (mask) {
        Days.ALL -> ctx.getString(R.string.every_day)
        Days.WEEKDAYS -> ctx.getString(R.string.weekdays)
        Days.WEEKEND -> ctx.getString(R.string.weekends)
        0 -> ctx.getString(R.string.once)
        else -> DayOfWeek.entries
            .filter { (mask and Days.bit(it)) != 0 }
            .joinToString(", ") { ctx.getString(dayNameRes(it)) }
    }
}
