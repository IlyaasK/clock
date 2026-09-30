package com.ilyaas.einkclock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ilyaas.einkclock.alarm.AlarmService
import com.ilyaas.einkclock.data.Repo
import android.util.Log

/**
 * Debug-only test hook so acceptance tests never wait for a real clock time:
 *
 *   adb shell am broadcast -n com.ilyaas.einkclock/.debug.TestRingReceiver --ei alarm_id 3
 *   adb shell am broadcast -n com.ilyaas.einkclock/.debug.TestRingReceiver --ei timer_id 1
 *
 * Declared only in src/debug/AndroidManifest.xml, so release builds have no such
 * component and no such code.
 */
class TestRingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarm_id", -1)
        val timerId = intent.getIntExtra("timer_id", -1)
        when {
            alarmId >= 0 -> {
                // Fire like the real thing: pass the alarm's actual scheduled
                // trigger, so "dismiss this instance" consumes the right one.
                AlarmService.acquireBootLock(context)
                val pending = goAsync()
                Thread {
                    val alarm = Repo.alarm(alarmId)
                    val zone = java.time.ZoneId.systemDefault()
                    val skips = Repo.skipsByAlarm()
                    val triggerAt = alarm?.let {
                        com.ilyaas.einkclock.alarm.NextTrigger.next(
                            it,
                            java.time.Instant.now(),
                            zone,
                        ) { id, date -> skips[id]?.contains(date.toString()) == true }?.toInstant()?.toEpochMilli()
                    } ?: System.currentTimeMillis()
                    context.startForegroundService(
                        Intent(context, AlarmService::class.java)
                            .setAction(AlarmService.ACTION_RING_ALARM)
                            .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                            .putExtra(AlarmService.EXTRA_TRIGGER_AT, triggerAt)
                            .putExtra(AlarmService.EXTRA_SNOOZE, intent.getBooleanExtra("snooze", false)),
                    )
                    pending.finish()
                }.start()
            }
            timerId >= 0 -> {
                AlarmService.acquireBootLock(context)
                context.startForegroundService(
                    Intent(context, AlarmService::class.java)
                        .setAction(AlarmService.ACTION_RING_TIMER)
                        .putExtra(AlarmService.EXTRA_TIMER_ID, timerId),
                )
            }
        }
    }
}

/**
 * Debug-only alarm factory so acceptance tests are deterministic:
 *
 *   adb shell am broadcast -n com.ilyaas.einkclock/.debug/TestCreateReceiver \
 *     --ei hour 8 --ei minute 5 --ei mask 127 --es label "daily" --ei count 4
 *
 * mask: bit0=Mon .. bit6=Sun, 0 = once. count creates that many alarms one
 * minute apart, mirroring the brief's 8:00 / 8:05 / 8:10 / 8:20 cluster.
 */
class TestCreateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("hour", 8)
        val minute = intent.getIntExtra("minute", 0)
        val mask = intent.getIntExtra("mask", 0)
        val label = intent.getStringExtra("label") ?: ""
        val count = intent.getIntExtra("count", 1).coerceAtLeast(1)
        val pending = goAsync()
        Thread {
            try {
                var m = minute
                var h = hour
                repeat(count) {
                    Repo.insertAlarm(
                        com.ilyaas.einkclock.data.Alarm(
                            hour = h,
                            minute = m,
                            label = label,
                            repeatMask = mask,
                            snoozeMinutes = 10,
                        ),
                    )
                    m += 5
                    if (m >= 60) {
                        m -= 60
                        h = (h + 1) % 24
                    }
                }
                com.ilyaas.einkclock.alarm.Scheduler.rescheduleAll(context)
                Log.i("ClockDebug", "created $count alarm(s) from $hour:$minute mask=$mask")
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/**
 * Debug-only DB dump for environments without a host sqlite3:
 *
 *   adb shell am broadcast -n com.ilyaas.einkclock/.debug.DumpReceiver
 *   adb logcat -d -s ClockDebug
 */
class DumpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val thread = Thread {
            try {
                val skips = Repo.skipsByAlarm()
                for (a in Repo.alarms()) {
                    val dates = skips[a.id]?.sorted()?.joinToString(",") ?: ""
                    Log.i(
                        "ClockDebug",
                        "alarm id=${a.id} ${a.hour}:${a.minute} enabled=${a.enabled} mask=${a.repeatMask} " +
                            "vibrate=${a.vibrate} snooze=${a.snoozeMinutes} skipped=[$dates]",
                    )
                }
                Log.i("ClockDebug", "skips=${skips.size} alarms=${Repo.alarms().size} timers=${Repo.timers().size}")
            } finally {
                pending.finish()
            }
        }
        thread.start()
    }
}
