# Design decisions

Why this app looks the way it does. Every claim here was measured on the phone
(Bigme HiBreak, Android 14 / SDK 34, e-ink 824×1648 @ 300 dpi); the raw numbers are
in [TESTING.md](TESTING.md) and `clock-audit/`.

## 1. No AndroidX, no Compose, no coroutines, no libraries

The brief forbade all of it, and the measurements justify it: AppCompat plus
Material Components alone are ~12 k method references, i.e. a fifth of the single-dex
budget, and the stock app's own dex is at 94.5 % of that budget. Framework widgets
(`android.widget.Switch`, `TimePickerDialog`, `NumberPicker`, `ScrollView`) cover
everything this app needs, and `kotlin-stdlib` is the only dependency R8 has to keep.

Result: 232 KB of dex for the whole app, one dex, no native libraries, ~7.5 KB
`resources.arsc` because only English resources exist.

**Activities are plain `android.app.Activity`.** The tab shell groups four tabs in
one activity with hand-built view trees instead of fragments or four activities:
switching tabs is `removeAllViews` + `addView`, which renders 11 frames for six
switches (stock: 77 frames, 32 % janky).

## 2. Scheduling: one `setAlarmClock()` per alarm

`setAlarmClock(AlarmClockInfo(trigger, showIntent), operation)` does two things at
once: SystemUI shows the next alarm in the status bar / lock screen (the phone's
owners expect that, and the stock app has it), and the trigger is exempt from Doze.
`setExactAndAllowWhileIdle` gives neither, and `setRepeating` cannot express
"skip today for this one alarm".

Registered entries after a five-alarm setup (from `dumpsys alarm`):

```
Alarm clock blocks registered by com.ilyaas.einkclock: 5
   triggerTime= 2026-09-30 08:00:00.000
   triggerTime= 2026-09-30 08:05:00.000
   triggerTime= 2026-09-30 08:10:00.000
   triggerTime= 2026-09-30 08:20:00.000
   triggerTime= 2026-09-30 08:40:00.000
```

Timers use `setExactAndAllowWhileIdle` instead, so a running timer does not
masquerade as "the next alarm" in SystemUI.

Every trigger is computed as `ZonedDateTime.of(date, LocalTime, zone)` — never from
a fixed offset — so a spring-forward gap shifts forward (02:30 → 03:30) and a
fall-back ambiguity resolves to the earlier offset. Both are asserted by unit tests,
and the device confirms the fall-back case: at `2026-11-01 01:25 EDT` the registered
trigger epoch for 08:00 is `1793538000000` (= 08:00 **EST**), not `1793534400000`.

## 3. Skips are dates, not mask edits

`alarm_skip(alarm_id, date)` is the only place a dismissal is recorded. Some FOSS
alarm apps implement "skip today" by clearing today's weekday bit, which silently
loses the day for the *next* week too; this app never touches `repeat_mask` and never
sets `enabled = 0` for a repeating alarm. Consequences:

- "tomorrow is normal" is true by construction, not by careful undo logic;
- the alarm row keeps showing its real schedule while skipped (`skipped today` is
  appended to the summary line);
- rows older than a week are pruned on application start.

One-shot alarms are the exception in one direction: if a *victim* of "I'm awake" is a
one-shot, a skip row would disarm it forever, so it is disabled instead (and Undo
re-enables it).

## 4. "I'm awake" is a chain, not a window

The original brief specified "skip every enabled alarm whose next trigger is inside
now + 15 min". Real wake-up sets are bursts — 8:00, 8:05, 8:10, 8:20 — and a plain
window stops at the edge of the first hop. The product owner asked for the chained
reading, so:

```
cursor = now
repeat:
    limit = cursor + window
    batch = every enabled alarm (other than the ringing one) whose next occurrence
            is today and ≤ limit
    if batch is empty: stop
    skip(batch); cursor = max(batch times)
```

Consequences, all covered by unit tests: a 5-minute burst chains end to end; a
15-minute burst still chains with a 15-minute window; the chain stops at the first
gap larger than the window (so an 8:40 alarm after an 8:00–8:20 burst survives); and
two alarms at the same minute are both skipped because the batch is collected before
the cursor moves.

The window options are 5 / 10 / 15 minutes. There is no "no other alarms in the next
N min" message: if nothing chained, the screen simply dismisses.

Undo is a real state change, not an animation: the skipped ids are kept for 10
seconds, and Undo deletes today's `alarm_skip` rows and reschedules.

## 5. The ring flow, and the Android 14 discovery that shaped it

`AlarmReceiver` → `PowerManager` partial wake lock → `startForegroundService` →
`AlarmService` (foreground, `mediaPlayback`, `directBootAware`) which plays the
ringtone on `USAGE_ALARM`, vibrates, posts a high-importance notification with a
full-screen intent, and launches the ring screen. One ring at a time; a second alarm
queues behind the first.

`USE_FULL_SCREEN_INTENT` is **not enough** to show the ring screen when the phone is
unlocked and Clock is in the background. Android 14 refuses the activity start:

```
W ActivityTaskManager: Background activity launch blocked [callingPackage: com.ilyaas.einkclock;
  appSwitchState: 2; callingUidHasAnyVisibleWindow: false; callingUidProcState: FOREGROUND_SERVICE; ...]
E ActivityTaskManager: Abort background activity starts from 10281
```

