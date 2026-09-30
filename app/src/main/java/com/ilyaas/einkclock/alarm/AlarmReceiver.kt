package com.ilyaas.einkclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmManager -> service hop. Holds a short wake lock while the process comes
 * up (released by AlarmService once it has called startForeground).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (id < 0) return
        AlarmService.acquireBootLock(context)
        val svc = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_RING_ALARM)
            .putExtra(AlarmService.EXTRA_ALARM_ID, id)
            .putExtra(AlarmService.EXTRA_TRIGGER_AT, intent.getLongExtra(EXTRA_TRIGGER_AT, 0L))
            .putExtra(AlarmService.EXTRA_SNOOZE, intent.getBooleanExtra(EXTRA_SNOOZE, false))
        try {
            context.startForegroundService(svc)
        } catch (e: Exception) {
            // Rare: a foreground-service start refused by the platform. Fall back to
            // a plain service start; the wake lock times out on its own either way.
            AlarmService.releaseBootLock()
            runCatching { context.startService(svc) }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.ilyaas.einkclock.action.FIRE_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_SNOOZE = "snooze"
    }
}
