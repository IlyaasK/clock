package com.ilyaas.einkclock.util

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All user-visible number/date formatting. Locale is fixed: resources are en-only. */
object Fmt {

    private val H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val H12 = DateTimeFormatter.ofPattern("h:mm", Locale.US)
    private val H12_AMPM = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val H24_AMPM = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DATE_LONG = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val DAY_TIME = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
    private val DAY_TIME_24 = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)

    fun is24Hour(context: Context): Boolean = DateFormat.is24HourFormat(context)

    /** Big alarm-row time. */
    fun alarmTime(context: Context, hour: Int, minute: Int): String {
        val t = LocalTime.of(hour, minute)
        return if (is24Hour(context)) t.format(H24) else t.format(H12_AMPM)
    }

    /** Compact time used inside summary strings ("8:10 AM"). */
    fun clock(context: Context, hour: Int, minute: Int): String {
        val t = LocalTime.of(hour, minute)
        return if (is24Hour(context)) t.format(H24_AMPM) else t.format(H12_AMPM)
    }

    fun clock(context: Context, instant: Instant, zone: ZoneId): String {
        val t = instant.atZone(zone).toLocalTime()
        return if (is24Hour(context)) t.format(H24_AMPM) else t.format(H12_AMPM)
    }

    /** "17:55" / "5:55 PM" in an arbitrary zone (world clocks). */
    fun clockInZone(context: Context, zone: ZoneId): String {
        val t = java.time.ZonedDateTime.now(zone).toLocalTime()
        return if (is24Hour(context)) t.format(H24_AMPM) else t.format(H12_AMPM)
    }

    /** "GMT+02:00" for the current instant. */
    fun offsetLabel(zone: ZoneId): String {
        val offset = zone.rules.getOffset(Instant.now())
        return "GMT" + offset.id.replace("Z", "+00:00")
    }

    /** "Wed, Sep 30" */
    fun dateLong(date: LocalDate): String = date.format(DATE_LONG)

    /** "Thu 8:00 AM" — the next-alarm line. */
    fun dayTime(context: Context, instant: Instant, zone: ZoneId): String {
        val z = instant.atZone(zone)
        return z.format(if (is24Hour(context)) DAY_TIME_24 else DAY_TIME)
    }

    /** HH:MM:SS (timers). */
    fun hms(millis: Long): String {
        val total = (millis.coerceAtLeast(0L)) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    /** MM:SS.hh (stopwatch), growing to H:MM:SS.hh past an hour. */
    fun stopwatch(millis: Long): String {
        val hundredths = (millis.coerceAtLeast(0L)) / 10
        val h = hundredths / 360000
        val m = (hundredths / 6000) % 60
        val s = (hundredths / 100) % 60
        val hs = hundredths % 100
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, hs)
        } else {
            String.format(Locale.US, "%02d:%02d.%02d", m, s, hs)
        }
    }

    /** "5 min" / "1 h 5 min" for duration chrome. */
    fun duration(millis: Long): String {
        val totalMin = millis / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m)
        else String.format(Locale.US, "%d min", m)
    }
}
