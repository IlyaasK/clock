# Build "Clock" — a debloated, e-ink-first alarm app for the Bigme HiBreak Pro

You are the coding agent. This document is your complete brief: there is no prior conversation to read. You have Android Studio + a working Android SDK, and `adb` access to the target phone. Build the app, install it on the phone, and prove it works with pasted command output.

Repo path to create: wherever you keep projects — suggested name `eink-clock`. No existing code to integrate with; this is greenfield.

---

## 1. The device

| | |
|---|---|
| Model | Bigme HiBreak Pro (`ro.product.model=HiBreak`), serial `B651CNW2GCBB006000438` |
| OS | Android 14, `ro.build.version.sdk=34` |
| CPU | arm64-v8a |
| Display | **e-ink**, 824×1648 px, 300 dpi, `wm size` 824x1648 / `wm density` 300 |
| Launcher | Olauncher (`app.olaunchercf`) — minimalist, text-based |
| Timezone at audit | America/New_York |
| Animation scales | already 0.0 globally (`window_animation_scale`, `transition_animation_scale`, `animator_duration_scale`) |
| Vendor EPD quirk | `vendor.xrz.disable_eink_vsync=1` |

The display is the whole point: **e-ink panels refresh slowly and ghost**. Anything that animates, ripples, or repaints continuously is a defect on this device, not a nicety.

---

## 2. Why we're replacing the stock app (measured, not assumed)

The phone ships an OEM-forked AOSP DeskClock as `com.android.deskclock` (`/system/app/ClockProd/ClockProd.apk`).

Reference artifacts you can inspect (ask the user for the `clock-audit/` folder; if you have it, use it):

| File | What it is |
|---|---|
| `clock-audit/FINDINGS.md` | full audit: all measurements, manifest analysis, scheduling forensics |
| `clock-audit/ClockProd.apk` | the pulled stock APK (sha256 `4e754d4732bbecfbfe8438234921f6b7bb47bc0b180717689167041a319931fa`) |
| `clock-audit/manifest.xml` | stock manifest, decoded |
| `clock-audit/screen-clock.png`, `clock-audit/screen-alarm.png` | stock UI on the e-ink panel — the look to match |
| `clock-audit/dumpsys-alarm.txt` | how the stock app schedules alarms |
| `foss-android-alarm-eink-research.md` | survey proving no FOSS app has the feature we need |

**Stock facts:**
- 10.2 MB APK. Breakdown: `classes.dex` 8.7 MB, `resources.arsc` 3.7 MB, native libs 1.7 MB, `res/` 1.3 MB.
- **Single dex with 61,943 method references = 94.5 % of the 64 K limit** — effectively unfixable/unmodifiable.
- ~85 locales in the string table; `libmmkv.so` shipped for **4 ABIs** when only arm64 is usable (1.3 MB of pure waste); Tencent MMKV + a vendor `xrz.framework` SDK + an exported no-permission `AlarmProvider` that does nothing useful.
- **Java + Android Views** (AppCompat 4.1 k methods, Material Components 7.7 k), **zero Compose**, targetSdk 30 on an Android 14 device.
- 57.8 MB PSS / 178 MB RSS.
- Cold start 300–580 ms depending on load; frame times median 44 ms / p90 81 ms during tab switches (a trivial launcher on the same device: 29/57 ms). Idle on the Clock tab: 0 frames in 30 s (good — do not regress this).
- It cannot be patched or replaced in place: platform-signed, `sharedUserId="android.uid.system"`, and there is no root. It **can** be disabled cleanly (verified): `pm disable-user --user 0 com.android.deskclock`, reversible with `pm enable`.

**Targets for your build:** APK ≤ 600 KB, one dex, **zero** native libs, English-only resources, PSS ≤ 25 MB, cold start ≤ 300 ms, 0 frames rendered while idle.

---

## 3. Non-negotiable constraints

1. **Kotlin + classic Android Views.** No Jetpack Compose. No Jetpack anything.
2. **No AndroidX dependencies at all.** No AppCompat, no Material Components, no RecyclerView, no ConstraintLayout, no Lifecycle, no coroutines. Use `android.app.Activity`, `android.widget.*`, `android.app.Fragment` (or plain views), `SQLiteOpenHelper`, `android.os.Handler`. The only dependency is `kotlin-stdlib` (bundled by R8).
   - Theme: `android:Theme.Material.Light.NoActionBar` and friends (framework themes, API 21+).
   - Rows in a list: build them in code into a `LinearLayout` inside a `ScrollView`, or use a framework `ListView`. You will have < 20 alarms; do not build a recycling abstraction.
