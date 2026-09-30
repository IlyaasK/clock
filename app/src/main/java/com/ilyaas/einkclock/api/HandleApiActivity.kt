package com.ilyaas.einkclock.api

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import com.ilyaas.einkclock.alarm.AlarmService
import com.ilyaas.einkclock.alarm.RingActions
import com.ilyaas.einkclock.alarm.Scheduler
import com.ilyaas.einkclock.alarm.Timers
import com.ilyaas.einkclock.data.Alarm
import com.ilyaas.einkclock.data.Days
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.ui.AlarmEditActivity
import com.ilyaas.einkclock.ui.MainActivity
import com.ilyaas.einkclock.util.Async
import java.time.DayOfWeek

/**
 * The public alarm/timer intent API: SET_ALARM, SET_TIMER, SHOW_ALARMS,
 * SHOW_TIMERS, DISMISS_ALARM, DISMISS_TIMER, SNOOZE_ALARM.
 *
 * SET_ALARM / SET_TIMER arrive through the activity-alias that carries
 * com.android.alarm.permission.SET_ALARM, mirroring the stock manifest.
 */
class HandleApiActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> setAlarm(intent)
            AlarmClock.ACTION_SET_TIMER -> setTimer(intent)
            AlarmClock.ACTION_SHOW_ALARMS -> openTab(MainActivity.TAB_ALARM)
            AlarmClock.ACTION_SHOW_TIMERS -> openTab(MainActivity.TAB_TIMER)
            AlarmClock.ACTION_DISMISS_ALARM, AlarmClock.ACTION_DISMISS_TIMER -> dismiss(intent)
            AlarmClock.ACTION_SNOOZE_ALARM -> snooze(intent)
            else -> finish()
        }
    }

    private fun setAlarm(intent: Intent) {
        val hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
        val minutes = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1)
        if (hour in 0..23 && minutes in 0..59) {
            val mask = maskFromDays(intent)
            val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE) ?: ""
            val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
            Async.io {
                val id = Repo.insertAlarm(
                    Alarm(
                        hour = hour,
                        minute = minutes,
                        label = label,
                        repeatMask = mask,
                        snoozeMinutes = Prefs.of().defaultSnoozeMinutes,
                    ),
                )
                Scheduler.rescheduleAll(this)
                Async.main {
                    if (skipUi) {
                        openTab(MainActivity.TAB_ALARM)
                    } else {
                        // Confirmation UI: the normal (auto-saving) detail screen.
                        startActivity(
                            Intent(this, AlarmEditActivity::class.java)
                                .putExtra(AlarmEditActivity.EXTRA_ALARM_ID, id),
                        )
                        overridePendingTransition(0, 0)
                        finish()
                    }
                }
            }
        } else {
            openTab(MainActivity.TAB_ALARM)
        }
    }

    private fun setTimer(intent: Intent) {
        val lengthMs = intent.getLongExtra(AlarmClock.EXTRA_LENGTH, 0L)
            .takeIf { it > 0L } ?: intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0).toLong()
        val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE) ?: ""
        if (lengthMs > 0L) {
            Async.io {
                Timers.create(
                    this,
                    lengthMs,
                    label.ifBlank { getString(com.ilyaas.einkclock.R.string.timer_label_default) },
                )
                Async.main { openTab(MainActivity.TAB_TIMER) }
            }
        } else {
            openTab(MainActivity.TAB_TIMER)
        }
    }

    private fun dismiss(intent: Intent) {
        when (intent.getStringExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE)) {
            AlarmClock.ALARM_SEARCH_MODE_ALL -> { if (intent.action == AlarmClock.ACTION_DISMISS_TIMER) RingActions.dismissAllTimers(this) else RingActions.dismissAll(this) }
            else -> RingActions.dismissAll(this)
        }
        finish()
    }

    private fun snooze(intent: Intent) {
        // SNOOZE_ALARM carries no alarm id; the ringing instance honours its own snooze length.
        val key = AlarmService.currentKey()
        if (key != null && key.startsWith(AlarmService.KIND_ALARM + ":")) {
            val id = key.substringAfter(':').toIntOrNull() ?: -1
            if (id >= 0) {
                Async.io {
                    val alarm = Repo.alarm(id)
                    val minutes = alarm?.snoozeMinutes ?: Prefs.of().defaultSnoozeMinutes
                    Scheduler.scheduleSnooze(
                        this,
                        id,
                        System.currentTimeMillis() + minutes * 60_000L,
                    )
                }
                AlarmService.control(this, AlarmService.ACTION_DISMISS, key)
            }
        }
        finish()
    }

    private fun maskFromDays(intent: Intent): Int {
        @Suppress("DEPRECATION")
        val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS) ?: return 0
        if (days.isEmpty()) return 0
        var mask = 0
        for (calendarDay in days) {
            // Calendar: SUNDAY=1 … SATURDAY=7 -> ISO Monday-first bits.
            val day = when (calendarDay) {
                1 -> DayOfWeek.SUNDAY
                2 -> DayOfWeek.MONDAY
                3 -> DayOfWeek.TUESDAY
                4 -> DayOfWeek.WEDNESDAY
                5 -> DayOfWeek.THURSDAY
                6 -> DayOfWeek.FRIDAY
                else -> DayOfWeek.SATURDAY
            }
            mask = mask or Days.bit(day)
        }
        return mask
    }

    private fun openTab(tab: Int) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_TAB, tab),
        )
        overridePendingTransition(0, 0)
        finish()
    }
}
