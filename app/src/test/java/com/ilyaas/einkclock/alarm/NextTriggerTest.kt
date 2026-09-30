package com.ilyaas.einkclock.alarm

import com.ilyaas.einkclock.data.Alarm
import com.ilyaas.einkclock.data.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * JVM unit tests for the scheduling math and the "I'm awake" selector. No device,
 * no Android framework: every case injects its own instant, zone and skip lookup.
 */
class NextTriggerTest {

    private val ny: ZoneId = ZoneId.of("America/New_York")

    private fun instant(date: String, time: String, zone: ZoneId = ny): Instant =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant()

    private fun alarm(
        id: Int = 1,
        hour: Int,
        minute: Int,
        mask: Int = Days.ALL,
        enabled: Boolean = true,
    ) = Alarm(id = id, hour = hour, minute = minute, repeatMask = mask, enabled = enabled)

    @Test
    fun `daily alarm later today is today`() {
        val now = instant("2026-09-30", "07:00")
        val next = NextTrigger.next(alarm(hour = 8, minute = 0), now, ny)
        assertNotNull(next)
        assertEquals(LocalDate.parse("2026-09-30"), next!!.toLocalDate())
        assertEquals(LocalTime.parse("08:00"), next.toLocalTime())
    }

    @Test
    fun `daily alarm earlier today rolls to tomorrow`() {
        val now = instant("2026-09-30", "09:00")
        val next = NextTrigger.next(alarm(hour = 8, minute = 0), now, ny)!!
        assertEquals(LocalDate.parse("2026-10-01"), next.toLocalDate())
    }

    @Test
    fun `weekday alarm on friday evening rolls to monday`() {
        val now = instant("2026-10-02", "20:00") // Friday
        val next = NextTrigger.next(alarm(hour = 8, minute = 0, mask = Days.WEEKDAYS), now, ny)!!
        assertEquals(LocalDate.parse("2026-10-05"), next.toLocalDate()) // Monday
    }

    @Test
    fun `skip row for today pushes the alarm to the next repeat day`() {
        val now = instant("2026-09-30", "07:00")
        val today = LocalDate.parse("2026-09-30")
        val next = NextTrigger.next(alarm(hour = 8, minute = 0), now, ny) { _, day -> day == today }!!
        assertEquals(LocalDate.parse("2026-10-01"), next.toLocalDate())
    }

    @Test
    fun `a skip row from last week has no effect today`() {
        val now = instant("2026-09-30", "07:00")
        val lastWeek = LocalDate.parse("2026-09-23")
        val next = NextTrigger.next(alarm(hour = 8, minute = 0), now, ny) { _, day -> day == lastWeek }!!
        assertEquals(LocalDate.parse("2026-09-30"), next.toLocalDate())
    }

    @Test
    fun `one-shot alarm later today fires today`() {
        val now = instant("2026-09-30", "07:00")
        val next = NextTrigger.next(alarm(hour = 8, minute = 0, mask = 0), now, ny)!!
        assertEquals(LocalDate.parse("2026-09-30"), next.toLocalDate())
    }

    @Test
    fun `one-shot alarm already past fires tomorrow`() {
        val now = instant("2026-09-30", "09:00")
        val next = NextTrigger.next(alarm(hour = 8, minute = 0, mask = 0), now, ny)!!
        assertEquals(LocalDate.parse("2026-10-01"), next.toLocalDate())
    }

    @Test
    fun `disabled alarm has no trigger`() {
        assertNull(NextTrigger.next(alarm(hour = 8, minute = 0, enabled = false), instant("2026-09-30", "07:00"), ny))
    }

    // ------------------------------------------------------------ I'm awake window

    @Test
    fun `a five minute burst from 8-00 to 8-30 is dismissed as one unit`() {
        // The user's case: seven alarms every 5 minutes, all part of one wake-up.
        val now = instant("2026-09-30", "08:00")
        val burst = (0..6).map { i -> alarm(id = 10 + i, hour = 8, minute = i * 5) }
        val selected = NextTrigger.wakeCluster(burst, now, ny, windowMinutes = 15, excludingId = 10)
        assertEquals(listOf(11, 12, 13, 14, 15, 16), selected.map { it.id })
    }

    @Test
    fun `a fifteen minute burst chains with a fifteen minute window`() {
        val now = instant("2026-09-30", "08:00")
        val burst = (0..3).map { i -> alarm(id = 20 + i, hour = 8, minute = i * 15) }
        val selected = NextTrigger.wakeCluster(burst, now, ny, windowMinutes = 15, excludingId = 20)
        assertEquals(listOf(21, 22, 23), selected.map { it.id })
    }

    @Test
    fun `the chain breaks at the first gap larger than the window`() {
        val now = instant("2026-09-30", "08:00")
        val alarms = listOf(
            alarm(id = 30, hour = 8, minute = 0), // ringing
            alarm(id = 31, hour = 8, minute = 5),
            alarm(id = 32, hour = 8, minute = 10),
            alarm(id = 33, hour = 8, minute = 40), // 30 min after 8:10 -> chain ends
            alarm(id = 34, hour = 8, minute = 45),
        )
        val selected = NextTrigger.wakeCluster(alarms, now, ny, windowMinutes = 15, excludingId = 30)
        assertEquals(listOf(31, 32), selected.map { it.id })
    }

