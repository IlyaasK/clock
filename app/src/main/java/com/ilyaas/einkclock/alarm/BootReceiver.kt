package com.ilyaas.einkclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ilyaas.einkclock.util.Async

/**
 * Re-registers every enabled alarm after a reboot (including a reboot before the
 * first unlock), a time change, a timezone change, or an app update.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit
            else -> return
        }
        val pending = goAsync()
        Async.io {
            try {
                Scheduler.rescheduleAll(context)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}
