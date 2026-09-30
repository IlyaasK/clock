package com.ilyaas.einkclock.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ilyaas.einkclock.R

/** Notification channels + the ringing notification. Registered before anything posts. */
object Notifications {

    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_TIMER = "timer"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ALARM) == null) {
            val alarm = NotificationChannel(
                CHANNEL_ALARM,
                context.getString(R.string.channel_alarm),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alarm)
                // Sound and vibration are produced by AlarmService itself on the
                // alarm stream, so the channel must stay silent.
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(alarm)
        }
        if (nm.getNotificationChannel(CHANNEL_TIMER) == null) {
            val timer = NotificationChannel(
                CHANNEL_TIMER,
                context.getString(R.string.channel_timer),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_timer)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(timer)
        }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    /** The ringing notification: full-screen intent is the fallback when the screen is locked. */
    fun ringing(
        context: Context,
        channel: String,
        notificationId: Int,
        title: String,
        text: String,
        contentIntent: PendingIntent,
    ): Notification = Notification.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(title)
        .setContentText(text)
        .setCategory(Notification.CATEGORY_ALARM)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setAutoCancel(false)
        .setShowWhen(false)
        .setContentIntent(contentIntent)
        .setFullScreenIntent(contentIntent, true)
        .build()

    /** Small helper for the "open the app on the alarm tab" pending intent. */
    fun activity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
