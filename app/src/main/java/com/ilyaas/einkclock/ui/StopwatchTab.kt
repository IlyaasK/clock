package com.ilyaas.einkclock.ui

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.util.Async
import com.ilyaas.einkclock.util.Fmt

/**
 * MM:SS.hh, monospace so the digits do not jitter. Repaints at most 10x/s while
 * running and not at all while stopped.
 */
class StopwatchTab(host: Activity) : TabView(host) {

    private val prefs get() = Prefs.of()

    private val timeView: TextView = Ui.text(ctx, "00:00.00", 72f, Ui.BLACK).apply {
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
    }
    private val startStop: TextView = Ui.button(ctx, ctx.getString(R.string.start), filled = true, sp = 22f, heightDp = 64)
    private val lapBtn: TextView = Ui.button(ctx, ctx.getString(R.string.lap), filled = false, sp = 20f, heightDp = 64, weight = 1f)
    private val resetBtn: TextView = Ui.button(ctx, ctx.getString(R.string.reset), filled = false, sp = 20f, heightDp = 64, weight = 1f)
    private val lapList = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    private val ticker = object : Runnable {
        override fun run() {
            renderTime()
            if (prefs.stopwatchRunning) Async.mainDelayed(100L, this) else renderButtons()
        }
    }

    override val view: View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(ctx, 16f), Ui.dp(ctx, 40f), Ui.dp(ctx, 16f), Ui.dp(ctx, 16f))
        addView(timeView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(Ui.space(ctx, 24))
        addView(startStop)
        addView(Ui.space(ctx, 8))
        addView(
            Ui.horizontal(ctx).apply {
                addView(lapBtn)
                addView(Ui.gap(ctx, 8))
                addView(resetBtn)
            },
        )
        addView(Ui.space(ctx, 16))
        addView(
            ScrollView(ctx).apply {
                isFillViewport = true
                        addView(lapList, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
    }

    init {
        startStop.setOnClickListener { if (prefs.stopwatchRunning) stop() else start() }
        lapBtn.setOnClickListener { lap() }
        resetBtn.setOnClickListener { if (!prefs.stopwatchRunning) reset() }
    }

    override fun onShown() {
        renderTime()
        renderLaps()
        renderButtons()
        Async.cancel(ticker)
        if (prefs.stopwatchRunning) Async.mainDelayed(100L, ticker)
    }

    override fun onHidden() {
        Async.cancel(ticker)
    }

    private fun elapsed(): Long {
        val running = prefs.stopwatchRunning
        val base = prefs.stopwatchAccumulated
        return if (running) base + (System.currentTimeMillis() - prefs.stopwatchStartedAt) else base
    }

    private fun start() {
        prefs.stopwatchStartedAt = System.currentTimeMillis()
        prefs.stopwatchRunning = true
        renderButtons()
        Async.cancel(ticker)
        Async.mainDelayed(100L, ticker)
    }

    private fun stop() {
        val now = System.currentTimeMillis()
        prefs.stopwatchAccumulated = prefs.stopwatchAccumulated + (now - prefs.stopwatchStartedAt)
        prefs.stopwatchRunning = false
        prefs.stopwatchStartedAt = 0L
        Async.cancel(ticker)
        renderTime()
        renderButtons()
    }

    private fun lap() {
        val splits = laps().toMutableList()
        val total = elapsed()
        splits.add(total - splits.sum())
        prefs.stopwatchLaps = splits.joinToString(",")
        renderLaps()
    }

    private fun reset() {
        prefs.stopwatchRunning = false
        prefs.stopwatchStartedAt = 0L
        prefs.stopwatchAccumulated = 0L
        prefs.stopwatchLaps = ""
        renderTime()
        renderLaps()
        renderButtons()
    }

    private fun laps(): List<Long> =
        prefs.stopwatchLaps.split(',').mapNotNull { it.trim().toLongOrNull() }

    private fun renderTime() {
        timeView.text = Fmt.stopwatch(elapsed())
    }

    private fun renderButtons() {
        val running = prefs.stopwatchRunning
        startStop.text = ctx.getString(if (running) R.string.stop else R.string.start)
        lapBtn.setTextColor(Ui.BLACK)
        lapBtn.isEnabled = true
        val resetTone = if (running) Ui.GRAY else Ui.BLACK
        resetBtn.setTextColor(resetTone)
        resetBtn.isEnabled = !running
    }

    private fun renderLaps() {
        lapList.removeAllViews()
        val splits = laps()
        var total = 0L
        val rows = ArrayList<String>(splits.size)
        splits.forEachIndexed { index, split ->
            total += split
            rows.add(ctx.getString(R.string.lap_format, index + 1, Fmt.stopwatch(split), Fmt.stopwatch(total)))
        }
        // Newest first.
        for (i in rows.indices.reversed()) {
            lapList.addView(
                Ui.text(ctx, rows[i], 18f, Ui.BLACK).apply {
                    setPadding(0, Ui.dp(ctx, 8f), 0, Ui.dp(ctx, 8f))
                },
            )
        }
    }
}