3. **No third-party libraries.** Not even a logging one.
4. **No animation, ever:** no `ObjectAnimator`, no `ViewPropertyAnimator`, no ripple (`android:background="?android:attr/selectableItemBackground"` is banned), no `ActivityOptions` transitions, no `overridePendingTransition` (call `overridePendingTransition(0,0)`), no Material elevation/shadows, no `RecyclerView` item animators.
5. **Monochrome only.** Pure `#FFFFFF` background, pure `#000000` foreground. Disabled/inactive states may use a 50 % gray (`#808080`). No accent colors, no dynamic color, no dark theme in v1.
6. `minSdk 34`, `targetSdk 34`, `compileSdk 35` (the phone is Android 14; targeting exactly its platform removes every legacy branch). If you want to lower `minSdk` to 29, gate the API-31+ exact-alarm and API-33+ notification-permission paths — but there is no reason to.
7. `resourceConfigurations += listOf("en")`, `vectorDrawables.useSupportLibrary = false`, `isMinifyEnabled = true`, `isShrinkResources = true`.
8. No native code, no `jniLibs`, no APK splits (single universal APK).
9. Package `com.ilyaas.einkclock`, app label `Clock`, single Gradle module `app`, Kotlin 2.x, AGP 8.x.
10. Every UI string in `strings.xml` with an English default. No hardcoded user-facing strings.

---

## 4. Functional spec

Four bottom tabs, matching the stock app's structure: **CLOCK · ALARM · STOPWATCH · TIMER** (clock first, as on the phone). Text-only tab bar: 4 equal columns, uppercase label, ~14sp, selected = black text + 2 dp top border, unselected = 50 % gray. No icons required; no selection animation.

### 4.1 Alarm tab

Layout per row (copy the stock look — see `screen-alarm.png`):
- A card: 8 dp rounded border, 1.5 dp pure black stroke, white fill, ~12 dp inner padding, ~8 dp gap between cards.
- Left column: the time as **HH:MM at ~56 sp** (24-hour or 12-hour per system setting; read `DateFormat.is24HourFormat`). If the alarm is disabled, render it at 50 % gray.
- Under the time, a ~15 sp summary line: the label (if any) plus the repeat summary — `Every day`, `Once`, `Mon, Tue, Wed, Thu, Fri`, `Weekdays`, `Weekends`.
- Right: a framework `android.widget.Switch`, vertically centered, ~48 dp touch target. Toggling must update the DB and reschedule immediately.
- Tap the row → edit screen. Long-press → a `AlertDialog` with Edit / Delete.
- Top bar: title `Alarm` on the left; on the right two 48 dp buttons drawn as plain text/vector: **+** (new alarm) and a gear (settings). No toolbar widget needed — a plain horizontal `LinearLayout`.
- Empty state: one centered line, `No alarms`.

### 4.2 Alarm edit screen

- Big time display at the top (tap it or the +/- steppers → a framework `TimePicker` in `AlertDialog`, or a full-screen `TimePicker`).
- Label text field (`EditText`, hint `Label`).
- Repeat: seven day toggles (`Mon`…`Sun`) as 44 dp square toggle buttons, black fill = selected. Plus quick actions: `Every day`, `Weekdays`, `Weekends`, `Once`.
- Snooze duration: 5 / 10 / 15 / 20 / 30 minutes (default 10).
- Vibrate: on/off (default on).
- Ringtone: default = system alarm ringtone (`RingtoneManager.getDefaultUri(TYPE_ALARM)`); a `Choose` button opening `ACTION_RINGTONE_PICKER`. Stretch goal — if it costs more than ~40 lines, ship with the default ringtone plus a `Silent` option and note it.
- Actions: `Save` (black filled, full width), `Delete` (outline, full width, below) when editing an existing alarm.
- Deleting must cancel its pending alarm and remove its skip rows.

### 4.3 Ring screen — **the centerpiece**

Full-screen activity, `launchMode="singleInstance"`, `excludeFromRecents="true"`, `taskAffinity=""`, `android:showWhenLocked="true"`, `android:turnScreenOn="true"`, plus `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` while ringing. Show over the keyguard; do not require unlocking.

