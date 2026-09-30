package com.ilyaas.einkclock.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.alarm.Scheduler
import com.ilyaas.einkclock.data.Alarm
import com.ilyaas.einkclock.data.Days
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.data.RINGTONE_SILENT
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.util.Async
import com.ilyaas.einkclock.util.Fmt
import java.time.DayOfWeek

/**
 * Detail screen for one existing alarm.
 *
 * There is no Save button on purpose: every change (time, label, days, snooze,
 * vibrate, ringtone) is written and rescheduled immediately, so the screen has
 * exactly one way out — Done — and one destructive action — Delete. The "+" in
 * the list creates the alarm after its time picker, so this screen never has to
 * ask "new or existing?".
 */
class AlarmEditActivity : Activity() {

    private var alarmId = -1
    private var hour = 8
    private var minute = 0
    private var mask = 0
    private var vibrate = true
    private var snoozeMinutes = 10
    private var ringtone: String? = null
    private var enabled = true

    private lateinit var titleView: TextView
    private lateinit var timeView: TextView
    private lateinit var labelEdit: EditText
    private lateinit var dayRow: LinearLayout
    private lateinit var quickRow: LinearLayout
    private lateinit var snoozeRow: LinearLayout
    private lateinit var ringtoneValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        snoozeMinutes = Prefs.of().defaultSnoozeMinutes
        if (alarmId < 0) {
            finish()
            return
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@AlarmEditActivity, 16f), Ui.dp(this@AlarmEditActivity, 8f), Ui.dp(this@AlarmEditActivity, 16f), Ui.dp(this@AlarmEditActivity, 24f))
        }

        titleView = Ui.text(this, getString(R.string.alarm_label_default), 24f, Ui.BLACK, bold = true)
        val done = Ui.button(this, getString(R.string.done), filled = false, sp = 16f, heightDp = 48).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@AlarmEditActivity, 96f), Ui.dp(this@AlarmEditActivity, 48f))
            setOnClickListener { finish() }
        }
        column.addView(
            Ui.horizontal(this).apply {
                addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(done)
            },
        )

        timeView = Ui.text(this, "", 72f, Ui.BLACK).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(0, Ui.dp(this@AlarmEditActivity, 16f), 0, Ui.dp(this@AlarmEditActivity, 16f))
            setOnClickListener { pickTime() }
        }
        column.addView(timeView)

        labelEdit = EditText(this).apply {
            hint = getString(R.string.label_hint)
            setTextColor(Ui.BLACK)
            setHintTextColor(Ui.GRAY)
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(0, Ui.dp(this@AlarmEditActivity, 16f), 0, Ui.dp(this@AlarmEditActivity, 16f))
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) persist() }
        }
        column.addView(labelEdit)

        column.addView(section(getString(R.string.repeat)))
        dayRow = Ui.horizontal(this)
        column.addView(dayRow)
        quickRow = Ui.horizontal(this).apply { setPadding(0, Ui.dp(this@AlarmEditActivity, 8f), 0, 0) }
        column.addView(quickRow)

        column.addView(section(getString(R.string.snooze_duration)))
        snoozeRow = Ui.horizontal(this)
        column.addView(snoozeRow)

        column.addView(section(getString(R.string.vibrate)))
        column.addView(
            Switch(this).apply {
                isChecked = vibrate
                thumbTintList = Ui.switchThumb()
                trackTintList = Ui.switchTrack()
                background = ColorDrawable(Ui.WHITE)
                minimumWidth = Ui.dp(this@AlarmEditActivity, 48f)
                minimumHeight = Ui.dp(this@AlarmEditActivity, 48f)
                contentDescription = getString(R.string.vibrate)
                setOnCheckedChangeListener { _, checked ->
                    vibrate = checked
                    persist()
                }
            },
            Ui.wrapWrap(),
        )

        column.addView(section(getString(R.string.ringtone)))
        ringtoneValue = Ui.text(this, "", 16f, Ui.BLACK)
        column.addView(
            Ui.horizontal(this).apply {
                addView(ringtoneValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(
                    Ui.button(this@AlarmEditActivity, getString(R.string.choose), filled = false, sp = 16f, heightDp = 48).apply {
                        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@AlarmEditActivity, 120f), Ui.dp(this@AlarmEditActivity, 48f))
                        setOnClickListener { pickRingtone() }
                    },
                )
            },
        )

        column.addView(Ui.space(this, 28))
        column.addView(
            Ui.button(this, getString(R.string.delete), filled = false, sp = 18f, heightDp = 56).apply {
                setOnClickListener { confirmDelete() }
            },
        )

        setContentView(
            ScrollView(this).apply {
                addView(column, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            },
        )

        Async.ioAsync(
            work = { Repo.alarm(alarmId) },
            then = { alarm ->
                if (alarm == null) {
                    finish()
                    return@ioAsync
                }
                hour = alarm.hour
                minute = alarm.minute
                mask = alarm.repeatMask
                vibrate = alarm.vibrate
                enabled = alarm.enabled
                snoozeMinutes = alarm.snoozeMinutes
                ringtone = alarm.ringtone
                labelEdit.setText(alarm.label)
                titleView.text = alarm.label.ifBlank { getString(R.string.alarm_label_default) }
                renderAll()
            },
        )
    }

    override fun onPause() {
        // The label is the only free-text field: commit it when leaving.
        persist()
        super.onPause()
    }

    private fun section(title: String): View = Ui.text(this, title, 15f, Ui.GRAY).apply {
        setPadding(0, Ui.dp(this@AlarmEditActivity, 20f), 0, Ui.dp(this@AlarmEditActivity, 8f))
    }

    private fun renderAll() {
        timeView.text = Fmt.clock(this, hour, minute)
        renderDays()
        renderQuick()
        renderSnooze()
        renderRingtone()
    }

    private fun renderDays() {
        dayRow.removeAllViews()
        for (day in DayOfWeek.entries) {
            val selected = (mask and Days.bit(day)) != 0
            dayRow.addView(
                Ui.chip(this, getString(Ui.dayNameRes(day)), selected, heightDp = 44).apply {
                    layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@AlarmEditActivity, 44f), 1f).apply {
                        rightMargin = Ui.dp(this@AlarmEditActivity, 4f)
                    }
                    setOnClickListener {
                        mask = if (selected) mask and Days.bit(day).inv() else mask or Days.bit(day)
                        renderDays()
                        renderQuick()
                        persist()
                    }
                },
            )
        }
    }

    private fun renderQuick() {
        quickRow.removeAllViews()
        val options = listOf(
            getString(R.string.every_day) to Days.ALL,
            getString(R.string.weekdays) to Days.WEEKDAYS,
            getString(R.string.weekends) to Days.WEEKEND,
            getString(R.string.once) to 0,
        )
        for ((label, value) in options) {
            quickRow.addView(
                Ui.chip(this, label, mask == value, heightDp = 44).apply {
                    layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@AlarmEditActivity, 44f), 1f).apply {
                        rightMargin = Ui.dp(this@AlarmEditActivity, 4f)
                    }
                    textSize = 13f
                    setOnClickListener {
                        mask = value
                        renderDays()
                        renderQuick()
                        persist()
                    }
                },
            )
        }
    }

    private fun renderSnooze() {
        snoozeRow.removeAllViews()
        for (minutes in SNOOZE_OPTIONS) {
            snoozeRow.addView(
                Ui.chip(this, getString(R.string.minutes_format, minutes), snoozeMinutes == minutes, heightDp = 44).apply {
                    layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@AlarmEditActivity, 44f), 1f).apply {
                        rightMargin = Ui.dp(this@AlarmEditActivity, 4f)
                    }
                    textSize = 14f
                    setOnClickListener {
                        snoozeMinutes = minutes
                        renderSnooze()
                        persist()
                    }
                },
            )
        }
    }

    private fun renderRingtone() {
        ringtoneValue.text = when (ringtone) {
            null -> getString(R.string.system_default)
            RINGTONE_SILENT -> getString(R.string.silent)
            else -> runCatching {
                RingtoneManager.getRingtone(this, Uri.parse(ringtone))?.getTitle(this)
            }.getOrNull() ?: getString(R.string.ringtone)
        }
    }

    /** Write this alarm and re-register everything. Called on every change. */
    private fun persist() {
        if (alarmId < 0) return
        val label = labelEdit.text.toString().trim()
        titleView.text = label.ifBlank { getString(R.string.alarm_label_default) }
        val alarm = Alarm(
            id = alarmId,
            hour = hour,
            minute = minute,
            label = label,
            enabled = enabled,
            repeatMask = mask,
            vibrate = vibrate,
            ringtone = ringtone,
            snoozeMinutes = if (snoozeMinutes > 0) snoozeMinutes else Prefs.of().defaultSnoozeMinutes,
            firedAt = null,
        )
        Async.io {
            Repo.updateAlarm(alarm)
            // A changed time must not keep a stale PendingIntent or a pending snooze.
            Scheduler.cancelAlarm(this, alarmId)
            Scheduler.rescheduleAll(this)
        }
    }

    private fun pickTime() {
        TimePickerDialog(
            this,
            { _, h, m ->
                hour = h
                minute = m
                timeView.text = Fmt.clock(this, hour, minute)
                persist()
            },
            hour,
            minute,
            Fmt.is24Hour(this),
        ).show()
    }

    private fun pickRingtone() {
        val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.ringtone))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtone?.let { Uri.parse(it) })
        }
        startActivityForResult(picker, REQ_RINGTONE)
        overridePendingTransition(0, 0)
    }

    @Deprecated("No AndroidX ActivityResult API: this app has no AndroidX at all.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_RINGTONE && resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringtone = uri?.toString() ?: RINGTONE_SILENT
            renderRingtone()
            persist()
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete)
            .setPositiveButton(R.string.delete) { _, _ ->
                Async.io {
                    Scheduler.cancelAlarm(this, alarmId)
                    Repo.deleteAlarm(alarmId)
                    Scheduler.rescheduleAll(this)
                }
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val REQ_RINGTONE = 21
        private val SNOOZE_OPTIONS = intArrayOf(5, 10, 15, 20, 30)
    }
}
