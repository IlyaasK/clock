package com.ilyaas.einkclock.ui

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.alarm.Scheduler
import com.ilyaas.einkclock.data.Cities
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.util.Async
import com.ilyaas.einkclock.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Big HH:MM (no seconds — a per-second repaint is the e-ink defect we removed),
 * the date, the next alarm, and the selected world clocks as bordered cards.
 *
 * Repaints once per minute boundary, on NEXT_ALARM_CLOCK_CHANGED, and when the
 * city selection changes; nothing at all while the tab is hidden.
 */
class ClockTab(host: Activity) : TabView(host) {

    private val hourView: TextView = Ui.text(ctx, "", 120f, Ui.BLACK).apply { gravity = Gravity.CENTER }
    private val meridiemView: TextView = Ui.text(ctx, "", 32f, Ui.BLACK).apply {
        setPadding(Ui.dp(ctx, 8f), 0, 0, Ui.dp(ctx, 18f))
    }
    private val dateView: TextView = Ui.text(ctx, "", 20f, Ui.BLACK).apply { gravity = Gravity.CENTER }
    private val nextView: TextView = Ui.text(ctx, "", 18f, Ui.GRAY).apply { gravity = Gravity.CENTER }
    private val worldColumn = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, Ui.dp(ctx, 24f), 0, 0)
    }
    private var receiverRegistered = false

    private val tick = object : Runnable {
        override fun run() {
            renderTime()
            renderNextAlarm()
            renderWorld()
            scheduleTick()
        }
    }

    private val deferNextAlarm = Runnable {
        renderNextAlarm()
        if (!receiverRegistered) {
            runCatching {
                ctx.registerReceiver(
                    alarmChanged,
                    IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            }
            receiverRegistered = true
        }
    }

    private val alarmChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            renderNextAlarm()
        }
    }

    override val view: View = ScrollView(ctx).apply {
        isFillViewport = true
        addView(
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(Ui.dp(ctx, 16f), Ui.dp(ctx, 32f), Ui.dp(ctx, 16f), Ui.dp(ctx, 16f))
                addView(
                    Ui.horizontal(ctx).apply {
                        gravity = Gravity.BOTTOM
                        addView(hourView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        addView(meridiemView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    },
                )
                addView(Ui.space(ctx, 8))
                addView(dateView)
                addView(Ui.space(ctx, 16))
                addView(nextView)
                addView(worldColumn)
            },
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun onShown() {
        // Only the time and date are needed for the first frame; the next-alarm
        // line needs an AlarmManager round trip, so it lands a moment later.
        renderTime()
        renderWorld()
        scheduleTick()
        Async.mainDelayed(NEXT_ALARM_DELAY_MS, deferNextAlarm)
    }

    override fun onHidden() {
        Async.cancel(tick)
        Async.cancel(deferNextAlarm)
        if (receiverRegistered) {
            runCatching { ctx.unregisterReceiver(alarmChanged) }
            receiverRegistered = false
        }
    }

    private fun renderTime() {
        val now = ZonedDateTime.now()
        if (Fmt.is24Hour(ctx)) {
            hourView.text = now.format(H24)
            meridiemView.visibility = View.GONE
        } else {
            hourView.text = now.format(H12)
            meridiemView.text = now.format(MERIDIEM)
            meridiemView.visibility = View.VISIBLE
        }
        dateView.text = Fmt.dateLong(now.toLocalDate()).uppercase(Locale.US)
    }

    private fun renderNextAlarm() {
        val next = Scheduler.nextAlarmClock(ctx)
        nextView.text = if (next == null) {
            ctx.getString(R.string.no_alarm_set)
        } else {
            ctx.getString(
                R.string.next_alarm,
                Fmt.dayTime(ctx, Instant.ofEpochMilli(next.triggerTime), ZoneId.systemDefault()),
            )
        }
    }

    // ------------------------------------------------------------- world clocks

    private fun renderWorld() {
        worldColumn.removeAllViews()
        val local = ZoneId.systemDefault()
        for (zoneId in Prefs.of().worldClocks) {
            val zone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: continue
            val card = Ui.card(ctx)
            val row = Ui.horizontal(ctx)

            val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            left.addView(Ui.text(ctx, Cities.label(zoneId), 20f, Ui.BLACK))
            left.addView(Ui.text(ctx, relativeLabel(zone, local), 15f, Ui.BLACK))
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val timeRow = Ui.horizontal(ctx).apply { gravity = Gravity.BOTTOM }
            if (Fmt.is24Hour(ctx)) {
                timeRow.addView(Ui.text(ctx, Fmt.clockInZone(ctx, zone), 34f, Ui.BLACK))
            } else {
                val time = ZonedDateTime.now(zone)
                timeRow.addView(Ui.text(ctx, time.format(H12), 34f, Ui.BLACK))
                timeRow.addView(
                    Ui.text(ctx, time.format(MERIDIEM), 15f, Ui.BLACK).apply {
                        setPadding(Ui.dp(ctx, 4f), 0, 0, Ui.dp(ctx, 4f))
                    },
                )
            }
            row.addView(timeRow)
            card.addView(row)

            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { showCityPicker() }
            card.setOnLongClickListener {
                confirmRemove(zoneId)
                true
            }
            worldColumn.addView(card, Ui.cardParams(ctx))
        }
        worldColumn.addView(
            Ui.text(ctx, "+ " + ctx.getString(R.string.add_city), 18f, Ui.GRAY).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(ctx, 14f), 0, Ui.dp(ctx, 14f))
                isClickable = true
                isFocusable = true
                setOnClickListener { showCityPicker() }
            },
        )
    }

    /** "6 hours ahead", "9 hr 30 min ahead", "Tomorrow, 17 hours ahead". */
    private fun relativeLabel(zone: ZoneId, local: ZoneId): String {
        val now = Instant.now()
        val diffMinutes = (zone.rules.getOffset(now).totalSeconds - local.rules.getOffset(now).totalSeconds) / 60
        val phrase = if (diffMinutes == 0) {
            ctx.getString(R.string.rel_same)
        } else {
            val hours = abs(diffMinutes) / 60
            val minutes = abs(diffMinutes) % 60
            val parts = ArrayList<String>(2)
            if (hours > 0) {
                parts.add(
                    if (hours == 1) ctx.getString(R.string.unit_hour) else ctx.getString(R.string.unit_hours, hours),
                )
            }
            if (minutes > 0) parts.add(ctx.getString(R.string.unit_min, minutes))
            val amount = parts.joinToString(" ")
            if (diffMinutes > 0) ctx.getString(R.string.rel_ahead, amount) else ctx.getString(R.string.rel_behind, amount)
        }
        val zoneDate = LocalDate.now(zone)
        val localDate = LocalDate.now(local)
        return when {
            zoneDate.isAfter(localDate) -> ctx.getString(R.string.rel_tomorrow, phrase)
            zoneDate.isBefore(localDate) -> ctx.getString(R.string.rel_yesterday, phrase)
            else -> phrase
        }
    }

    private fun confirmRemove(zoneId: String) {
        AlertDialog.Builder(ctx)
            .setMessage(ctx.getString(R.string.remove_city, Cities.label(zoneId)))
            .setPositiveButton(R.string.remove) { _, _ ->
                Prefs.of().worldClocks = Prefs.of().worldClocks.filter { it != zoneId }
                renderWorld()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCityPicker() {
        val cities = Cities.ALL
        val labels = cities.map { it.label }.toTypedArray()
        val checked = BooleanArray(cities.size) { cities[it].zoneId in Prefs.of().worldClocks }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.world_clocks)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.done) { _, _ ->
                // Keep catalogue order so the list stays stable between edits.
                Prefs.of().worldClocks = cities.filterIndexed { index, _ -> checked[index] }.map { it.zoneId }
                renderWorld()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Align to the next minute boundary: at most one repaint per minute. */
    private fun scheduleTick() {
        Async.cancel(tick)
        val now = System.currentTimeMillis()
        Async.mainDelayed(60_000L - (now % 60_000L) + 25L, tick)
    }

    companion object {
        private const val NEXT_ALARM_DELAY_MS = 120L
        private val H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        private val H12 = DateTimeFormatter.ofPattern("h:mm", Locale.US)
        private val MERIDIEM = DateTimeFormatter.ofPattern("a", Locale.US)
    }
}
