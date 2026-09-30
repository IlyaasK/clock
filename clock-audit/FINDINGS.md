# HiBreak stock Clock app — audit + "I'm awake" feasibility

**Date:** 2026-09-30 · **Device:** Bigme HiBreak (`B651CNW2GCBB006000438`), Android 14 (SDK 34), arm64-v8a, 824×1648 @ 300dpi, TZ America/New_York
**Artifact:** `/system/app/ClockProd/ClockProd.apk`, 10,201,365 B, sha256 `4e754d4732bbecfbfe8438234921f6b7bb47bc0b180717689167041a319931fa`
**Copy in this dir:** `ClockProd.apk` (pulled), `manifest.xml`, `dex_strings.txt`, `alarmactivity.smali`, `alarmprovider.smali`, `dumpsys-alarm.txt`, `screen-clock.png`, `screen-alarm.png`

> **Not in the published repository:** the vendor APK itself (`ClockProd.apk`) and the
> raw bytecode dumps derived from it (`dex/classes.dex`, `dex_strings.txt`,
> `alarmactivity.smali`, `alarmprovider.smali`). They stay next to this file on the
> machine that ran the audit; everything needed to follow the findings below — the
> decoded manifest, `dumpsys alarm`, the stock screenshots and these notes — is
> included.

---

## 0. What the app actually is

| Property | Value |
|---|---|
| Package | `com.android.deskclock` (only clock/alarm app on the device) |
| Path / type | `/system/app/ClockProd/ClockProd.apk` — preinstalled, `sharedUserId="android.uid.system"`, uid 1000, platform-signed |
| versionName / code | `1.0.14` / 12 |
| minSdk / targetSdk / compileSdk | 24 / **30** / 33 |
| Launcher activity | `com.android.deskclock/.ActivityDeskClock` |
| Upstream lineage | **AOSP DeskClock** (Android 11 / R era) — class tree matches (`AlarmStateManager`, `AlarmKlaxon`, `data/DataModel`, `uidata/`, `alarms/dataadapter/*ViewHolder`, `actionbarmenu/`, `widget/pop/`) |
| ODM additions | `com.android.provider.AlarmProvider` (exported, no permission), `com.tencent.mmkv` (`libmmkv.so` × 4 ABIs), `xrz.framework.manager.SimUtil`, prop `vendor.xrz.disable_eink_vsync=1` |
| Hardening | resource names obfuscated (`res/yS.xml`, …) — AndResGuard-style |
| Root | none (`su` absent, `ro.secure=1`, `ro.debuggable=0`) |

### Size / shape (measured)
- APK 10.2 MB (16.4 MB uncompressed): **dex 8.7 MB · resources.arsc 3.7 MB · native libs 1.7 MB · res 1.3 MB**
- **Single `classes.dex`, 61,943 method references = 94.5 % of the 64 K single-dex limit**, 50,268 defined methods, 1,173 files
- Native libs are MMKV for 4 ABIs — only `arm64-v8a` (483 KB) is used; 1.3 MB is dead weight on this device
- Strings resource table carries **~85 locales** (default, ca, da, fa, ja, ka, pa, ta, nb, be, de, ne, te, af, bg, th, fi, hi, si, vi, …) plus `mdpi/hdpi/xhdpi/…` and `watch` configs → the bulk of the 3.7 MB arsc
- ART state: `speed-profile` (bg-dexopt) at audit time; forced `speed` via `cmd package compile` changed nothing measurable

---

## 1. Performance — is a rewrite needed for speed?

**Verdict: no. There is no meaningful latency on the table.** The app is not compute-bound; the e-ink panel is the floor.

