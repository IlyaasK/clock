package com.ilyaas.einkclock.data

import java.time.DayOfWeek

/** Sentinel stored in `alarms.ringtone` meaning "no sound at all". */
const val RINGTONE_SILENT = "silent"

/**
 * Repeat schedule bits. ISO weekday order: bit0 = Monday … bit6 = Sunday.
 * A mask of 0 means "once" (a one-shot alarm).
 */
object Days {
    const val MON = 1 shl 0
    const val TUE = 1 shl 1
    const val WED = 1 shl 2
    const val THU = 1 shl 3
    const val FRI = 1 shl 4
    const val SAT = 1 shl 5
    const val SUN = 1 shl 6
    const val WEEKDAYS = MON or TUE or WED or THU or FRI
    const val WEEKEND = SAT or SUN
    const val ALL = MON or TUE or WED or THU or FRI or SAT or SUN

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    /** Monday-first list of the selected days, for the repeat summary. */
    fun selected(dayValues: List<DayOfWeek>, mask: Int): List<DayOfWeek> =
        dayValues.filter { (mask and bit(it)) != 0 }
}

data class Alarm(
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatMask: Int = 0,
    val vibrate: Boolean = true,
    val ringtone: String? = null,
    val snoozeMinutes: Int = 10,
    val firedAt: Long? = null,
) {
    val isRepeating: Boolean get() = repeatMask != 0

    fun repeatsOn(day: DayOfWeek): Boolean = (repeatMask and Days.bit(day)) != 0
}

object TimerState {
    const val IDLE = 0
    const val RUNNING = 1
    const val PAUSED = 2
}

data class TimerRow(
    val id: Int = 0,
    val lengthMs: Long,
    val remainingMs: Long,
    val state: Int,
    val endAt: Long?,
    val label: String = "",
)
