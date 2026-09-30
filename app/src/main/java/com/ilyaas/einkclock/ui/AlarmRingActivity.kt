package com.ilyaas.einkclock.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.alarm.RingActions
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.util.Async
import com.ilyaas.einkclock.util.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The ring screen: shows over the keyguard, turns the screen on, keeps it on,
 * and the three buttons are the only way out (back does nothing).
 */
class AlarmRingActivity : Activity() {

    private lateinit var clockView: TextView
    private lateinit var titleView: TextView
    private lateinit var scheduledView: TextView
    private lateinit var snoozeView: TextView
    private lateinit var awakeButton: TextView
    private lateinit var actionRow: LinearLayout
    private lateinit var resultView: TextView
    private lateinit var undoButton: TextView

    private var alarmId = -1
    private var triggerAt = 0L
    private var snoozeMinutes = 10

    private val finishLater = Runnable { finishRing() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Ui.WHITE)
            setPadding(Ui.dp(this@AlarmRingActivity, 16f), Ui.dp(this@AlarmRingActivity, 24f), Ui.dp(this@AlarmRingActivity, 16f), Ui.dp(this@AlarmRingActivity, 24f))
        }

        clockView = Ui.text(this, "", 96f, Ui.BLACK).apply {
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
        titleView = Ui.text(this, "", 28f, Ui.BLACK).apply { gravity = Gravity.CENTER }
        scheduledView = Ui.text(this, "", 20f, Ui.GRAY).apply { gravity = Gravity.CENTER }
        snoozeView = Ui.text(this, "", 18f, Ui.GRAY).apply { gravity = Gravity.CENTER }
        resultView = Ui.text(this, "", 20f, Ui.BLACK).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        awakeButton = Ui.button(this, getString(R.string.im_awake), filled = true, sp = 24f, heightDp = 72)
        undoButton = Ui.button(this, getString(R.string.undo), filled = false, sp = 20f, heightDp = 64).apply {
            visibility = View.GONE
            setOnClickListener { undoSkips() }
        }

        val pause = Ui.button(this, getString(R.string.pause), filled = false, sp = 20f, heightDp = 64, weight = 1f)
        val cancel = Ui.button(this, getString(R.string.cancel), filled = false, sp = 20f, heightDp = 64, weight = 1f)
        actionRow = Ui.horizontal(this).apply {
            addView(pause)
            addView(Ui.gap(this@AlarmRingActivity, 8))
            addView(cancel)
        }

        column.addView(clockView)
        column.addView(Ui.space(this, 8))
        column.addView(titleView)
        column.addView(Ui.space(this, 6))
        column.addView(scheduledView)
        column.addView(Ui.space(this, 6))
        column.addView(snoozeView)
        column.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                addView(resultView)
                addView(Ui.space(this@AlarmRingActivity, 12))
                addView(undoButton)
                addView(Ui.space(this@AlarmRingActivity, 8))
                addView(awakeButton)
                addView(Ui.space(this@AlarmRingActivity, 10))
                addView(actionRow)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(column)

        awakeButton.setOnClickListener { imAwake() }
        pause.setOnClickListener { pause() }
        cancel.setOnClickListener { cancelInstance() }

        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun render(from: Intent?) {
        val i = from ?: return
        Async.cancel(finishLater)
        alarmId = i.getIntExtra(EXTRA_ID, -1)
        triggerAt = i.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis())
        snoozeMinutes = i.getIntExtra(EXTRA_SNOOZE_MINUTES, Prefs.of().defaultSnoozeMinutes)
        val snoozed = i.getBooleanExtra(EXTRA_SNOOZED, false)
        val snoozeAt = i.getLongExtra(EXTRA_SNOOZE_AT, 0L)
        val missed = i.getBooleanExtra(EXTRA_MISSED, false)
        val hour = i.getIntExtra(EXTRA_HOUR, 0)
        val minute = i.getIntExtra(EXTRA_MINUTE, 0)

        clockView.text = ZonedDateTime.now().format(if (Fmt.is24Hour(this)) H24 else H12)
        titleView.text = when {
            missed -> getString(R.string.missed_alarm)
            else -> i.getStringExtra(EXTRA_LABEL) ?: getString(R.string.alarm_label_default)
        }
        scheduledView.text = Fmt.clock(this, hour, minute)
        if (snoozed && snoozeAt > 0L) {
            snoozeView.visibility = View.VISIBLE
            snoozeView.text = getString(
                R.string.snoozed_at,
                Fmt.clock(this, Instant.ofEpochMilli(snoozeAt), ZoneId.systemDefault()),
            )
        } else {
            snoozeView.visibility = View.GONE
        }
        resultView.visibility = View.GONE
        undoButton.visibility = View.GONE
        awakeButton.visibility = View.VISIBLE
        actionRow.visibility = View.VISIBLE
    }

    private fun imAwake() {
        if (alarmId < 0) {
            finishRing()
            return
        }
        val window = Prefs.of().imAwakeWindowMinutes
        awakeButton.isEnabled = false
        awakeButton.text = getString(R.string.im_awake)
        RingActions.imAwake(this, alarmId, triggerAt, window) { result ->
            if (result.labels.isEmpty()) {
                // Nothing else was part of the wake-up burst: just get out of the way.
                finishRing()
                return@imAwake
            }
            skippedAlarmIds = result.ids
            resultView.visibility = View.VISIBLE
            resultView.text = getString(R.string.skipped_alarms, result.labels.joinToString(", "))
            awakeButton.visibility = View.GONE
            actionRow.visibility = View.GONE
            undoButton.visibility = View.VISIBLE
            Async.mainDelayed(10_000L, finishLater)
        }
    }

    private var skippedAlarmIds: List<Int> = emptyList()

    private fun undoSkips() {
        Async.cancel(finishLater)
        RingActions.undo(this, skippedAlarmIds) {
            resultView.text = getString(R.string.skips_undone)
            undoButton.visibility = View.GONE
            Async.mainDelayed(1_500L, finishLater)
        }
    }

    private fun pause() {
        if (alarmId >= 0) RingActions.snooze(this, alarmId, triggerAt, snoozeMinutes)
        finishRing()
    }

    private fun cancelInstance() {
        if (alarmId >= 0) RingActions.cancel(this, alarmId, triggerAt)
        finishRing()
    }

    private fun finishRing() {
        Async.cancel(finishLater)
        finish()
        overridePendingTransition(0, 0)
    }

    /** The buttons are the only exit. */
    @Deprecated("Required override: back must not silently dismiss an alarm.")
    override fun onBackPressed() {
        // deliberately ignored
    }

    override fun onDestroy() {
        Async.cancel(finishLater)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_LABEL = "label"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_SNOOZED = "snoozed"
        const val EXTRA_SNOOZE_AT = "snooze_at"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_MISSED = "missed"
        const val EXTRA_TRIGGER_AT = "trigger_at"

        private val H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        private val H12 = DateTimeFormatter.ofPattern("h:mm", Locale.US)
    }
}
