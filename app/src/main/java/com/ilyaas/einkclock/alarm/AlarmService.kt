package com.ilyaas.einkclock.alarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.data.Alarm
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.data.RINGTONE_SILENT
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.data.TimerState
import com.ilyaas.einkclock.ui.AlarmRingActivity
import com.ilyaas.einkclock.ui.TimersUpActivity
import com.ilyaas.einkclock.util.Async
import com.ilyaas.einkclock.util.Fmt

/**
 * Foreground service that owns the ringing state: one ring at a time, extra
 * rings queue up behind it. It plays the ringtone on the alarm stream, vibrates,
 * posts a high-importance notification with a full-screen intent, and launches
 * the ring screen.
 */
class AlarmService : Service() {

    private class Ring(
        val kind: String,
        val id: Int,
        val triggerAt: Long,
        val label: String,
        val hour: Int,
        val minute: Int,
        val snoozed: Boolean,
        val snoozeAt: Long,
        val snoozeMinutes: Int,
        val vibrate: Boolean,
        val ringtone: String?,
        val missed: Boolean,
    ) {
        val key: String get() = "$kind:$id"
        val notificationId: Int get() = NOTIF_BASE + if (kind == KIND_TIMER) TIMER_NOTIF_OFFSET + id else id
    }

    private var current: Ring? = null
    private val queue = ArrayList<Ring>()
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING_ALARM -> ringAlarm(intent)
            ACTION_RING_TIMER -> ringTimer(intent)
            ACTION_DISMISS -> dismiss(intent.getStringExtra(EXTRA_KEY))
            ACTION_DISMISS_ALL -> dismissAll()
            else -> if (current == null) stopEverything()
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ ringing

