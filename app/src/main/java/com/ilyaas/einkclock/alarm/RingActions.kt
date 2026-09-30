package com.ilyaas.einkclock.alarm

import android.content.Context
import com.ilyaas.einkclock.data.Alarm
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.data.TimerState
import com.ilyaas.einkclock.util.Async
import com.ilyaas.einkclock.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The three ring-screen verbs, plus timer dismiss/extend.
 *
 * All three stop the sound first, then do their bookkeeping on the io thread:
 *  - Cancel  : dismiss this instance only (one-shot rows are disabled, not deleted)
 *  - Pause   : snooze this alarm (one-off exact alarm at now + snoozeMinutes)
 *  - I'm awake: dismiss this instance and skip today's other occurrences that
 *              fall inside the window, via alarm_skip rows only
 */
object RingActions {

    /** What "I'm awake" skipped: ids for Undo, labels for the message. */
    class SkipResult(val ids: List<Int>, val labels: List<String>)

    /** Called when a *regular* (non-snooze) instance starts ringing. */
    fun consumeInstance(context: Context, alarm: Alarm, triggerAt: Long) {
        val now = System.currentTimeMillis()
        Repo.setFiredAt(alarm.id, now)
        if (alarm.isRepeating) {
            // Re-arm the next occurrence immediately, so a ring the user never
            // dismisses cannot leave the alarm unscheduled.
            Scheduler.scheduleAlarm(context, alarm, Instant.ofEpochMilli(maxOf(triggerAt, now)))
        } else {
            // One-shot: nothing pending until it is dismissed (then it is disabled).
            Scheduler.cancelAlarm(context, alarm.id)
        }
    }

    fun cancel(context: Context, alarmId: Int, triggerAt: Long) {
        AlarmService.control(context, AlarmService.ACTION_DISMISS, "${AlarmService.KIND_ALARM}:$alarmId")
        Async.io {
            val alarm = Repo.alarm(alarmId)
            Prefs.of().clearSnooze(alarmId)
            Repo.setFiredAt(alarmId, System.currentTimeMillis())
            if (alarm == null) return@io
            if (alarm.isRepeating) {
                val now = System.currentTimeMillis()
                Scheduler.scheduleAlarm(context, alarm, Instant.ofEpochMilli(maxOf(triggerAt, now)))
            } else {
                // Keep the row so it can be re-enabled by hand.
                Repo.setEnabled(alarmId, false)
            }
        }
    }

    /** DISMISS_ALARM with search mode ALL (or a plain "stop everything"). */
    fun dismissAll(context: Context) {
        AlarmService.control(context, AlarmService.ACTION_DISMISS_ALL)
    }

    fun snooze(context: Context, alarmId: Int, triggerAt: Long, snoozeMinutes: Int) {
        AlarmService.control(context, AlarmService.ACTION_DISMISS, "${AlarmService.KIND_ALARM}:$alarmId")
        Async.io {
            val minutes = if (snoozeMinutes > 0) snoozeMinutes else Prefs.of().defaultSnoozeMinutes
            val until = System.currentTimeMillis() + minutes * 60_000L
            Prefs.of().setSnoozeAt(alarmId, until)
            Scheduler.scheduleSnooze(context, alarmId, until)
        }
    }

    /**
     * I'm awake: dismiss the ringing instance exactly like Cancel, and add
     * alarm_skip rows for today for every other enabled alarm whose next trigger
     * is inside now + windowMinutes. One-shot victims are disabled instead (a skip
     * row would disarm them forever).
     */
    fun imAwake(
        context: Context,
        alarmId: Int,
        triggerAt: Long,
        windowMinutes: Int,
        onResult: (SkipResult) -> Unit,
    ) {
        val key = "${AlarmService.KIND_ALARM}:$alarmId"
        AlarmService.control(context, AlarmService.ACTION_DISMISS, key)
        Async.ioAsync(
            work = {
                val zone = ZoneId.systemDefault()
                val now = Instant.now()
                val today = now.atZone(zone).toLocalDate()
                val skips = Repo.skipsByAlarm()
                val isSkipped = { id: Int, d: LocalDate -> skips[id]?.contains(d.toString()) == true }
                val alarms = Repo.alarms()
                val victims = NextTrigger.wakeCluster(alarms, now, zone, windowMinutes, isSkipped, alarmId)
                for (v in victims) {
                    if (v.isRepeating) Repo.addSkip(v.id, today) else Repo.setEnabled(v.id, false)
                }
                Prefs.of().clearSnooze(alarmId)
                Repo.setFiredAt(alarmId, System.currentTimeMillis())
                val ringing = alarms.firstOrNull { it.id == alarmId }
                if (ringing != null && !ringing.isRepeating) Repo.setEnabled(alarmId, false)
                Scheduler.rescheduleAll(
                    context,
                    fromOverrides = mapOf(alarmId to maxOf(triggerAt, System.currentTimeMillis())),
                    zone = zone,
                )
                SkipResult(victims.map { it.id }, victims.map { Fmt.clock(context, it.hour, it.minute) })
            },
            then = onResult,
        )
    }

    fun undo(context: Context, alarmIds: List<Int>, onDone: () -> Unit) {
        Async.ioAsync(
            work = {
                val today = LocalDate.now()
                Repo.removeSkips(alarmIds, today)
                // One-shot victims were disabled rather than skipped; re-enable both kinds.
                for (id in alarmIds) Repo.setEnabled(id, true)
                Scheduler.rescheduleAll(context)
            },
            then = { onDone() },
        )
    }

    // ------------------------------------------------------------------ timers

    fun dismissTimer(context: Context, timerId: Int) {
        AlarmService.control(context, AlarmService.ACTION_DISMISS, "${AlarmService.KIND_TIMER}:$timerId")
        Async.io { Timers.delete(context, timerId) }
    }

    fun extendTimer(context: Context, timerId: Int) {
        AlarmService.control(context, AlarmService.ACTION_DISMISS, "${AlarmService.KIND_TIMER}:$timerId")
        Async.io { Timers.addMinute(context, timerId) }
    }

    fun dismissAllTimers(context: Context) {
        AlarmService.control(context, AlarmService.ACTION_DISMISS_ALL)
        Async.io {
            for (t in Repo.timers()) {
                if (t.state == TimerState.IDLE && t.remainingMs <= 0L) Timers.delete(context, t.id)
            }
        }
    }
}
