package com.ilyaas.einkclock.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ilyaas.einkclock.data.Alarm
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.data.TimerState
import com.ilyaas.einkclock.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything that talks to AlarmManager.
 *
 * User alarms use setAlarmClock(), which (a) makes SystemUI show the next alarm
 * in the status bar / lock screen and (b) exempts the trigger from Doze. Timers
 * use exact-but-not-indicator alarms so a running timer does not masquerade as
 * the "next alarm".
 *
 * Every function here touches SQLite and must run on [com.ilyaas.einkclock.util.Async.io].
 */
object Scheduler {

    /** Request codes: one PendingIntent per alarm id (per snooze slot), one per timer. */
    const val RC_ALARM_BASE = 0
    const val RC_SNOOZE_BASE = 100_000
    const val RC_TIMER_BASE = 200_000

    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun canUseFullScreenIntent(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Required on Android 14 for the ring screen to start from the background. */
    fun canDrawOverlays(context: Context): Boolean = android.provider.Settings.canDrawOverlays(context)

    private fun showIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        RC_ALARM_BASE,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alarmOperation(
        context: Context,
        alarmId: Int,
        snooze: Boolean,
        triggerAt: Long,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAt)
            .putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        val rc = if (snooze) RC_SNOOZE_BASE + alarmId else RC_ALARM_BASE + alarmId
        return PendingIntent.getBroadcast(
            context,
            rc,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun timerOperation(context: Context, timerId: Int, triggerAt: Long): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java)
            .setAction(TimerReceiver.ACTION_EXPIRE)
            .putExtra(TimerReceiver.EXTRA_TIMER_ID, timerId)
            .putExtra(TimerReceiver.EXTRA_TRIGGER_AT, triggerAt)
        return PendingIntent.getBroadcast(
            context,
            RC_TIMER_BASE + timerId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Register one alarm's next occurrence, or a snooze instance. Never throws. */
    fun scheduleAlarmClock(context: Context, alarmId: Int, triggerAt: Long, snooze: Boolean) {
        if (triggerAt <= System.currentTimeMillis()) return
        val am = context.getSystemService(AlarmManager::class.java)
        val op = alarmOperation(context, alarmId, snooze, triggerAt)
        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent(context))
        try {
            if (am.canScheduleExactAlarms()) {
                am.setAlarmClock(info, op)
            } else {
                // Exact alarms denied: still fire, just without the indicator/Doze grant.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
        }
    }

    fun scheduleTimer(context: Context, timerId: Int, triggerAt: Long) {
        if (triggerAt <= System.currentTimeMillis()) return
        val am = context.getSystemService(AlarmManager::class.java)
        val op = timerOperation(context, timerId, triggerAt)
        try {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
        }
    }

    /** Register the next occurrence of one alarm row (used after edit/toggle/dismiss). */
    fun scheduleAlarm(
        context: Context,
        alarm: Alarm,
        from: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        skips: Map<Int, Set<String>> = Repo.skipsByAlarm(),
    ) {
        if (!alarm.enabled) {
            cancelAlarm(context, alarm.id)
            return
        }
        val isSkipped = { id: Int, d: LocalDate -> skips[id]?.contains(d.toString()) == true }
        val next = NextTrigger.next(alarm, from, zone, isSkipped) ?: return
        scheduleAlarmClock(context, alarm.id, next.toInstant().toEpochMilli(), snooze = false)
    }

    fun scheduleSnooze(context: Context, alarmId: Int, atMillis: Long) {
        scheduleAlarmClock(context, alarmId, atMillis, snooze = true)
    }

    /** Drop a pending alarm and any pending snooze for it. */
    fun cancelAlarm(context: Context, alarmId: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(alarmOperation(context, alarmId, snooze = false, triggerAt = 0L))
        am.cancel(alarmOperation(context, alarmId, snooze = true, triggerAt = 0L))
        Prefs.of().clearSnooze(alarmId)
    }

    fun cancelTimer(context: Context, timerId: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(timerOperation(context, timerId, 0L))
    }

    /**
     * Recompute and re-register every enabled alarm and every running timer.
     * Called after any mutation, and on boot / time set / timezone change.
     *
     * [fromOverrides] pins the "now" used for one alarm id (the instance that just
     * fired), so dismissing an alarm early can never re-arm today's occurrence.
     */
    fun rescheduleAll(
        context: Context,
        fromOverrides: Map<Int, Long> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val now = Instant.now()
        val skips = Repo.skipsByAlarm()
        for (alarm in Repo.enabledAlarms()) {
            val base = fromOverrides[alarm.id]?.let { maxOf(it, now.toEpochMilli()) } ?: now.toEpochMilli()
            scheduleAlarm(context, alarm, Instant.ofEpochMilli(base), zone, skips)
        }
        for (timer in Repo.timers()) {
            if (timer.state == TimerState.RUNNING && timer.endAt != null) {
                scheduleTimer(context, timer.id, timer.endAt)
            }
        }
    }

    /** Diagnostics for the settings screen: the earliest registered trigger. */
    fun nextAlarmClock(context: Context): AlarmManager.AlarmClockInfo? =
        context.getSystemService(AlarmManager::class.java).nextAlarmClock
}