    private fun ringAlarm(intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (id < 0) {
            stopIfIdle()
            return
        }
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis())
        val snoozed = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        releaseBootLock()
        Async.io {
            val alarm: Alarm? = Repo.alarm(id)
            if (alarm == null) {
                Async.main { stopIfIdle() }
                return@io
            }
            if (!snoozed) RingActions.consumeInstance(this, alarm, triggerAt)
            val req = Ring(
                kind = KIND_ALARM,
                id = id,
                triggerAt = triggerAt,
                label = alarm.label.ifBlank { getString(R.string.alarm_label_default) },
                hour = alarm.hour,
                minute = alarm.minute,
                snoozed = snoozed,
                snoozeAt = if (snoozed) Prefs.of().snoozeAt(id) else 0L,
                snoozeMinutes = alarm.snoozeMinutes,
                vibrate = alarm.vibrate,
                ringtone = alarm.ringtone,
                missed = System.currentTimeMillis() - triggerAt > MISSED_AFTER_MS,
            )
            Async.main { enqueueOrRing(req) }
        }
    }

    private fun ringTimer(intent: Intent) {
        val id = intent.getIntExtra(EXTRA_TIMER_ID, -1)
        if (id < 0) {
            stopIfIdle()
            return
        }
        releaseBootLock()
        Async.io {
            val timer = Repo.timer(id)
            if (timer == null) {
                Async.main { stopIfIdle() }
                return@io
            }
            // Park it at zero: the "Timers up" screen shows it until dismissed.
            Repo.updateTimer(timer.copy(state = TimerState.IDLE, remainingMs = 0L, endAt = null))
            val req = Ring(
                kind = KIND_TIMER,
                id = id,
                triggerAt = System.currentTimeMillis(),
                label = timer.label.ifBlank { getString(R.string.timer_label_default) },
                hour = 0,
                minute = 0,
                snoozed = false,
                snoozeAt = 0L,
                snoozeMinutes = 0,
                vibrate = true,
                ringtone = null,
                missed = false,
            )
            Async.main { enqueueOrRing(req) }
        }
    }

    private fun enqueueOrRing(req: Ring) {
        if (current != null) {
            queue.removeAll { it.key == req.key }
            queue.add(req)
            return
        }
        current = req
        startRing(req)
    }

    private fun startRing(req: Ring) {
        startForeground(req.notificationId, buildNotification(req), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (ringLock == null) {
            ringLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "einkclock:ringing")
                .also {
                    it.setReferenceCounted(false)
                    it.acquire(30 * 60_000L)
                }
        }
        startActivity(ringIntent(req))
        startSound(req)
    }

    private fun ringTitle(req: Ring): String = when {
        req.kind == KIND_TIMER -> getString(R.string.timers_up)
        req.missed -> getString(R.string.missed_alarm)
        else -> req.label
    }

    private fun ringText(req: Ring): String = when {
        req.kind == KIND_TIMER -> req.label
        req.snoozed -> getString(R.string.snoozed_at, Fmt.clock(this, req.hour, req.minute))
        else -> Fmt.clock(this, req.hour, req.minute)
    }

    private fun ringIntent(req: Ring): Intent {
        val target = if (req.kind == KIND_TIMER) TimersUpActivity::class.java else AlarmRingActivity::class.java
        return Intent(this, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(AlarmRingActivity.EXTRA_KEY, req.key)
            .putExtra(AlarmRingActivity.EXTRA_KIND, req.kind)
            .putExtra(AlarmRingActivity.EXTRA_ID, req.id)
            .putExtra(AlarmRingActivity.EXTRA_TITLE, ringTitle(req))
            .putExtra(AlarmRingActivity.EXTRA_LABEL, req.label)
            .putExtra(AlarmRingActivity.EXTRA_HOUR, req.hour)
            .putExtra(AlarmRingActivity.EXTRA_MINUTE, req.minute)
            .putExtra(AlarmRingActivity.EXTRA_SNOOZED, req.snoozed)
            .putExtra(AlarmRingActivity.EXTRA_SNOOZE_AT, req.snoozeAt)
            .putExtra(AlarmRingActivity.EXTRA_SNOOZE_MINUTES, req.snoozeMinutes)
            .putExtra(AlarmRingActivity.EXTRA_MISSED, req.missed)
            .putExtra(AlarmRingActivity.EXTRA_TRIGGER_AT, req.triggerAt)
    }

    private fun buildNotification(req: Ring): Notification {
        val channel = if (req.kind == KIND_TIMER) Notifications.CHANNEL_TIMER else Notifications.CHANNEL_ALARM
        val content = Notifications.activity(this, req.notificationId, ringIntent(req))
        return Notifications.ringing(this, channel, req.notificationId, ringTitle(req), ringText(req), content)
    }

    private fun startSound(req: Ring) {
        stopSound()
        if (req.ringtone != RINGTONE_SILENT) {
            val uri: Uri? = req.ringtone?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (uri != null) {
                runCatching {
                    player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        setDataSource(this@AlarmService, uri)
                        isLooping = true
                        setOnErrorListener { _, _, _ -> true }
                        setOnPreparedListener { mp -> runCatching { mp.start() } }
                        prepareAsync()
                    }
                }.onFailure { player = null }
            }
        }
        if (req.vibrate) {
            val v = getSystemService(VibratorManager::class.java)?.defaultVibrator
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0))
                vibrator = v
            }
        }
    }

    private fun stopSound() {
        player?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        player = null
        vibrator?.let { v -> runCatching { v.cancel() } }
        vibrator = null
    }

    // ----------------------------------------------------------------- stopping

    private fun dismiss(key: String?) {
        val cur = current
        if (cur == null) {
            stopIfIdle()
            return
        }
        if (key != null && key != cur.key) {
            // Dismissing a queued ring: drop it, leave the current one alone.
            queue.removeAll { it.key == key }
            return
        }
        stopSound()
        Notifications.cancel(this, cur.notificationId)
        current = null
        val next = if (queue.isNotEmpty()) queue.removeAt(0) else null
        if (next != null) {
            current = next
            startRing(next)
        } else {
            stopEverything()
        }
    }

    private fun dismissAll() {
        queue.clear()
        current?.let { Notifications.cancel(this, it.notificationId) }
        stopEverything()
    }

    private fun stopIfIdle() {
        if (current == null && queue.isEmpty()) stopEverything()
    }

    private fun stopEverything() {
        stopSound()
        current = null
        queue.clear()
        ringLock?.let { if (it.isHeld) it.release() }
        ringLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        ringLock?.let { if (it.isHeld) it.release() }
        ringLock = null
        instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING_ALARM = "com.ilyaas.einkclock.action.RING_ALARM"
        const val ACTION_RING_TIMER = "com.ilyaas.einkclock.action.RING_TIMER"
        const val ACTION_DISMISS = "com.ilyaas.einkclock.action.DISMISS"
        const val ACTION_DISMISS_ALL = "com.ilyaas.einkclock.action.DISMISS_ALL"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_TIMER_ID = "timer_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_SNOOZE = "snooze"
        const val EXTRA_KEY = "key"

        const val KIND_ALARM = "alarm"
        const val KIND_TIMER = "timer"

        private const val NOTIF_BASE = 1000
        private const val TIMER_NOTIF_OFFSET = 5000
        private const val MISSED_AFTER_MS = 10 * 60_000L

        @Volatile
        private var instance: AlarmService? = null

        @Volatile
        private var bootLock: PowerManager.WakeLock? = null

        fun isRinging(): Boolean = instance?.current != null

        fun currentKey(): String? = instance?.current?.key

        /** Short wake lock bridging the receiver and startForeground(). */
        fun acquireBootLock(context: Context) {
            val wl = context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "einkclock:ring")
            wl.setReferenceCounted(false)
            wl.acquire(10_000L)
            bootLock = wl
        }

        internal fun releaseBootLock() {
            bootLock?.let { if (it.isHeld) it.release() }
            bootLock = null
        }

        /**
         * Send a control action to a live ringing service. Returns false when
         * there is nothing ringing, so callers never start the service cold.
         */
        fun control(context: Context, action: String, key: String? = null): Boolean {
            val svc = instance ?: return false
            if (svc.current == null && action != ACTION_DISMISS_ALL) return false
            val i = Intent(context, AlarmService::class.java).setAction(action)
            if (key != null) i.putExtra(EXTRA_KEY, key)
            return runCatching { context.startService(i) }.isSuccess
        }
    }
}
