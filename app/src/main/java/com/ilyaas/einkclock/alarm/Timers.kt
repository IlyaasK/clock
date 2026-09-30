package com.ilyaas.einkclock.alarm

import android.content.Context
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.data.TimerRow
import com.ilyaas.einkclock.data.TimerState

/** Timer engine. All functions run on the io thread and persist every change. */
object Timers {

    /** A timer that reached zero stays visible until dismissed or extended. */
    fun isExpired(t: TimerRow, now: Long): Boolean =
        t.state == TimerState.IDLE && t.lengthMs > 0 && t.remainingMs <= 0L && remaining(t, now) <= 0L

    fun remaining(t: TimerRow, now: Long): Long = when (t.state) {
        TimerState.RUNNING -> ((t.endAt ?: now) - now)
        else -> t.remainingMs
    }.coerceAtLeast(0L)

    fun visible(all: List<TimerRow>, now: Long): List<TimerRow> =
        all.filter { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED || isExpired(it, now) }

    /** Create + start a timer. Returns its id. */
    fun create(context: Context, lengthMs: Long, label: String): Int {
        val len = lengthMs.coerceAtLeast(1_000L)
        val now = System.currentTimeMillis()
        val id = Repo.insertTimer(
            TimerRow(lengthMs = len, remainingMs = len, state = TimerState.RUNNING, endAt = now + len, label = label),
        )
        Scheduler.scheduleTimer(context, id, now + len)
        return id
    }

    fun pause(context: Context, id: Int) {
        val t = Repo.timer(id) ?: return
        val now = System.currentTimeMillis()
        Scheduler.cancelTimer(context, id)
        Repo.updateTimer(t.copy(state = TimerState.PAUSED, remainingMs = remaining(t, now), endAt = null))
    }

    fun resume(context: Context, id: Int) {
        val t = Repo.timer(id) ?: return
        val now = System.currentTimeMillis()
        val rem = remaining(t, now).coerceAtLeast(1_000L)
        Repo.updateTimer(t.copy(state = TimerState.RUNNING, remainingMs = rem, endAt = now + rem))
        Scheduler.scheduleTimer(context, id, now + rem)
    }

    /** Resume + 60 s, from paused, expired or running. */
    fun addMinute(context: Context, id: Int) {
        val t = Repo.timer(id) ?: return
        val now = System.currentTimeMillis()
        val base = if (remaining(t, now) <= 0L) 0L else remaining(t, now)
        val rem = base + 60_000L
        Repo.updateTimer(t.copy(state = TimerState.RUNNING, remainingMs = rem, endAt = now + rem))
        Scheduler.scheduleTimer(context, id, now + rem)
    }

    fun delete(context: Context, id: Int) {
        Scheduler.cancelTimer(context, id)
        Repo.deleteTimer(id)
    }

    fun rescheduleAll(context: Context) {
        for (t in Repo.timers()) {
            if (t.state == TimerState.RUNNING && t.endAt != null) Scheduler.scheduleTimer(context, t.id, t.endAt)
        }
    }
}
