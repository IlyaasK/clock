package com.ilyaas.einkclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Timer expiry -> the same ringing service, with the timer UI. */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXPIRE) return
        val id = intent.getIntExtra(EXTRA_TIMER_ID, -1)
        if (id < 0) return
        AlarmService.acquireBootLock(context)
        val svc = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_RING_TIMER)
            .putExtra(AlarmService.EXTRA_TIMER_ID, id)
            .putExtra(AlarmService.EXTRA_TRIGGER_AT, intent.getLongExtra(EXTRA_TRIGGER_AT, 0L))
        try {
            context.startForegroundService(svc)
        } catch (e: Exception) {
            AlarmService.releaseBootLock()
            runCatching { context.startService(svc) }
        }
    }

    companion object {
        const val ACTION_EXPIRE = "com.ilyaas.einkclock.action.EXPIRE_TIMER"
        const val EXTRA_TIMER_ID = "timer_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
    }
}
