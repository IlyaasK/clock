package com.ilyaas.einkclock.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.alarm.AlarmService
import com.ilyaas.einkclock.alarm.Timers
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.data.TimerRow
import com.ilyaas.einkclock.data.TimerState
import com.ilyaas.einkclock.util.Async
import com.ilyaas.einkclock.util.Fmt

/** N simultaneous timers, one row each, no spinners. Ticks at 1 Hz while visible. */
class TimerTab(host: Activity) : TabView(host) {

    private class Snap(val timers: List<TimerRow>, val now: Long)

    private val list = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(ctx, 16f), 0, Ui.dp(ctx, 16f), Ui.dp(ctx, 16f))
    }
    private val emptyView: TextView = Ui.text(ctx, ctx.getString(R.string.no_timers), 20f, Ui.GRAY).apply {
        gravity = Gravity.CENTER
        setPadding(0, Ui.dp(ctx, 48f), 0, 0)
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            Async.mainDelayed(1_000L, this)
        }
    }

    override val view: View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(buildTopBar())
        addView(
            ScrollView(ctx).apply {
                isFillViewport = true
                        addView(list, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
    }

    private fun buildTopBar(): View = Ui.rowTopBar(ctx).apply {
        addView(
            Ui.text(ctx, ctx.getString(R.string.tab_timer), 26f, Ui.BLACK, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            ImageView(ctx).apply {
                setImageResource(R.drawable.ic_plus)
                contentDescription = ctx.getString(R.string.add_timer)
                setPadding(Ui.dp(ctx, 12f), Ui.dp(ctx, 12f), Ui.dp(ctx, 12f), Ui.dp(ctx, 12f))
                background = ColorDrawable(Ui.WHITE)
                isClickable = true
                isFocusable = true
                setOnClickListener { showAddDialog() }
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 48f), Ui.dp(ctx, 48f))
            },
        )
    }

    override fun onShown() {
        refresh()
        Async.cancel(tick)
        Async.mainDelayed(1_000L, tick)
    }

    override fun onHidden() {
        Async.cancel(tick)
    }

    fun refresh() {
        Async.ioAsync(
            work = {
                val now = System.currentTimeMillis()
                Snap(Timers.visible(Repo.timers(), now), now)
            },
            then = { snap -> render(snap) },
        )
    }

    private fun render(snap: Snap) {
        list.removeAllViews()
        if (snap.timers.isEmpty()) {
            list.addView(emptyView)
            return
        }
        if (anyTimerRinging()) {
            list.addView(
                Ui.text(ctx, ctx.getString(R.string.timers_up), 18f, Ui.GRAY).apply {
                    setPadding(0, 0, 0, Ui.dp(ctx, 8f))
                },
            )
        }
        for (timer in snap.timers) list.addView(buildCard(timer, snap.now), Ui.cardParams(ctx))
    }

    private fun anyTimerRinging(): Boolean =
        AlarmService.currentKey()?.startsWith(AlarmService.KIND_TIMER + ":") == true

    private fun buildCard(timer: TimerRow, now: Long): View {
        val card = Ui.card(ctx)
        val remaining = Timers.remaining(timer, now)
        card.addView(Ui.text(ctx, Fmt.hms(remaining), 48f, Ui.BLACK))
        if (timer.label.isNotBlank()) card.addView(Ui.text(ctx, timer.label, 15f, Ui.BLACK))
        card.addView(Ui.space(ctx, 10))
        card.addView(progress(if (timer.lengthMs > 0) remaining.toFloat() / timer.lengthMs else 0f))
        card.addView(Ui.space(ctx, 12))

        val row = Ui.horizontal(ctx)
        val first = if (timer.state == TimerState.RUNNING) {
            smallButton(ctx.getString(R.string.pause)) { Async.io { Timers.pause(ctx, timer.id); refresh() } }
        } else {
            smallButton(ctx.getString(R.string.resume)) { Async.io { Timers.resume(ctx, timer.id); refresh() } }
        }
        row.addView(first)
        row.addView(Ui.gap(ctx, 6))
        row.addView(smallButton(ctx.getString(R.string.add_minute)) { Async.io { Timers.addMinute(ctx, timer.id); refresh() } })
        row.addView(Ui.gap(ctx, 6))
        row.addView(smallButton(ctx.getString(R.string.delete)) { Async.io { Timers.delete(ctx, timer.id); refresh() } })
        card.addView(row)
        return card
    }

    private fun smallButton(label: String, onClick: () -> Unit): TextView =
        Ui.button(ctx, label, filled = false, sp = 15f, heightDp = 44, weight = 1f).apply {
            setOnClickListener { onClick() }
        }

    /** Plain 4 dp bar: black = remaining, light gray = elapsed. No animation. */
    private fun progress(fraction: Float): View {
        val clamped = fraction.coerceIn(0f, 1f)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0x22000000)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 4f))
            addView(
                View(ctx).apply { setBackgroundColor(Ui.BLACK) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, clamped.coerceAtLeast(0.0001f)),
            )
            addView(
                View(ctx).apply { setBackgroundColor(Ui.WHITE) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (1f - clamped).coerceAtLeast(0.0001f)),
            )
        }
    }

    private fun showAddDialog() {
        val presets = longArrayOf(60_000L, 300_000L, 600_000L, 900_000L, 1_800_000L, 3_600_000L)
        val presetLabels = intArrayOf(
            R.string.preset_1m, R.string.preset_5m, R.string.preset_10m,
            R.string.preset_15m, R.string.preset_30m, R.string.preset_1h,
        )
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 24f), Ui.dp(ctx, 8f), Ui.dp(ctx, 24f), Ui.dp(ctx, 8f))
        }

        val hours = numberPicker(0, 23)
        val minutes = numberPicker(0, 59)
        val seconds = numberPicker(0, 59)
        val pickers = Ui.horizontal(ctx).apply {
            gravity = Gravity.CENTER
            addView(labelledPicker(ctx.getString(R.string.hours), hours))
            addView(Ui.gap(ctx, 8))
            addView(labelledPicker(ctx.getString(R.string.minutes), minutes))
            addView(Ui.gap(ctx, 8))
            addView(labelledPicker(ctx.getString(R.string.seconds), seconds))
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.add_timer)
            .setView(container)
            .setPositiveButton(R.string.start, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        fun create(ms: Long) {
            if (ms <= 0L) return
            Async.io {
                Timers.create(ctx, ms, ctx.getString(R.string.timer_label_default))
                Async.main { refresh() }
            }
        }

        // preset chips: two rows of three
        for (rowIndex in 0 until 2) {
            val row = Ui.horizontal(ctx).apply { setPadding(0, Ui.dp(ctx, 8f), 0, 0) }
            for (col in 0 until 3) {
                val i = rowIndex * 3 + col
                row.addView(
                    Ui.chip(ctx, ctx.getString(presetLabels[i]), selected = false, heightDp = 48).apply {
                        layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 48f), 1f).apply {
                            rightMargin = Ui.dp(ctx, 6f)
                        }
                        setOnClickListener {
                            create(presets[i])
                            dialog.dismiss()
                        }
                    },
                )
            }
            container.addView(row)
        }
        container.addView(Ui.space(ctx, 12))
        container.addView(pickers)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ms = hours.value * 3_600_000L + minutes.value * 60_000L + seconds.value * 1_000L
                if (ms > 0L) {
                    create(ms)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun labelledPicker(label: String, picker: NumberPicker): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(Ui.text(ctx, label, 14f, Ui.GRAY), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(picker, LinearLayout.LayoutParams(Ui.dp(ctx, 72f), Ui.dp(ctx, 96f)))
        }

    private fun numberPicker(min: Int, max: Int): NumberPicker = NumberPicker(ctx).apply {
        this.minValue = min
        this.maxValue = max
        value = min
        wrapSelectorWheel = true
    }
}
