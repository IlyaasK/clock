package com.ilyaas.einkclock.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate

/**
 * All SQLite access. Every function here is synchronous and must be called from
 * [com.ilyaas.einkclock.util.Async.io] — SQLite never runs on the main thread.
 */
object Repo {

    @Volatile
    private var helper: ClockDb? = null

    fun init(context: Context) {
        if (helper == null) {
            synchronized(Repo::class.java) {
                if (helper == null) {
                    val h = ClockDb(context.createDeviceProtectedStorageContext())
                    h.setWriteAheadLoggingEnabled(true)
                    helper = h
                }
            }
        }
    }

    private fun db(): SQLiteDatabase = helper!!.writableDatabase

    // ------------------------------------------------------------------ alarms

    private const val ALARM_COLS =
        "id,hour,minute,label,enabled,repeat_mask,vibrate,ringtone,snooze_minutes,fired_at"

    private fun readAlarm(c: android.database.Cursor): Alarm = Alarm(
        id = c.getInt(0),
        hour = c.getInt(1),
        minute = c.getInt(2),
        label = c.getString(3) ?: "",
        enabled = c.getInt(4) != 0,
        repeatMask = c.getInt(5),
        vibrate = c.getInt(6) != 0,
        ringtone = c.getString(7),
        snoozeMinutes = c.getInt(8),
        firedAt = if (c.isNull(9)) null else c.getLong(9),
    )

    fun alarms(): List<Alarm> {
        val out = ArrayList<Alarm>()
        db().rawQuery("SELECT $ALARM_COLS FROM alarms ORDER BY hour, minute, id", null).use { c ->
            while (c.moveToNext()) out.add(readAlarm(c))
        }
        return out
    }

    fun enabledAlarms(): List<Alarm> = alarms().filter { it.enabled }

    fun alarm(id: Int): Alarm? {
        db().rawQuery("SELECT $ALARM_COLS FROM alarms WHERE id = ?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) readAlarm(c) else null
        }
    }

    fun insertAlarm(a: Alarm): Int {
        val cv = ContentValues().apply {
            put("hour", a.hour)
            put("minute", a.minute)
            put("label", a.label)
            put("enabled", if (a.enabled) 1 else 0)
            put("repeat_mask", a.repeatMask)
            put("vibrate", if (a.vibrate) 1 else 0)
            put("ringtone", a.ringtone)
            put("snooze_minutes", a.snoozeMinutes)
            put("fired_at", a.firedAt)
        }
        return db().insert("alarms", null, cv).toInt()
    }

    fun updateAlarm(a: Alarm) {
        val cv = ContentValues().apply {
            put("hour", a.hour)
            put("minute", a.minute)
            put("label", a.label)
            put("enabled", if (a.enabled) 1 else 0)
            put("repeat_mask", a.repeatMask)
            put("vibrate", if (a.vibrate) 1 else 0)
            put("ringtone", a.ringtone)
            put("snooze_minutes", a.snoozeMinutes)
            put("fired_at", a.firedAt)
        }
        db().update("alarms", cv, "id = ?", arrayOf(a.id.toString()))
    }

    fun deleteAlarm(id: Int) {
        db().delete("alarms", "id = ?", arrayOf(id.toString()))
        db().delete("alarm_skip", "alarm_id = ?", arrayOf(id.toString()))
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        val cv = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        db().update("alarms", cv, "id = ?", arrayOf(id.toString()))
    }

    fun setFiredAt(id: Int, at: Long?) {
        val cv = ContentValues().apply { put("fired_at", at) }
        db().update("alarms", cv, "id = ?", arrayOf(id.toString()))
    }

    // ------------------------------------------------------------------- skips

    /** alarm id -> set of ISO local dates (yyyy-MM-dd) skipped. */
    fun skipsByAlarm(): Map<Int, MutableSet<String>> {
        val out = HashMap<Int, MutableSet<String>>()
        db().rawQuery("SELECT alarm_id, date FROM alarm_skip", null).use { c ->
            while (c.moveToNext()) {
                out.getOrPut(c.getInt(0)) { HashSet() }.add(c.getString(1))
            }
        }
        return out
    }

    fun addSkip(alarmId: Int, date: LocalDate) {
        val cv = ContentValues().apply {
            put("alarm_id", alarmId)
            put("date", date.toString())
        }
        db().insertWithOnConflict("alarm_skip", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeSkips(alarmIds: Collection<Int>, date: LocalDate) {
        if (alarmIds.isEmpty()) return
        val args = ArrayList<String>(alarmIds.size + 1)
        args.add(date.toString())
        alarmIds.forEach { args.add(it.toString()) }
        val placeholders = alarmIds.joinToString(",") { "?" }
        db().delete("alarm_skip", "date = ? AND alarm_id IN ($placeholders)", args.toTypedArray())
    }

    fun pruneSkips(before: LocalDate) {
        db().delete("alarm_skip", "date < ?", arrayOf(before.toString()))
    }

    // ------------------------------------------------------------------ timers

    private const val TIMER_COLS = "id,length_ms,remaining_ms,state,end_at,label"

    private fun readTimer(c: android.database.Cursor): TimerRow = TimerRow(
        id = c.getInt(0),
        lengthMs = c.getLong(1),
        remainingMs = c.getLong(2),
        state = c.getInt(3),
        endAt = if (c.isNull(4)) null else c.getLong(4),
        label = c.getString(5) ?: "",
    )

    fun timers(): List<TimerRow> {
        val out = ArrayList<TimerRow>()
        db().rawQuery("SELECT $TIMER_COLS FROM timers ORDER BY id", null).use { c ->
            while (c.moveToNext()) out.add(readTimer(c))
        }
        return out
    }

    fun timer(id: Int): TimerRow? {
        db().rawQuery("SELECT $TIMER_COLS FROM timers WHERE id = ?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) readTimer(c) else null
        }
    }

    fun insertTimer(t: TimerRow): Int {
        val cv = ContentValues().apply {
            put("length_ms", t.lengthMs)
            put("remaining_ms", t.remainingMs)
            put("state", t.state)
            put("end_at", t.endAt)
            put("label", t.label)
        }
        return db().insert("timers", null, cv).toInt()
    }

    fun updateTimer(t: TimerRow) {
        val cv = ContentValues().apply {
            put("length_ms", t.lengthMs)
            put("remaining_ms", t.remainingMs)
            put("state", t.state)
            put("end_at", t.endAt)
            put("label", t.label)
        }
        db().update("timers", cv, "id = ?", arrayOf(t.id.toString()))
    }

    fun deleteTimer(id: Int) {
        db().delete("timers", "id = ?", arrayOf(id.toString()))
    }
}