| Measurement | DeskClock | Reference |
|---|---|---|
| Cold start (`am start -W`, force-stopped) | **535–581 ms** (interleaved run); 298–330 ms in an earlier quieter run | `com.android.settings` 551–616 ms in the same interleaved run |
| Warm start | 0 ms (`TotalTime: 0`, activity already resumed) | — |
| Frame time, 6 tab switches | 77 frames, **median 44 ms, p90 81 ms**, 32 % janky, 25 "slow issue draw commands" | Olauncher **29 / 57 ms**, 11 % janky · Settings **57 / 89 ms**, 38 % janky |
| Idle redraw on Clock tab | **0 frames in 30 s** (no per-second repaint, no continuous e-ink refresh) | — |
| Memory | **57.8 MB PSS / 178 MB RSS**, 180 MB RES | heavy for a clock |
| AOT | `speed-profile`; forcing full `speed` = no change (318–329 ms vs 299–328 ms) | startup is not dex-verification bound |

So: app-side headroom over a trivial app is ~15 ms/frame and ~0 ms of startup; the display pipeline (GPU 90th-pct stalls in the seconds range, animations already globally disabled: `window/transition/animator_scale = 0.0`) sets the wall. A rewrite cannot make the panel refresh faster.

**What a rewrite *would* win (real, but not "faster"):**
- 10.2 MB → ~0.5–1.5 MB (drop 85 locales, 4-ABI MMKV, Material/AppCompat/coroutines/stdlib)
- 58 MB PSS → ~15 MB
- targetSdk 30 → 34 (exact-alarm + full-screen-intent + notification rules the current app predates)
- The ability to change **scheduling semantics** — which is the real reason to leave this app (see §3)

**Blocking constraint if you wanted to modify instead of replace:** 94.5 % of the single-dex 64 K method-ref limit. Adding a feature to this APK needs multidex surgery *even before* the signing problem below.

---

## 2. Kotlin + Jetpack Compose?

**No — on both counts.**

- **No Compose at all:** 0 hits for `Landroidx/compose/…`, `Composable`, `Composer`, `ComposeView` in a 74k-string scan of `classes.dex`. UI is classic **Android Views**.
- **Libraries:** AppCompat (4,116 defined methods), **Material Components 2** (`com.google.android.material`, 7,753), ConstraintLayout, RecyclerView, Fragment, Preference, Transition, Lifecycle, Window — plus bundled `kotlin.collections` (4,216) and `kotlinx.coroutines` (4,501) pulled in by bundled/vendor code.
- **App's own code is Java:** smali carries `.source "AlarmProvider.java"`, `.source "DataModel.java"`, etc. There is no Kotlin app code (`Lkotlin/Metadata;` appears as a single deduped string, from the bundled stdlib).
- Ring screen (`alarms/AlarmActivity`) is Java + `ImageView`/`TextView`/`TextClock`/`CircleView` with `ValueAnimator` bounce/alert animators, bound to ids `0x7f0a01e5` (snooze), `0x7f0a00b7` (dismiss), `0x7f0a00f5` (hint), layout `0x7f0d001f`.

---

## 3. Adding an "I'm awake" button

### Can we patch the stock app? No.
- Platform-signed + `sharedUserId=android.uid.system` → a rebuilt APK cannot be installed as an update (signature mismatch) and cannot be given uid 1000 without the platform key.
- No root (`su` absent) → cannot replace the file in `/system` or `pm install` into the system partition.
- 94.5 % single-dex method budget → multidex rebuild anyway.
- The one vendor hook, `com.android.provider.AlarmProvider` (`android:exported="true"`, authority `com.android.deskclock.alarmprovider`, **no permission**), is a dud: `query/insert/update/delete` all return `null`/`0`, `onCreate()` returns `false`, and `call("start_service")` just re-runs boot-time alarm init. It does **not** allow reading or cancelling alarms. (It is still a small security smell: any app can make DeskClock take a wake lock and reload its data model.)

### What the feature has to mean in DeskClock's model (measured, not assumed)
- Scheduled state on the device right now: 5 `change_state` alarms (04:45, 06:00, 06:05, 06:10, 06:20) + one `indicator`/`Alarm clock` at **08:00** for 2026-10-01 — i.e. the on-screen alarms 06:45 (off), 08:00, 08:05, 08:10, 08:20 each get a state alarm **2 h before** their fire time (`LOW_NOTIFICATION`), and only the next instance is registered as a real alarm clock.
- Consequence: the later alarms in the cluster are **not** AlarmManager entries you can cancel — they are `AlarmInstance` rows in `ClockProvider` (`content://com.android.deskclock`, `exported="false"`). "Cancel them for that day" means marking/deleting *today's instance* inside the alarm app's own database.
- Android has no public API for app A to enumerate or cancel app B's alarms (`AlarmManager.getNextAlarmClock()` returns one, and `ACTION_DISMISS_ALARM` only affects an already-ringing alarm). So this feature **must live inside whichever app owns the alarms**.