Layout, top to bottom, centered:
1. Current time, **~96 sp**, no seconds.
2. The alarm's label (or `Alarm`) at ~28 sp.
3. The alarm's scheduled time at ~20 sp (e.g. `8:00 AM`), 50 % gray.
4. If snoozed, a line `Snoozed · rings again at 8:10 AM`.
5. **`I'm awake`** — a full-width, ~72 dp tall, black-filled button, white ~24 sp text.
6. Below it, a row of two half-width (~64 dp tall) outline buttons: **`Pause`** (left) and **`Cancel`** (right), ~20 sp.

Semantics — these three buttons are the product:

- **`Cancel`** = dismiss *this instance only*. Stop sound/vibration, cancel the notification, and:
  - repeating alarm → delete today's instance only; the alarm row stays enabled and fires again on its next scheduled day;
  - one-shot alarm → mark it disabled (keep the row, so it can be re-enabled by hand).
- **`Pause`** = snooze *this alarm* (the user explicitly chose this over "pause everything today"). Cancel the current ring, schedule a one-off exact alarm `now + snoozeMinutes`, and mark the instance as snoozed so the ring screen shows the re-ring time.
- **`I'm awake`** = dismiss this instance **and** skip every *other* enabled alarm whose next trigger falls inside the window, **for today only**. See §6.2 for the exact algorithm.

Also: hardware back and volume keys must not silently dismiss. Back = same as `Cancel` is acceptable only if you also show nothing else; better: back does nothing (the buttons are the only exit). Swallow `onBackPressed`.

### 4.4 Clock tab