With the display-over-other-apps appop granted, the same call succeeds and the log
reads `Displayed com.ilyaas.einkclock/.ui.AlarmRingActivity for user 0: +233ms`.
So the app declares `SYSTEM_ALERT_WINDOW`, nudges once on first run with a plain
explanation, exposes a status row + deep link in Settings, and `scripts/install.sh`
grants it over adb. Without it the app still rings: the full-screen intent covers
the locked/screen-off case, and the notification is the fallback.

## 6. Device-protected storage and direct boot

The database and preferences live in the device-protected context
(`createDeviceProtectedStorageContext()`), i.e. `/data/user_de/0/com.ilyaas.einkclock/`,
exactly like the stock app. The receiver and service are `directBootAware`, and
`BootReceiver` re-registers every enabled alarm on `BOOT_COMPLETED`,
`LOCKED_BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and `MY_PACKAGE_REPLACED`.
Verified: after `adb reboot`, six enabled alarms were re-registered without the app
being opened.

Note for anyone reading the audit instructions: because the database is
device-protected, `run-as <pkg> cat databases/clock.db` finds nothing — pull
`/data/user_de/0/<pkg>/databases/clock.db` instead (TESTING.md does).

**Platform quirk worth knowing:** a manual clock jump
(`adb shell cmd alarm set-time`) does not recompute already-registered alarms'
elapsed deadlines on this ROM, and `TIME_SET` is sent with
`FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT`, so a manifest receiver never sees it. A
multi-hour jump therefore leaves an alarm armed at its old elapsed deadline until the
app next reschedules (app launch, timezone change, boot). Network time updates are
small, so this does not affect normal use; it does affect the brief's T6 recipe, which
is why T6's device evidence is the registered trigger epoch rather than a ring.

## 7. E-ink UI rules

- Pure `#FFFFFF` / `#000000`; 50 % gray `#808080` marks disabled or secondary text.
  Theme accents, control tints and the dialog theme are all forced black so no
  Material teal leaks in.
- 1.5 dp black strokes and 8 dp radii drawn with `GradientDrawable` instead of
  elevation or `CardView`; buttons are `TextView`s with drawn backgrounds, because
  the framework `Button` brings a `StateListAnimator` and a ripple.
- No window animation anywhere: the theme nulls every `windowAnimationStyle` entry
  and every `startActivity` is followed by `overridePendingTransition(0, 0)`.
- No indeterminate progress: timer progress is a 4 dp black-on-gray bar computed
  from the remaining fraction.
- Repaints are event-driven and minute-aligned: the Clock tab ticks on the next
  minute boundary, the Timer tab at 1 Hz only while visible, the Stopwatch at 10 Hz
  only while running, and every handler is cancelled in `onHidden()`/`onPause()`.
  Measured: 0 frames in 30 s idle, 0–1 frames in 60 s (the minute tick).
- Redundant full-screen backgrounds were removed (tabs inherit the white window
  background) — on an e-ink panel a skipped full-screen fill is real time.

## 7b. Self-healing registration

`am force-stop` (and, on this ROM, a manual clock jump) can leave the alarm set
empty. Every process start therefore re-registers all enabled alarms 1.5 s after
`Application.onCreate` — off the first frame and off the main thread — so opening the
app always restores the true schedule. Measured: 0 registered while force-stopped, 6
after a single launch.

## 8. Startup work is deliberately deferred

Cold start was ~800 ms when the four tab view trees, the notification channels, the
skip-table prune and an `AlarmManager.nextAlarmClock()` round trip all happened
before the first frame. Now: tabs are built on first use, channels are created by the
service that posts them, the prune waits 3 s, and the next-alarm line lands 120 ms
after the first frame. That plus `windowDisablePreview` (which avoids a second
full-screen paint of the same white) brought the median to ~330 ms.

## 9. State that is not in SQLite, and why

- **Snooze** is a `SharedPreferences` timestamp plus a second `PendingIntent`
  (`RC_SNOOZE_BASE + alarmId`). It is an instance of an alarm, not an alarm, so it
  does not belong in a row that also holds the schedule; and a separate request code
  means the snooze and the regular next occurrence never overwrite each other.
- **Stopwatch** state (`running`, `startedAt`, `accumulated`) is in prefs so it
  survives process death. Laps are in memory only.
- **Settings** (I'm awake window, default snooze, world-clock selection, one-time
  nudge flags) are prefs.

## 10. Timers without a schema change

The brief's `timers` table has `state ∈ {idle, running, paused}`. An expired timer is
represented as `state = idle` with `remaining_ms = 0` and a non-zero `length_ms`, so
the "Timers up" screen can list exactly the timers that just finished without adding
a fourth state or a fifth table.

## 11. Deliberately not built

No analog clock, no seconds on the Clock tab, no splash/onboarding/wizard, no
analytics, no crash reporting, no update checker, no dark mode (e-ink has no backlight
to save), no "upcoming alarm" persistent notification (it would repaint for no
information gain), and no recycling list abstraction for fewer than twenty rows.

## 12. Where the numbers landed

Beaten: APK size (40× smaller), dex size (37×), `resources.arsc` (490×), native
libraries (1.7 MB → 0), tab-switch frames (77 → 11) and jank (32 % → 9 %), PSS
(58–65 MB → 49–59 MB), and cold start (median 376 ms → ~330 ms interleaved).

Missed, honestly: the ≤300 ms cold-start target (median 304 ms; the e-ink panel and
the framework's process/window setup dominate — Settings needs 445–640 ms in the same
conditions) and the ≤25 MB PSS target, which is below the floor of this ROM: a
minimalist text launcher measures 32.2 MB PSS here, Clock 47.8 MB, the stock alarm app
65.1 MB. The app itself is 47 views, one activity, no bitmaps and no web views.
