package com.ilyaas.einkclock.alarm

import com.ilyaas.einkclock.data.Alarm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure scheduling math — no Android imports, so JVM unit tests can drive it with
 * an injected `from` instant and an injected skip lookup.
 *
 * All triggers are computed as ZonedDateTime in the device zone, never from a
 * fixed offset, so DST gaps and the repeated fall-back hour behave: a nonexistent
 * local time is shifted forward by the platform and an ambiguous one resolves to
 * the earlier offset.
 */
object NextTrigger {

    /** 7 days of schedule + slack for skipped days. */
    const val HORIZON_DAYS = 9

    fun next(
        alarm: Alarm,
        from: Instant,
        zone: ZoneId,
        isSkipped: (Int, LocalDate) -> Boolean = { _, _ -> false },
    ): ZonedDateTime? {
        if (!alarm.enabled) return null
        val time = LocalTime.of(alarm.hour, alarm.minute)
        val startDay = from.atZone(zone).toLocalDate()

        if (alarm.repeatMask == 0) {
            // One-shot: the next wall-clock occurrence of this time.
            val today = ZonedDateTime.of(startDay, time, zone)
            val day = if (today.toInstant().isAfter(from)) startDay else startDay.plusDays(1)
            return if (isSkipped(alarm.id, day)) null else ZonedDateTime.of(day, time, zone)
        }

        var day = startDay
        for (i in 0 until HORIZON_DAYS) {
            if (alarm.repeatsOn(day.dayOfWeek) && !isSkipped(alarm.id, day)) {
                val t = ZonedDateTime.of(day, time, zone)
                if (t.toInstant().isAfter(from)) return t
            }
            day = day.plusDays(1)
        }
        return null
    }

    /**
     * The "I'm awake" selector: a chained walk over the alarm set.
     *
     * Starting at [now], take the earliest occurrence that is within
     * [windowMinutes] of the *previous* one, skip it, and repeat. So a wake-up
     * burst of alarms every 5 (or 10, or 15) minutes chains away as one unit
     * instead of stopping after the first window, which is what "the rest of
     * these are the same wake-up" means in practice.
     *
     * Only today's occurrences are skipped; the alarm rows keep their repeat
     * masks, so tomorrow is normal by construction.
     */
    fun wakeCluster(
        alarms: List<Alarm>,
        now: Instant,
        zone: ZoneId,
        windowMinutes: Int,
        isSkipped: (Int, LocalDate) -> Boolean = { _, _ -> false },
        excludingId: Int = -1,
    ): List<Alarm> {
        val today = now.atZone(zone).toLocalDate()
        val windowSeconds = windowMinutes * 60L
        val remaining = alarms.filter { it.enabled && it.id != excludingId }.toMutableList()
        val out = ArrayList<Alarm>()
        var cursor: Instant = now

        while (true) {
            val limit = cursor.plusSeconds(windowSeconds)
            // Everything due inside this hop: duplicates at the same minute included.
            val batch = ArrayList<Pair<Alarm, Instant>>()
            for (a in remaining) {
                val t = next(a, cursor, zone, isSkipped) ?: continue
                if (t.toLocalDate() != today) continue
                val at = t.toInstant()
                if (!at.isAfter(limit)) batch.add(a to at)
            }
            if (batch.isEmpty()) break
            batch.forEach { out.add(it.first) }
            remaining.removeAll(batch.map { it.first })
            cursor = batch.maxOf { it.second }
        }
        return out
    }
}