- Big digital time, **~120 sp**, centered, upper third. **HH:MM only — no seconds** (a per-second repaint is exactly the e-ink defect we're removing; the stock app renders 0 frames while idle and you must match that).
- Date line below: `Wed, Sep 30` at ~20 sp.
- Next alarm line: `Alarm · Thu 8:00 AM` (update on `android.app.action.NEXT_ALARM_CLOCK_CHANGED` and a 60-second-aligned `Handler` tick — nothing faster).
- Optional, only if cheap: two or three world clock rows (city, offset, time). The stock app shows Berlin / Mumbai / Auckland. Mark this as P2 — ship without it if it costs more than a trivial amount.
- No analog clock. No seconds hand. Ever.

### 4.5 Stopwatch tab

- Big elapsed display `MM:SS.hh` (hundredths), ~72 sp monospace-ish (use `Typeface.MONOSPACE` so digits don't jitter).
- Buttons: `Start`/`Stop` (black filled), `Lap`, `Reset` (outline). Reset only enabled when stopped.
- Lap list below: `Lap 3 · 00:12.44 · total 01:03.10`, newest first, plain rows, no dividers animation.
- **Refresh discipline:** while running, update the display at most ~10× per second; while stopped, do not tick at all. Persist `startedAtMillis` + `accumulatedMillis` so the stopwatch survives process death.

### 4.6 Timer tab

- Support **N simultaneous timers** (like the stock app). Each timer row: remaining `HH:MM:SS` (~48 sp), optional label, a progress indicator (a plain horizontal 4 dp bar of black on light gray — no animated spinner).
- `+` → add timer: a duration picker (framework `TimePicker` in `TimerPickerDialog` mode, or hour/minute/second number fields), plus preset chips `1m · 5m · 10m · 15m · 30m · 1h`.
- Each timer row: `Pause`/`Resume`, `+1 min`, `Delete`.
- When a timer expires: sound + vibrate + a notification with a full-screen intent, and a `Timers up` activity with `Dismiss` and `+1 min` buttons (reuse the ring screen's chrome). Update remaining time once per second while the tab is visible; nothing when it isn't.

### 4.7 Settings screen (P2, minimal)

- `I'm awake window` — minutes, default **15**, options 5 / 10 / 15 / 20 / 30. This is the one setting the ring feature depends on.
- `Snooze duration` default (same options).
- `Battery optimization` → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for our package, with a plain-language explanation. Alarms are unreliable under Doze without it; the user should be nudged once.
- `Exact alarms` status row: if `!alarmManager.canScheduleExactAlarms()`, show a row that deep-links to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. (On the HiBreak it will be granted via `USE_EXACT_ALARM`; still handle denial.)
- `Full-screen intent` status row: if `!notificationManager.canUseFullScreenIntent()`, deep-link to `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
- No about page, no links, no telemetry.

---

## 5. Scheduling, correctness, and platform rules

Non-negotiable: **an alarm that doesn't fire is a total failure.** Aim for AOSP-DeskClock-grade scheduling with none of its weight.

**Permissions (manifest):**
```xml
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
```

**Scheduling rules:**
1. Schedule every enabled alarm's next occurrence with `AlarmManager.setAlarmClock(AlarmClockInfo(triggerAtMillis, showIntent), operation)`. Using `setAlarmClock` for each alarm means SystemUI automatically shows the earliest as the status-bar/lock-screen "next alarm" indicator, exactly like the stock app does (verified in `dumpsys-alarm.txt`: the stock app registers one `Alarm clock:` block for the next instance). `showIntent` = a `PendingIntent` to your main activity (alarm tab); `operation` = an explicit `PendingIntent` to `AlarmReceiver` carrying the alarm id.
2. `PendingIntent.getBroadcast(ctx, alarmId, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`. Request code = alarm id. Never use implicit intents.
3. `AlarmReceiver.onReceive` → acquire a `PowerManager.PARTIAL_WAKE_LOCK` (10 s timeout), mark the instance firing, then `startForegroundService(AlarmService)`.
4. `AlarmService` (foreground, `foregroundServiceType="mediaPlayback"`, `directBootAware`): `MediaPlayer` looping the chosen ringtone with `AudioAttributes(USAGE_ALARM, CONTENT_TYPE_SONIFICATION)`, `Vibrator` pattern, a high-importance notification channel (`ALARM`) with `setFullScreenIntent(...)`, and it launches `AlarmRingActivity`. Stop and release everything on dismiss/snooze; `stopForeground(STOP_FOREGROUND_REMOVE)`.
5. Reschedule from a `directBootAware` receiver on: `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`. On any of these, recompute and re-register every enabled alarm.
6. Also re-register after any mutation (create/edit/delete/toggle/dismiss/snooze/skip/undo).
7. DST and timezone changes: compute triggers from `ZonedDateTime` in the device zone, never from fixed offsets. Use `ZonedDateTime.of(date, LocalTime.of(h, m), zone)`; for a nonexistent local time (spring forward) the platform shifts it forward, and for an ambiguous time it picks the earlier offset — both are acceptable; write a unit test asserting the alarm still fires on the DST-transition day.
8. `directBootAware="true"` on the receiver and service, and keep alarm data in device-protected storage (`context.createDeviceProtectedStorageContext()`) so alarms survive a reboot before first unlock — the stock app does this (its `dataDir` is `/data/user_de/0/…`), and you should too.
9. If the phone is in a call or the activity can't start, the notification's full-screen intent is the fallback. If two alarms fire at once, queue them (`AlarmService` handles one ring at a time, then the next).
10. Missed alarms: if `now - trigger > 10 min` when the receiver finally runs, still ring, but label it `Missed alarm` — do not silently swallow it.

**Android 14 gotchas to handle:**
- `POST_NOTIFICATIONS` is a runtime permission — request it on first launch with a plain rationale.
- `canScheduleExactAlarms()` must be checked before `setAlarmClock`; if false, show a non-blocking banner with a deep link. Never crash.
- `canUseFullScreenIntent()` as described in Settings; an alarm app is normally granted this, but check.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` must be declared *and* passed as the type when calling `startForeground`.
- Register the notification channel before posting anything; channel importance `IMPORTANCE_HIGH` for `ALARM`, `IMPORTANCE_LOW` for the persistent "upcoming alarm" notification (P2).

---

## 6. Data model and the "I'm awake" algorithm

### 6.1 SQLite (framework `SQLiteOpenHelper`, two tables + one)

```sql
CREATE TABLE alarms(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  hour INTEGER NOT NULL,
  minute INTEGER NOT NULL,
  label TEXT NOT NULL DEFAULT '',
  enabled INTEGER NOT NULL DEFAULT 1,
  repeat_mask INTEGER NOT NULL DEFAULT 0,   -- bit0=Mon … bit6=Sun; 0 = one-shot
  vibrate INTEGER NOT NULL DEFAULT 1,
  ringtone TEXT,                            -- uri string; NULL = system alarm default
  snooze_minutes INTEGER NOT NULL DEFAULT 10,
  fired_at INTEGER                          -- last fire, for "Missed alarm"
);
CREATE TABLE alarm_skip(                    -- "skip this occurrence for this alarm, on this date"
  alarm_id INTEGER NOT NULL,
  date TEXT NOT NULL,                       -- ISO local date, yyyy-MM-dd
  PRIMARY KEY(alarm_id, date)
);
CREATE TABLE timers(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  length_ms INTEGER NOT NULL,
  remaining_ms INTEGER NOT NULL,
  state INTEGER NOT NULL,                    -- 0 idle, 1 running, 2 paused
  end_at INTEGER,                            -- wall-clock deadline when running
  label TEXT
);
```

`repeat_mask = 0` means "once" — after it fires and is dismissed, set `enabled = 0` but keep the row.

**Critical:** skipping must never mutate `repeat_mask` or `enabled`. Some FOSS alarm apps implement skip by clearing today's weekday bit and rescheduling — do not copy that; it's a known upstream hack with a documented edge-case bug. Skips live only in `alarm_skip`, so "tomorrow is normal" is guaranteed by construction.

### 6.2 `I'm awake` — exact algorithm

```
fun onImAwake(ringingAlarmId: Int) {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val windowMinutes = settings.imAwakeWindowMinutes   // default 15
    val cutoff = now.plus(windowMinutes, MINUTES)

    val skipped = mutableListOf<Alarm>()
    for (alarm in db.enabledAlarmsExcluding(ringingAlarmId)) {
        val next = nextTrigger(alarm, now, zone) ?: continue
        // Only today's occurrences inside the window are skipped.
        if (next.toLocalDate(zone) == today && !next.isAfter(cutoff)) {
            db.addSkip(alarm.id, today)
            alarms.cancel(alarm.id)          // cancel its PendingIntent
            skipped += alarm
        }
    }
    dismiss(ringingAlarmId)                  // identical to Cancel
    rescheduleAll()                          // next occurrences, tomorrow included
    showResult(skipped)                      // e.g. "Skipped 8:05, 8:10, 8:20" + Undo, 10 s
    notifications.clearUpcomingAlarmNotifications()
}

fun nextTrigger(alarm, from, zone): ZonedDateTime? {
    if (!alarm.enabled) return null
    var day = from.toLocalDate()
    repeat(9) {                              // 7 days + slack for skips
        val t = ZonedDateTime.of(day, LocalTime.of(alarm.hour, alarm.minute), zone)
        if (alarm.repeatsOn(day.dayOfWeek) && t.toInstant().isAfter(from)
            && !db.isSkipped(alarm.id, day)) return t
        day = day.plusDays(1)
    }
    return null
}
```

Notes:
- The window is measured from **now**, not from the ringing alarm's scheduled time, so pressing the button late still catches the next alarm.
- A 20-minute-later alarm must **not** be skipped — test this.
- Duplicate alarms at the same time are all skipped.
- Provide **Undo** (P1): delete today's rows from `alarm_skip` and reschedule. Show it as a plain in-screen row/button for 10 seconds, then hide it (use a `Handler.postDelayed`, no animation).
- Prune `alarm_skip` rows older than 7 days on each app start.

---

## 7. Intent API (becoming the default alarm app)

Declare these so the phone, voice assistants, and automation apps can drive it — mirror the stock manifest (`clock-audit/manifest.xml`):

```xml
<!-- alarm clock API -->
<activity android:name=".api.HandleApiActivity" android:excludeFromRecents="true" android:launchMode="singleInstance">
  <intent-filter>
    <action android:name="android.intent.action.SET_ALARM"/>
    <action android:name="android.intent.action.SET_TIMER"/>
    <action android:name="android.intent.action.SHOW_ALARMS"/>
    <action android:name="android.intent.action.SHOW_TIMERS"/>
    <action android:name="android.intent.action.DISMISS_ALARM"/>
    <action android:name="android.intent.action.SNOOZE_ALARM"/>
    <category android:name="android.intent.category.DEFAULT"/>
  </intent-filter>
</activity>
```
Also honor `android.intent.extra.alarm.HOUR`, `.MINUTES`, `.DAYS`, `.MESSAGE`, `.SKIP_UI`, plus `android.intent.extra.alarm.SEARCH_MODE` / `EXTRA_ALARM_SEARCH_MODE` for `DISMISS_ALARM`/`SNOOZE_ALARM` (`ALL` = every ringing alarm, `NEXT` = the next one). Guard `SET_ALARM`/`SET_TIMER` behind `android:permission="com.android.alarm.permission.SET_ALARM"` on an `<activity-alias>` exactly as the stock app does.

**Making it the default.** There is no dependable `ALARM_CLOCK` role on this ROM (`cmd role get-role-holders android.app.role.ALARM_CLOCK` returns nothing; some ROMs expose one, this one does not). What actually works, in order:
1. Install the app.
2. Disable the stock clock: `adb shell pm disable-user --user 0 com.android.deskclock` → expect `new state: disabled-user`; revert with `pm enable` if needed.
3. Trigger a chooser once and pick "Always": `adb shell am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR 8 --ei android.intent.extra.alarm.MINUTES 0`
4. Verify: `adb shell cmd package resolve-activity --brief -a android.intent.action.SHOW_ALARMS` must name your package. (`SET_ALARM` may still show a resolver if another app claims it — Google Calendar does on this phone.)

Provide `scripts/install.sh` doing 1–4 non-interactively (skipping the chooser, which needs a human tap) and `scripts/revert.sh` re-enabling the stock app.

---

## 8. E-ink UI rules (apply to every screen)

- Pure black on pure white. 1.5 dp black strokes instead of shadows/elevation. 8 dp corner radius max.
- Touch targets ≥ 48 dp; primary ring-screen buttons ≥ 64 dp tall.
- Type scale: ring clock 96 sp · tab clock 120 sp · alarm row time 56 sp · timer 48 sp · buttons 20–24 sp · body 15–16 sp. Let system font scale apply.
- Dividers: 1 dp `#000000` at 20 % alpha, or just spacing — prefer spacing.
- **No** `ProgressBar` spinners, **no** indeterminate anything, **no** `SwipeRefreshLayout`, **no** `RippleDrawable`, **no** `StateListAnimator`, **no** `elevation`, **no** `CardView` (draw the border yourself with a `ShapeDrawable`).
- Screen refreshes must be *event-driven*: nothing periodic faster than 1 Hz, and only on the visible tab. The stock app's idle-on-Clock-tab result (0 frames / 30 s) is the bar.
- Kill transitions explicitly: `overridePendingTransition(0, 0)` after every `startActivity`, and no `windowEnterTransition`.

---

## 9. Device verification — required evidence

Attach raw output for each item to `TESTING.md`. Rebuild and rerun after every fix; do not claim a step passed without pasted output.

**Device tooling notes (verified on this exact phone — plan around them):**
- There is **no `sqlite3` binary on the device**, so `adb shell sqlite3` / `run-as … sqlite3` is not available. Inspect the DB by pulling it (§T2).
- `run-as` works for debuggable builds, but only once the app is installed under its final package name.
- `settings get global auto_time` is `1` (network time on). Any date change requires `settings put global auto_time 0` first, and may still be refused without root — see T4 for the deterministic alternative.
- No root on the device (`su` absent, `ro.secure=1`). Everything must work as a normal third-party app plus adb shell.
- `adb exec-out screencap -p > file.png` works for UI evidence.

**Install & wire up**
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.ilyaas.einkclock android.permission.POST_NOTIFICATIONS
adb shell pm disable-user --user 0 com.android.deskclock
adb shell cmd package resolve-activity --brief -a android.intent.action.SHOW_ALARMS
```

**Build-hygiene gates**
```bash
ls -l app/build/outputs/apk/debug/app-debug.apk            # ≤ 600 KB
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "\.so|classes.*\.dex|resources.arsc"
# expect: exactly one classes.dex, no .so, resources.arsc well under 100 KB
```

**Performance gates**
```bash
adb shell am force-stop com.ilyaas.einkclock
adb shell am start -W -n com.ilyaas.einkclock/.ui.MainActivity      # TotalTime ≤ 300 ms
adb shell dumpsys meminfo com.ilyaas.einkclock | grep "TOTAL PSS"   # ≤ 25 MB
adb shell dumpsys gfxinfo com.ilyaas.einkclock reset
# switch tabs 6 times, then:
adb shell dumpsys gfxinfo com.ilyaas.einkclock | grep -E "Total frames|Janky frames|90th percentile"
adb shell dumpsys gfxinfo com.ilyaas.einkclock reset
# sit on the Clock tab for 60 s, then: Total frames rendered must be 0
```

**Functional acceptance tests.** Add a **debug-only** test hook so you never wait for a real clock time: an exported receiver or activity, guarded by `if (BuildConfig.DEBUG)`, that fires the ring flow for a given alarm id immediately, e.g. `adb shell am broadcast -n com.ilyaas.einkclock/.debug.TestRingReceiver --ei alarm_id 3`.

*T0 — scheduling.* Create four repeating daily alarms 8:00, 8:05, 8:10, 8:20. Then `adb shell dumpsys alarm | grep -A6 com.ilyaas.einkclock` must show four `Alarm clock:` blocks with the correct trigger times, and the status bar / lock screen must show the next-alarm indicator for 8:00 (paste `adb exec-out screencap -p` output).

*T1 — ring, Pause, Cancel.* Fire alarm #1 with the test hook. The screen must wake, show over the lock screen, play the alarm ringtone on the alarm stream, and vibrate. Press `Pause` → see `Snoozed · rings again at HH:MM`, verify a new exact alarm exists ~10 min out. Press `Pause` again on the re-ring → then `Cancel` → verify the alarm's row still shows enabled with `Every day`, and that its next occurrence is tomorrow.

*T2 — **I'm awake** (the headline).* Fire the 8:00 alarm, press `I'm awake`. Then pull the database and inspect it **on the host** (the device has no `sqlite3`). The DB is in WAL mode, so pull the sidecar files too:
```bash
mkdir -p /tmp/clockdb
adb exec-out run-as com.ilyaas.einkclock cat databases/clock.db     > /tmp/clockdb/clock.db
adb exec-out run-as com.ilyaas.einkclock cat databases/clock.db-wal > /tmp/clockdb/clock.db-wal 2>/dev/null || true
adb exec-out run-as com.ilyaas.einkclock cat databases/clock.db-shm > /tmp/clockdb/clock.db-shm 2>/dev/null || true
sqlite3 /tmp/clockdb/clock.db \
  "select a.hour, a.minute, a.enabled, a.repeat_mask, ifnull(s.date,'') \
   from alarms a left join alarm_skip s on s.alarm_id=a.id order by a.hour, a.minute;"
```
(If the host has no `sqlite3`, fall back to a debug-only `DumpReceiver` that logs the same query to logcat under a fixed tag, and paste `adb logcat -d -s ClockDebug`.)
Expected: all four alarms `enabled=1` with unchanged `repeat_mask`; `alarm_skip` rows for **today** for 8:05, 8:10, 8:20 *only*. `dumpsys alarm` must show no remaining alarms today for the app, with next triggers on tomorrow's date. The UI must show `Skipped 8:05, 8:10, 8:20` with a working `Undo`.

*T3 — window boundary.* Add a fifth alarm at 8:40 (35 min after the firing alarm). After `I'm awake` it must **not** be skipped and must still be registered in `dumpsys alarm`.

*T4 — per-day, not permanent.* Two independent checks:
1. **Deterministic unit test (required).** `nextTrigger()` and the `I'm awake` selector must take an injectable clock/`Clock` so a JVM unit test can assert: today-in-window → skipped; today-outside-window → not skipped; tomorrow → not skipped; the day after a skip → fires normally; a skip row from last week has no effect. These tests run with `./gradlew :app:testDebugUnitTest` and need no device.
2. **On-device (mechanism verified working on this phone).** `cmd alarm set-time` / `cmd alarm set-timezone` are available from adb shell without root:
   ```bash
   adb shell settings put global auto_time 0
   adb shell cmd alarm set-time <epochMillis>   # move the clock to just before an alarm's trigger
   # confirm the alarm fires, and that tomorrow's occurrences are re-registered in dumpsys alarm
   adb shell settings put global auto_time 1    # restores network time — always do this
   ```
   Never leave the device's clock wrong.

*T5 — reboot persistence.* `adb shell reboot`, wait for boot, then `dumpsys alarm | grep -A6 com.ilyaas.einkclock` must show all enabled alarms re-registered without opening the app.

*T6 — timezone/DST.* Both commands are verified to work from adb shell on this phone:
```bash
adb shell cmd alarm set-timezone America/Chicago   # every trigger must recompute to the new local wall clock
adb shell cmd alarm set-timezone America/New_York  # restore
```
Then the DST case. The next US fall-back is 2026-11-01 02:00 EDT→EST, so 01:00–02:00 occurs twice. With the four daily alarms enabled:
```bash
adb shell settings put global auto_time 0
adb shell cmd alarm set-time 1793510700000   # 2026-11-01 01:25 EDT, inside the ambiguous hour
# the 08:00 alarm must now be 1h35m away in dumpsys alarm, not 35m — the repeated hour is not lost
adb shell cmd alarm set-time 1793537400000   # 2026-11-01 07:50 EST, ten minutes before the alarm
# confirm it rings at 08:00 local wall-clock time
adb shell settings put global auto_time 1
```
Pair this with unit tests over an injected clock for both 2026-03-08 (spring forward: 02:00–03:00 does not exist) and 2026-11-01 (fall back), asserting the alarm still fires at its intended wall-clock time and that no alarm is silently dropped.

*T7 — kill-safety.* `adb shell am force-stop com.ilyaas.einkclock`, then move the clock to 60 s before a registered trigger (`adb shell settings put global auto_time 0; adb shell cmd alarm set-time <triggerMillis - 60000>`). The alarm must still ring with the app not running. Restore `auto_time 1` afterwards.

*T8 — no regressions in the e-ink feel.* With all four tabs visited: no animation was visible; `dumpsys gfxinfo` shows no frames while idle; screen never flashes between screens.

---

## 10. Deliverables

- Repo `eink-clock` with the single `app` module, buildable with `./gradlew :app:assembleDebug` using only the local Android SDK.
- Source layout that a human can navigate: `data/` (DB, models, repository), `alarm/` (scheduler, receiver, service, ring logic), `ui/` (one activity per tab, plus `AlarmEditActivity`, `AlarmRingActivity`, `TimersUpActivity`, `SettingsActivity`, `api/HandleApiActivity`).
- `scripts/install.sh`, `scripts/verify.sh`, `scripts/revert.sh` (adb wrappers around §9).
- `README.md` — what it is, why it exists, how to build/install, how to make it the default, how to revert.
- `DESIGN.md` — decisions with the *why*: no-AndroidX, no-Compose, skip-dates-not-mask-mutation, `setAlarmClock` for the indicator, direct-boot storage, the `I'm awake` semantics, and the numbers from §2 you're beating.
- `TESTING.md` — raw output for T0–T8 and every gate above, plus the screenshot of the ring screen.
- `docs/ring-screen.png` and `docs/alarm-list.png` — screenshots taken on the device (`adb exec-out screencap -p > file.png`).

## 11. Definition of done

1. All gates in §9 pass with pasted evidence, and all acceptance tests T0–T8 pass.
2. APK ≤ 600 KB, one dex, no native libs, English-only resources.
3. Cold start ≤ 300 ms, PSS ≤ 25 MB, 0 idle frames — measured on the HiBreak, not on an emulator.
4. `I'm awake` behaves exactly as specified, including the 15-minute boundary, per-day-only skipping, the untouched repeat schedule, and Undo.
5. Alarms survive reboot, force-stop, timezone change, and a DST transition.
6. The stock `com.android.deskclock` is disabled, our app resolves `SHOW_ALARMS`, and `scripts/revert.sh` cleanly restores the stock app.
7. Nothing in the app animates, ripples, or repaints while idle.

## 12. Anti-requirements (if you're tempted, don't)

- Don't add AppCompat/Material/Compose "just for the Switch/TimePicker/toolbar" — the framework has all of it.
- Don't add coroutines; use a single background `HandlerThread` or `Executors.newSingleThreadExecutor()`, and never touch SQLite on the main thread.
- Don't implement skip by clearing the weekday bit or by disabling the alarm. Use `alarm_skip`.
- Don't use `setExact`/`setRepeating` for user alarms; the status-bar indicator and Doze-exemption come from `setAlarmClock`.
- Don't add an analog clock, a seconds display on the clock tab, a splash screen, a first-run wizard, an onboarding carousel, analytics, crash reporting, an update checker, or a rate-this-app prompt.
- Don't try to modify, re-sign, or overlay the stock APK. Disable it and install yours.
- Don't ship a "dark mode" in v1 — e-ink has no backlight benefit and it doubles the resource count.