### Practical paths
1. **Fork/patch a FOSS alarm app** (recommended — see §4). Fossify Clock already contains the right primitive (`skipNextOccurrence(alarmId)` in `AlarmController.kt`) and an "upcoming alarm" notification path; the patch is a window query + a button. Caveat from its source: skip is implemented by dropping *today's weekday bit* (`// TODO: This is a bit of a hack. Skipped alarms should be tracked properly.`) — fine for "Every day"/"Mon–Fri", and it has an upstream TODO for the single-weekday case.
2. **Build a small purpose-built e-ink alarm app.** ~1–2 kLOC, Views (not Compose), monochrome, no animation; schedules via `setAlarmClock()` so SystemUI still shows the next-alarm indicator; stores `skipDates` to make "I'm awake" exact. Needs `USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `showWhenLocked|turnScreenOn`, foreground service + wake lock. Costs a real app, buys exact semantics + a 1 MB APK.
3. **Root (Magisk) + replace the system APK.** Only path that keeps the stock package/system privileges; needs the platform key (or dropping `sharedUserId`), i.e. real risk. Not recommended.

**Replacement is mechanically possible without root:** `adb shell pm disable-user --user 0 com.android.deskclock` → `disabled-user`, and `pm enable` restores it (both verified on this device). So a third-party alarm app can take over cleanly.

### UI spec (as asked, restated)
On the ringing screen: **full-width "I'm awake"** button on top, and below it a row of two half-width buttons — **Pause** (left) and **Cancel** (right). In DeskClock terms, "Cancel" = dismiss this instance, "Pause" = snooze; "I'm awake" = dismiss this instance **and** skip every enabled alarm whose next trigger falls within +15 min (window configurable) for today only. Note the spec sentence was garbled in the request — worth confirming whether "Pause" means *snooze this alarm* or *pause all remaining alarms today*.

---

## 4. Existing FOSS app that already does both?

**No app has both an e-ink theme and a wake-time batch dismiss.** Full evidence: `../foss-android-alarm-eink-research.md`.

- **Nothing is e-ink-first.** No alarm app in the Boox/Bigme/Onyx/Mudita roundups; Mudita users fall back to the stock clock through an e-ink launcher.
- **The batch feature does not exist as asked.** Closest: **Chrono** (Flutter, GPL-3, stale since Jan 2025) ships "Skip all filtered alarms" + a "Today" filter — manual and pre-emptive, no wake-time trigger, no ±15 min window.
- **AOSP DeskClock, LineageOS and GrapheneOS forks: confirmed no batch dismiss** (`dismiss()` → `deleteInstanceAndUpdateParent(mAlarmInstance)`, single instance, no loop). BlackyHawky/Clock adds only per-alarm delete-after-use.
- **Best base = Fossify Clock** (Kotlin + Views, 27 XML layouts, 0 Compose; F-Droid; `AlarmController.kt` has `skipNextOccurrence`/`stopAlarm`/`snoozeAlarm` already; the exact ask is an open request, `FossifyOrg/Clock#307`). Patch = select enabled alarms whose computed next trigger is within the window, loop `skipNextOccurrence`, wire to a ringing-screen button.

---

## 5. Recommendation

1. **Do not rewrite for speed** — measurements in §1. It's AOT-compiled, Compose-free, idle-quiet, and starts on par with Settings; the panel is the bottleneck.
2. **If you want the "I'm awake" button, use Fossify Clock as the base** and patch it (small, localized) rather than building from zero; or write your own ~1 MB Views app if you want exact skip semantics and a true e-ink UI.
3. **Either way the stock app has to be disabled**, not modified — verified possible with `pm disable-user`.
