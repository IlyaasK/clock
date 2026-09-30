package com.ilyaas.einkclock.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.alarm.Scheduler
import com.ilyaas.einkclock.data.Alarm
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.util.Async
import com.ilyaas.einkclock.util.Fmt
import java.time.LocalDate
import java.time.LocalTime

/** The alarm list: one card per alarm, thumb-sized switch, tap to edit. */
class AlarmTab(host: Activity) : TabView(host) {

    private class Snapshot(val alarms: List<Alarm>, val skips: Map<Int, MutableSet<String>>)

    private val list = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(ctx, 16f), 0, Ui.dp(ctx, 16f), Ui.dp(ctx, 16f))
    }
    private val emptyView: TextView = Ui.text(ctx, ctx.getString(R.string.no_alarms), 20f, Ui.GRAY).apply {
        gravity = Gravity.CENTER
        setPadding(0, Ui.dp(ctx, 48f), 0, 0)
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
            Ui.text(ctx, ctx.getString(R.string.alarm_title), 26f, Ui.BLACK, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(icon(R.drawable.ic_plus, R.string.new_alarm) { newAlarm() })
        addView(icon(R.drawable.ic_gear, R.string.settings) { openSettings() })
    }

    private fun icon(res: Int, descriptionRes: Int, onClick: () -> Unit): View = ImageView(ctx).apply {
        setImageResource(res)
        contentDescription = ctx.getString(descriptionRes)
        setPadding(Ui.dp(ctx, 12f), Ui.dp(ctx, 12f), Ui.dp(ctx, 12f), Ui.dp(ctx, 12f))
        background = ColorDrawable(Ui.WHITE)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 48f), Ui.dp(ctx, 48f))
    }

    override fun onShown() {
        refresh()
    }

    fun refresh() {
        Async.ioAsync(
            work = { Snapshot(Repo.alarms(), Repo.skipsByAlarm()) },
            then = { snap -> render(snap) },
        )
    }

    private fun render(snap: Snapshot) {
        list.removeAllViews()
        if (snap.alarms.isEmpty()) {
            list.addView(emptyView)
            return
        }
        val today = LocalDate.now().toString()
        for (alarm in snap.alarms) {
            list.addView(buildCard(alarm, snap.skips[alarm.id]?.contains(today) == true), Ui.cardParams(ctx))
        }
    }

    private fun buildCard(alarm: Alarm, skippedToday: Boolean): View {
        val card = Ui.card(ctx)
        val row = Ui.horizontal(ctx)

        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val tone = if (alarm.enabled) Ui.BLACK else Ui.GRAY
        left.addView(Ui.text(ctx, Fmt.alarmTime(ctx, alarm.hour, alarm.minute), 56f, tone))
        val summary = ArrayList<String>(3)
        summary.add(alarm.label.ifBlank { ctx.getString(R.string.alarm_label_default) })
        summary.add(Ui.repeatSummary(ctx, alarm.repeatMask))
        if (skippedToday) summary.add(ctx.getString(R.string.skipped_today))
        left.addView(Ui.text(ctx, summary.joinToString("  "), 15f, tone))
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(
            Switch(ctx).apply {
                isChecked = alarm.enabled
                thumbTintList = Ui.switchThumb()
                trackTintList = Ui.switchTrack()
                background = ColorDrawable(Ui.WHITE)
                minimumWidth = Ui.dp(ctx, 48f)
                minimumHeight = Ui.dp(ctx, 48f)
                contentDescription = summary.joinToString(" ")
                setOnCheckedChangeListener { _, checked ->
                    Async.io {
                        Repo.setEnabled(alarm.id, checked)
                        val fresh = Repo.alarm(alarm.id)
                        if (fresh != null && checked) {
                            Scheduler.scheduleAlarm(ctx, fresh)
                        } else {
                            Scheduler.cancelAlarm(ctx, alarm.id)
                        }
                        Async.main { refresh() }
                    }
                }
            },
        )

        card.addView(row)
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { openEdit(alarm.id) }
        card.setOnLongClickListener {
            showRowMenu(alarm)
            true
        }
        return card
    }

    /**
     * "+" asks for the time first and then creates the alarm, so the user never
     * faces an empty detail screen with a Save button to hunt for.
     */
    private fun newAlarm() {
        val now = LocalTime.now()
        TimePickerDialog(
            ctx,
            { _, h, m ->
                Async.io {
                    Repo.insertAlarm(
                        Alarm(
                            hour = h,
                            minute = m,
                            label = "",
                            repeatMask = 0,
                            enabled = true,
                            snoozeMinutes = Prefs.of().defaultSnoozeMinutes,
                        ),
                    )
                    Scheduler.rescheduleAll(ctx)
                    Async.main { refresh() }
                }
            },
            now.hour,
            now.minute,
            Fmt.is24Hour(ctx),
        ).show()
    }

    private fun showRowMenu(alarm: Alarm) {
        AlertDialog.Builder(ctx)
            .setItems(arrayOf(ctx.getString(R.string.edit), ctx.getString(R.string.delete))) { _, which ->
                if (which == 0) {
                    openEdit(alarm.id)
                } else {
                    Async.io {
                        Scheduler.cancelAlarm(ctx, alarm.id)
                        Repo.deleteAlarm(alarm.id)
                        Async.main { refresh() }
                    }
                }
            }
            .show()
    }

    private fun openEdit(id: Int) {
        ctx.startActivity(
            Intent(ctx, AlarmEditActivity::class.java)
                .putExtra(AlarmEditActivity.EXTRA_ALARM_ID, id),
        )
        host.overridePendingTransition(0, 0)
    }

    private fun openSettings() {
        ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
        host.overridePendingTransition(0, 0)
    }
}
