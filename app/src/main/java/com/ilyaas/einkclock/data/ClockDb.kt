package com.ilyaas.einkclock.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Framework SQLiteOpenHelper, opened on a device-protected context so alarms
 * are readable before the first unlock after a reboot (the stock app does the
 * same: its dataDir is /data/user_de/0/…).
 */
class ClockDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE alarms(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              hour INTEGER NOT NULL,
              minute INTEGER NOT NULL,
              label TEXT NOT NULL DEFAULT '',
              enabled INTEGER NOT NULL DEFAULT 1,
              repeat_mask INTEGER NOT NULL DEFAULT 0,
              vibrate INTEGER NOT NULL DEFAULT 1,
              ringtone TEXT,
              snooze_minutes INTEGER NOT NULL DEFAULT 10,
              fired_at INTEGER
            )
            """.trimIndent(),
        )
        // "skip this occurrence, for this alarm, on this date" — never mutates
        // repeat_mask or enabled, so tomorrow is normal by construction.
        db.execSQL(
            """
            CREATE TABLE alarm_skip(
              alarm_id INTEGER NOT NULL,
              date TEXT NOT NULL,
              PRIMARY KEY(alarm_id, date)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE timers(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              length_ms INTEGER NOT NULL,
              remaining_ms INTEGER NOT NULL,
              state INTEGER NOT NULL,
              end_at INTEGER,
              label TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_alarm_skip_date ON alarm_skip(date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 only. Nothing to migrate yet.
    }

    companion object {
        const val NAME = "clock.db"
        const val VERSION = 1
    }
}
