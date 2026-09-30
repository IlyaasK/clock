package com.ilyaas.einkclock.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Small settings + instance state store (device-protected SharedPreferences).
 * Everything that is a *setting* or a *transient instance* lives here; alarms,
 * skips and timers live in SQLite.
 */
class Prefs private constructor(private val p: SharedPreferences) {

    var imAwakeWindowMinutes: Int
        get() = p.getInt(K_IMAWAKE_WINDOW, 15)
        set(value) = p.edit().putInt(K_IMAWAKE_WINDOW, value).apply()

    var defaultSnoozeMinutes: Int
        get() = p.getInt(K_DEFAULT_SNOOZE, 10)
        set(value) = p.edit().putInt(K_DEFAULT_SNOOZE, value).apply()

    var notificationRationaleShown: Boolean
        get() = p.getBoolean(K_NOTIF_RATIONALE, false)
        set(value) = p.edit().putBoolean(K_NOTIF_RATIONALE, value).apply()

    var batteryNudgeShown: Boolean
        get() = p.getBoolean(K_BATTERY_NUDGE, false)
        set(value) = p.edit().putBoolean(K_BATTERY_NUDGE, value).apply()

    var overlayNudgeShown: Boolean
        get() = p.getBoolean(K_OVERLAY_NUDGE, false)
        set(value) = p.edit().putBoolean(K_OVERLAY_NUDGE, value).apply()

    // ---- snooze bookkeeping: which alarm is currently snoozed, until when ----
    fun snoozeAt(alarmId: Int): Long = p.getLong("$K_SNOOZE$alarmId", 0L)

    fun setSnoozeAt(alarmId: Int, at: Long) {
        p.edit().putLong("$K_SNOOZE$alarmId", at).apply()
    }

    fun clearSnooze(alarmId: Int) {
        p.edit().remove("$K_SNOOZE$alarmId").apply()
    }

    // ---- stopwatch survives process death ----
    var stopwatchRunning: Boolean
        get() = p.getBoolean(K_SW_RUNNING, false)
        set(value) = p.edit().putBoolean(K_SW_RUNNING, value).apply()

    var stopwatchStartedAt: Long
        get() = p.getLong(K_SW_STARTED, 0L)
        set(value) = p.edit().putLong(K_SW_STARTED, value).apply()

    var stopwatchAccumulated: Long
        get() = p.getLong(K_SW_ACC, 0L)
        set(value) = p.edit().putLong(K_SW_ACC, value).apply()

    /** Lap millis, oldest first, comma separated (kept small on purpose). */
    var stopwatchLaps: String
        get() = p.getString(K_SW_LAPS, "") ?: ""
        set(value) = p.edit().putString(K_SW_LAPS, value).apply()

    /** Selected world-clock zone ids, in display order. */
    var worldClocks: List<String>
        get() = (p.getString(K_WORLD_CLOCKS, Cities.DEFAULT) ?: Cities.DEFAULT)
            .split(',')
            .filter { it.isNotBlank() }
        set(value) = p.edit().putString(K_WORLD_CLOCKS, value.joinToString(",")).apply()

    var lastTab: Int
        get() = p.getInt(K_LAST_TAB, 0)
        set(value) = p.edit().putInt(K_LAST_TAB, value).apply()

    companion object {
        private const val K_IMAWAKE_WINDOW = "im_awake_window"
        private const val K_DEFAULT_SNOOZE = "default_snooze"
        private const val K_NOTIF_RATIONALE = "notif_rationale_shown"
        private const val K_BATTERY_NUDGE = "battery_nudge_shown"
        private const val K_OVERLAY_NUDGE = "overlay_nudge_shown"
        private const val K_SNOOZE = "snooze_until_"
        private const val K_SW_RUNNING = "sw_running"
        private const val K_SW_STARTED = "sw_started_at"
        private const val K_SW_ACC = "sw_accumulated"
        private const val K_SW_LAPS = "sw_laps"
        private const val K_WORLD_CLOCKS = "world_clocks"
        private const val K_LAST_TAB = "last_tab"

        @Volatile
        private var instance: Prefs? = null

        /** Safe to call from any process start; uses device-protected storage. */
        fun init(context: Context) {
            if (instance == null) {
                synchronized(Prefs::class.java) {
                    if (instance == null) {
                        val de = context.createDeviceProtectedStorageContext()
                        instance = Prefs(de.getSharedPreferences("clock", Context.MODE_PRIVATE))
                    }
                }
            }
        }

        fun of(): Prefs = instance ?: error("Prefs.init() must run before use")
    }
}