    @Test
    fun `in-window alarms today are selected, out-of-window and tomorrow are not`() {
        val now = instant("2026-09-30", "08:00")
        val alarms = listOf(
            alarm(id = 1, hour = 8, minute = 0), // the ringing one, excluded
            alarm(id = 2, hour = 8, minute = 5), // +5 min -> skipped
            alarm(id = 3, hour = 8, minute = 10), // +10 min -> skipped
            alarm(id = 4, hour = 8, minute = 20), // +20 min -> NOT skipped
            alarm(id = 5, hour = 8, minute = 40), // +40 min -> NOT skipped
            alarm(id = 6, hour = 7, minute = 30), // already past -> next is tomorrow
        )
        val selected = NextTrigger.wakeCluster(alarms, now, ny, windowMinutes = 15, excludingId = 1)
        // 8:05 and 8:10 are inside the first hop; 8:10 + 15 min reaches 8:20, so it
        // chains too. 8:40 is beyond 8:20 + 15 min, so the burst ends there.
        assertEquals(listOf(2, 3, 4), selected.map { it.id })
    }

    @Test
    fun `the window boundary itself is included`() {
        val now = instant("2026-09-30", "08:00")
        val alarms = listOf(
            alarm(id = 2, hour = 8, minute = 15), // exactly now + 15 min
            alarm(id = 3, hour = 8, minute = 31), // past 8:15 + 15 min -> separate alarm
        )
        val selected = NextTrigger.wakeCluster(alarms, now, ny, windowMinutes = 15)
        assertEquals(listOf(2), selected.map { it.id })
    }

    @Test
    fun `duplicate alarms at the same time are all selected`() {
        val now = instant("2026-09-30", "08:00")
        val alarms = listOf(
            alarm(id = 2, hour = 8, minute = 5),
            alarm(id = 3, hour = 8, minute = 5),
        )
        assertEquals(listOf(2, 3), NextTrigger.wakeCluster(alarms, now, ny, 15).map { it.id })
    }

    @Test
    fun `a skipped alarm is not selected twice`() {
        val now = instant("2026-09-30", "08:00")
        val today = LocalDate.parse("2026-09-30")
        val alarms = listOf(alarm(id = 2, hour = 8, minute = 5))
        val selected = NextTrigger.wakeCluster(
            alarms, now, ny, 15,
            isSkipped = { _, day -> day == today },
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `the chain is measured from now first, then from each skipped alarm`() {
        // Pressed 12 minutes late: 8:05 and 8:10 are in the past, 8:20 is 3 minutes out.
        val now = instant("2026-09-30", "08:17")
        val alarms = listOf(
            alarm(id = 2, hour = 8, minute = 5),
            alarm(id = 3, hour = 8, minute = 20),
        )
        assertEquals(listOf(3), NextTrigger.wakeCluster(alarms, now, ny, 15).map { it.id })
    }

    // ------------------------------------------------------------------------ DST

    @Test
    fun `spring forward - a nonexistent local time is shifted forward and never dropped`() {
        // 2026-03-08 02:00 America/New_York jumps to 03:00: 02:30 does not exist.
        val zone = ny
        val day = LocalDate.parse("2026-03-08")
        val resolved = ZonedDateTime.of(day, LocalTime.of(2, 30), zone)
        assertEquals(LocalTime.parse("03:30"), resolved.toLocalTime())

        val now = instant("2026-03-08", "01:00")
        val next = NextTrigger.next(alarm(hour = 2, minute = 30), now, zone)
        assertNotNull(next)
        assertEquals(day, next!!.toLocalDate())
        assertTrue(next.toInstant().isAfter(now))
    }

    @Test
    fun `spring forward - an alarm after the gap still lands on the wall clock`() {
        val now = instant("2026-03-08", "01:00")
        val next = NextTrigger.next(alarm(hour = 8, minute = 0), now, ny)!!
        assertEquals(LocalDate.parse("2026-03-08"), next.toLocalDate())
        assertEquals(LocalTime.parse("08:00"), next.toLocalTime())
        // 01:00 EST = 06:00Z, 08:00 EDT = 12:00Z: six real hours across the 23-hour day.
        assertEquals(Duration.ofHours(6).toMillis(), Duration.between(now, next.toInstant()).toMillis())
    }

    @Test
    fun `fall back - an ambiguous local time resolves to the earlier offset and still fires`() {
        // 2026-11-01 01:00-02:00 occurs twice; 01:30 is ambiguous.
        val day = LocalDate.parse("2026-11-01")
        val resolved = ZonedDateTime.of(day, LocalTime.of(1, 30), ny)
        assertEquals("-04:00", resolved.offset.id)

        val now = instant("2026-11-01", "01:25")
        val next = NextTrigger.next(alarm(hour = 1, minute = 30), now, ny)
        assertNotNull(next)
        assertEquals(day, next!!.toLocalDate())
        // Five real minutes later, inside the repeated hour.
        assertEquals(5L * 60_000L, Duration.between(now, next.toInstant()).toMillis())
    }

    @Test
    fun `fall back - an alarm at 08-00 is 1h35m away from 01-25 EDT, not 35m`() {
        val now = instant("2026-11-01", "01:25")
        val next = NextTrigger.next(alarm(hour = 8, minute = 0), now, ny)!!
        val minutes = Duration.between(now, next.toInstant()).toMinutes()
        // 01:25 EDT -> 02:00 EDT (35m), the repeated 01:00-02:00 EST (60m), then 6h to 08:00 EST.
        assertEquals(35L + 60L + 360L, minutes)
    }

    @Test
    fun `timezone change recomputes the trigger to the new local wall clock`() {
        val chicago = ZoneId.of("America/Chicago")
        val now = instant("2026-09-30", "06:00", chicago)
        val next = NextTrigger.next(alarm(hour = 8, minute = 0), now, chicago)!!
        assertEquals(LocalTime.parse("08:00"), next.toLocalTime())
        assertEquals(chicago, next.zone)
    }
}
