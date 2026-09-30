# FOSS Android Alarm-Clock Apps for E-Ink + "I'm Awake" Batch Dismiss

**Research date:** 2026-09-30
**Target device:** Bigme HiBreak Pro (e-ink, Android 14, arm64)
**Requirements:** (a) e-ink-friendly theme (pure B/W, high contrast, no animation/grayscale dependence, large text); (b) an "I'm awake" action that dismisses/cancels *other* alarms inside a window (~±15 min) or the remaining alarms for the day.

> **Method note:** Repo metadata comes from the GitHub REST API; F-Droid metadata from the official `index-v1` index and per-package API. Where a claim is from an unverified secondary source it is marked **unverified**. All fetched page content was treated as data, not instructions.

---

## 1. Ranked candidates

Ranking is for *this* use case: e-ink rendering quality and UI toolkit first, then the availability of any batch-skip primitive.

| # | App | Repo | License | Distribution | Language / toolkit | minSdk | Last release | Last activity | Stars | E-ink verdict |
|---|-----|------|---------|--------------|--------------------|--------|--------------|---------------|-------|---------------|
| 1 | **Fossify Clock** | [FossifyOrg/Clock](https://github.com/FossifyOrg/Clock) | GPL-3.0 | [F-Droid `org.fossify.clock`](https://f-droid.org/en/packages/org.fossify.clock/) v1.6.0; Play | Kotlin + **Android Views** (0 Compose files; 27 XML layouts) | 26 | 1.6.0, 2026-01-30 | commit 2026-09-28 | 706 | **Best overall.** Views-based, no Compose animations; app-level AMOLED/black theming inherited from Fossify Commons. Functional, if not pretty. |
| 2 | **BlackyHawky/Clock** | [BlackyHawky/Clock](https://github.com/BlackyHawky/Clock) | GPL-3.0 | [F-Droid `com.best.deskclock`](https://f-droid.org/en/packages/com.best.deskclock/) v2.33 | Java + **AOSP Views/animators** | 23 | 2.33, 2026-09-25 | nightly 2026-09-29 | 1133 | Very active AOSP-DeskClock descendant with deep customization; but the firing activity is animation-heavy (`setAnimatedFractions` + ValueAnimators), so it needs e-ink tuning (or an e-ink ROM's animation-suppression mode). |
| 3 | **AlarmClock** (yuriykulikov) | [yuriykulikov/AlarmClock](https://github.com/yuriykulikov/AlarmClock) | **No LICENSE file at repo root** (state as unverified/proprietary-ambiguous) | [F-Droid `com.better.alarm`](https://f-droid.org/en/packages/com.better.alarm/) v3.15.04 | Kotlin + Views | 21 | tag 3.15.04 ([F-Droid](https://f-droid.org/en/packages/com.better.alarm/)) | commit 2026-06-03 | 621 | "Simple Alarm Clock"; older, plainer UI. Not Compose, so no Material-3 animation requirement. Has alarm **skip** (a skip-duration setting), but no batch action. |
| 4 | **Chrono** | [vicolo-dev/chrono](https://github.com/vicolo-dev/chrono) | GPL-3.0 | [F-Droid `com.vicolo.chrono`](https://f-droid.org/en/packages/com.vicolo.chrono/) v0.6.0 | **Dart / Flutter** | Flutter default (21) | v0.6.0, 2025-01-05 | commit 2025-01-13 (stale ~20 months) | 1791 | **Closest to the feature** (see §2/§6), but Flutter on e-ink is generally a poor fit — Material page transitions, ripple, elevation; maintenance has stalled. |
| 5 | **Clock You** | [you-apps/ClockYou](https://github.com/you-apps/ClockYou) | GPL-3.0 | [F-Droid `com.bnyro.clock`](https://f-droid.org/en/packages/com.bnyro.clock/) v12.0 | Kotlin + **Jetpack Compose (Material 3)** | 23 | v12.0, 2026-09-11 | commit 2026-09-29 | 669 | Explicitly "built with Material Design 3 (You)". M3 elevation/color-role theming and Compose animations are the *opposite* of the e-ink requirement. |
| 6 | **AOSP DeskClock** (platform) | upstream `platform/packages/apps/DeskClock` ([cs.android.com](https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/DeskClock/)) | Apache-2.0 | Ships with AOSP / some ROMs | Java + Views + ValueAnimator | 29 (GrapheneOS fork manifest) | tracks AOSP main | active | n/a (mirrors) | Functional, dated UI. Dismiss path animates. **No batch dismiss** (confirmed, §3). |
| 7 | **LineageOS DeskClock** | [LineageOS/android_packages_apps_DeskClock](https://github.com/LineageOS/android_packages_apps_DeskClock) | Apache-2.0 (AOSP) | ROM-bundled | Java + Views | ROM-defined | branch lineage-23.2 | commit 2026-09-28 | 39 | Fork of AOSP; `dismiss()` is byte-for-byte the AOSP logic. **No batch feature added.** |
| 8 | **GrapheneOS DeskClock** | [GrapheneOS/platform_packages_apps_DeskClock](https://github.com/GrapheneOS/platform_packages_apps_DeskClock) | Apache-2.0 upstream fork (GitHub reports `NOASSERTION` — **unverified as a distinct license**) | GrapheneOS ROM only | Java + Views | 29 | n/a | commit 2026-09-26 | 11 | Security-hardened AOSP fork. **No batch feature added.** |
| 9 | **Simple Alarm Clock** (SimpleMobileTools) | [SimpleMobileTools/Simple-Clock](https://github.com/SimpleMobileTools/Simple-Clock) | GPL-3.0 | Superseded by Fossify; last F-Droid-era release 5.11.2 (2023-09-23) | Kotlin + Views | — | 5.11.2, 2023-09-23 | commit 2024-06-11 | 625 | Predecessor of Fossify Clock; effectively dormant after the SimpleMobileTools sale. |
| 10 | **Alarmio** | [fennifith/Alarmio](https://github.com/fennifith/Alarmio) | Apache-2.0 | [archived] | Java + Views | 16 | 1.0.5, 2020-04-09 | 2023-01-30, **archived** | 340 | Abandoned; targets API 29, too old to trust on Android 14. |
| 11 | **AlarmClockXtreme** (FOSS project) | [SysAdminDoc/AlarmClockXtreme](https://github.com/SysAdminDoc/AlarmClockXtreme) | Apache-2.0 | GitHub releases (F-Droid APK artifact); **not in the F-Droid or IzzyOnDroid indexes** (verified: both APIs return `NOT_FOUND`) | Kotlin (see repo) | Android 8.0+ | v1.15.35, 2026-09-05 | commit 2026-09-26 | 28 | Not e-ink-designed. Has *wake confirmation* (re-rings if you fall back asleep) and per-alarm next-occurrence skip — but **no batch dismiss**. Distinct from the proprietary Play-store "Alarm Clock Xtreme" by AVG. |

### Candidates checked and eliminated

| Candidate | Finding |
|---|---|
| **Etar / Etar-Calendar** | [Etar-Group/Etar-Calendar](https://github.com/Etar-Group/Etar-Calendar) (2.6k★, GPL-3.0) is an open-source **calendar**, not an alarm clock. F-Droid: [`ws.xsoh.etar`](https://f-droid.org/en/packages/ws.xsoh.etar/). Its "alarms" are calendar event reminders; no wake-up batch dismiss. |
| **Clock+ (SecUSo)** | No repository located at the expected paths (`SecUSo/clockplus` → 404). **Unverified** whether it still exists / is maintained. |
| **LibreAlarm** | Only stale forks found ([pimpimmi/LibreAlarm](https://github.com/pimpimmi/LibreAlarm), last push 2017). Not a viable modern candidate. |
| **Alarm Klock** | No canonical modern repo; only old forks (e.g. `ypid/AlarmKlock`, 2014). Dead. |
| **Extreme Alarm Clock** | GitHub search returns unrelated/abandoned projects, no maintained FOSS Android alarm. |
| **Easy Alarm Clock / Wake Me Up / AlarmBell / Smart Alarm Clock / Just Another Alarm Clock** | GitHub-wide searches returned no maintained FOSS Android alarm matching these names. **Unverified / not found** — these appear to be Play-store titles or dead projects, not identifiable FOSS repos. |
| **Chrono (F-Droid)** | Same project as #4 above. |
| **com.dosse.clock31 (Clock31)**, **com.vayunmathur.clock (MA Clock)**, **com.firebirdberlin.nightdream (Nightdream)**, **com.chancehorizon.just24hoursplus**, **systems.sieber.fsclock** | Enumerated from the complete F-Droid index as clock-adjacent apps. Clock31 is a calendar/clock **widget**; Nightdream is a desk-clock screensaver (night mode = pure black on AMOLED); Just 24 Hours and FsClock are large-clock displays/screensavers. None is a full alarm app with batch dismiss. |
| **Sleep as Android** | **Not FOSS.** The app source is closed; only side projects are published ([urbandroid-team repos](https://github.com/orgs/urbandroid-team/repositories) — e.g. an Apache-2.0 captcha *support library* and watch add-ons). Widely used, but fails the open-source requirement. |

**Completeness note:** I enumerated **all 4,486 apps** in the official F-Droid `index-v1` and filtered for the Clock/Alarm categories and alarm/clock names. 33 packages matched; the complete relevant set is reflected above. Anything not listed is neither an alarm app nor on F-Droid under a discoverable name.

---

## 2. "I'm awake" batch-dismiss: per-candidate evidence

**Headline: no candidate has a true "I'm awake" batch-dismiss tied to the ringing alarm.** What exists is a *pre-emptive, manual* batch skip in Chrono only.

### Chrono — the only partial match

Chrono ships a batch action, "**Skip all filtered alarms**", from the alarm list's filter bar:

- Source: [`chrono/lib/alarm/screens/alarm_screen.dart:257`](https://github.com/vicolo-dev/chrono/blob/master/lib/alarm/screens/alarm_screen.dart), action handler `_handleSkipChangeMultiple()` loops over alarms and calls `alarm.setShouldSkip(value)`.
- Filters are date- and state-based, including a **"Today"** filter ([`chrono/lib/alarm/data/alarm_list_filters.dart`](https://github.com/vicolo-dev/chrono/blob/master/lib/alarm/data/alarm_list_filters.dart), `isToday()`).
- So: **filter to "Today" → "Skip all filtered alarms" = skip the rest of today's alarms.** This is the closest existing analogue to requirement (b).

**But it is not "I'm awake":**
- It is a **manual, pre-emptive app action** taken *before* alarms fire — not a button on the ringing screen.
- There is **no time-window (±15 min)** concept; granularity is the whole day via the filter.
- Chrono's own maintainers treat the *ringing-time* problem as **unimplemented**. The relevant feature requests are closed/deferred:
  - [vicolo-dev/chrono#219 — "Wakeup Check"](https://github.com/vicolo-dev/chrono/issues/219) — *closed*, merged into #152.
  - [vicolo-dev/chrono#152 — "Snooze Confirmation Feature for Alarms"](https://github.com/vicolo-dev/chrono/issues/152) — still **open**; maintainer: *"That sounds like an interesting and pretty useful feature, will definitely consider it."*
  - A commenter on #219 describes the canonical design: *"after you're done dismissing your alarm … a notification is silently generated that has a customisable timer. If you don't dismiss the notification within that time limit … you have to do the entire process of dismissing the alarm again."*
- **Caveat:** Chrono's `canBeSkipped => !isSnoozed && !isFinished && isEnabled` ([`alarm.dart:114-115`](https://github.com/vicolo-dev/chrono/blob/master/lib/alarm/types/alarm.dart)) suggests skip targets **enabled, not-yet-ringing** alarms, not an already-snoozed alarm. I could not verify that skipping also cancels an active snooze — treat "it clears pending snoozes too" as **unverified**.

### Fossify Clock — no batch dismiss, but the needed primitive exists

- Dismiss affects **only the ringing alarm**: `AlarmController.stopAlarm(alarmId)` stops the service and, for non-recurring alarms, disables/deletes *that* alarm — it never touches others ([`AlarmController.kt:130-145`](https://github.com/FossifyOrg/Clock/blob/main/app/src/main/kotlin/org/fossify/clock/helpers/AlarmController.kt)).
- It **does** have a reusable pre-emptive skip primitive: `skipNextOccurrence(alarmId)` ([`AlarmController.kt:64-93`](https://github.com/FossifyOrg/Clock/blob/main/app/src/main/kotlin/org/fossify/clock/helpers/AlarmController.kt)), wired to an "Early Alarm Dismissal" notification channel via `SkipUpcomingAlarmReceiver`.
- The exact requested feature is an **open feature request**:
  - [FossifyOrg/Clock#307 — "Configurable timeframe upcoming alarms notifications"](https://github.com/FossifyOrg/Clock/issues/307) (closed): *"It should list all active future alarms within the timeframe, **also offering a dismiss button for all of them** … I use it very often; either to check active upcoming alarms or to **dismiss repeating alarms for that day**."* — closed with **no implementing PR** and no comments.
  - [FossifyOrg/Clock#13 — "Disable an alarm once"](https://github.com/FossifyOrg/Clock/issues/13) (open, still active 2026-07) — per-alarm skip-next, not batch.
  - [SimpleMobileTools/Simple-Clock#478 — "Configurable interval for alarm dismissal"](https://github.com/SimpleMobileTools/Simple-Clock/issues/478) (open since 2023): *"Current interval for dismissing upcoming alarms early is too short … make this interval user configurable, for eg. 1 hour."* Direct evidence that the early-dismiss window is a **fixed short interval**, not user-tunable, in this code line.
  - Predecessor PR that *added* early dismissal: [SimpleMobileTools/Simple-Clock#468](https://github.com/SimpleMobileTools/Simple-Clock/pull/468) — *"Added a notification to dismiss an alarm if it is going to trigger within the next 5 minutes."* (merged 2023-07; that 5-minute constant is the thing #478 asks to make configurable).
- Note the inverse relationship: early dismissal lets you kill an alarm **before** it rings, one at a time.

### Clock You — no batch dismiss

- Has `dismissUpcomingAlarm(alarm)` and per-alarm dismissal from the alarm page ([`CreateUpdateDeleteAlarmUseCase.kt:36`](https://github.com/you-apps/ClockYou/blob/main/app/src/main/java/com/bnyro/clock/domain/usecase/CreateUpdateDeleteAlarmUseCase.kt), [PR #529 "[16/17] Add alarm dismissal directly from the alarm page"](https://github.com/you-apps/ClockYou/pull/529)).
- Grep for `dismissAll|cancelAll|skipAll|awake` across `app/src/main` returns only a code comment about keeping the screen awake. **No batch action.**

### BlackyHawky/Clock — no batch dismiss

- `dismiss()` deletes **only** the firing instance: `AlarmStateManager.deleteInstanceAndUpdateParent(appContext, getPrefs(), currentInstance, false)` ([`AlarmActivity.java`](https://github.com/BlackyHawky/Clock/blob/main/app/src/main/java/com/best/deskclock/alarms/AlarmActivity.java)). No loop over other alarms.
- Its closest feature is **per-alarm** "Delete alarm once dismissed" / "Delete alarm after use" ([issue #110](https://github.com/BlackyHawky/Clock/issues/110)), scoped to a single alarm.

### AlarmClockXtreme — no batch dismiss

- Alarm list offers only `alarm_list_skip_next_occurrence` → "Skip next occurrence" and `qs_tile_skip_next_alarm` → "Skip next alarm" (strings and [`AlarmListViewModel.skipNextOccurrence()`](https://github.com/SysAdminDoc/AlarmClockXtreme/blob/main/app/src/main/java/com/sysadmindoc/alarmclock/ui/alarmlist/AlarmListViewModel.kt)).
- Its *inverse* feature is real and shipping: **wake confirmation** — *"Wake confirmation rings again if you dismiss an alarm and fall back asleep"* ([README](https://github.com/SysAdminDoc/AlarmClockXtreme)) — implemented via `WakeConfirmWorker.kt`.
- Vacation mode / holiday skipping pause alarms inside a date range, but that is scheduled range logic, not a wake-time batch action.

### Alarmio / yuriykulikov AlarmClock / Simple-Clock / DeskClock forks

Grep for `dismissAll|cancelAll|awake|skipNext|dismissUpcoming` found **no batch-dismiss implementation** in any of them. yuriykulikov/AlarmClock has an alarm *skip duration* setting and open requests for dismiss challenges ([#319](https://github.com/yuriykulikov/AlarmClock/issues/319)) and volume-button dismiss ([#392](https://github.com/yuriykulikov/AlarmClock/issues/392)), but no batch dismiss.

---

## 3. AOSP DeskClock specifically — confirmed: no such feature

**Confirmed by reading the code, not just the docs.**

- Upstream: `platform/packages/apps/DeskClock` ([cs.android.com](https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/DeskClock/)), Apache-2.0.
- The entire dismiss path is:
  ```java
  private void dismiss() {
      mAlarmHandled = true;
      setAnimatedFractions(0.0f, 1.0f);
      getAlertAnimator(mDismissButton, R.string.alarm_alert_off_text, ...).start();
      AlarmStateManager.deleteInstanceAndUpdateParent(this, mAlarmInstance);   // ← only THIS instance
      Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_deskclock);
      unbindAlarmService();
  }
  ```
  Source: [GrapheneOS fork `AlarmActivity.java`](https://github.com/GrapheneOS/platform_packages_apps_DeskClock/blob/17/src/com/android/deskclock/alarms/AlarmActivity.java) (verbatim AOSP logic; the only grep hit for "dismissAll" in the whole `src/` tree is `dismissAllowingStateLoss()` on a dialog fragment, which is unrelated).
- `deleteInstanceAndUpdateParent(...)` removes **one** `AlarmInstance` and reschedules its parent. There is no iteration over other alarms, no time-window query, and no `cancelAll` path in `AlarmActivity`.
- **Forks checked — none adds it:**
  - **LineageOS**: `dismiss()` is identical, still calling `setAnimatedFractions` + `AlarmStateManager.deleteInstanceAndUpdateParent(this, mAlarmInstance)`; no `awake`/`dismissAll` code ([source](https://github.com/LineageOS/android_packages_apps_DeskClock)).
  - **GrapheneOS**: same ([repo](https://github.com/GrapheneOS/platform_packages_apps_DeskClock)).
  - **BlackyHawky/Clock** (the most feature-rich AOSP descendant, 1.1k★): same single-instance dismiss; adds per-alarm delete-after-use only.
- **Your prior knowledge is correct.**

---

## 4. Wake-up-detection alternatives (auto-dismiss on unlock / pick-up / motion)

These solve the *adjacent* problem ("did the user actually get up?") rather than "cancel my other alarms in the next 15 minutes." None of them batch-cancels other alarms.

| Mechanism | Product | FOSS? | What it does |
|---|---|---|---|
| Dismiss-on-unlock / dismiss-on-pickup / motion | **Sleep as Android** | **No** — app is proprietary; only side libraries are open ([urbandroid-team repos](https://github.com/orgs/urbandroid-team/repositories)) | The `wake-up check` FAQ page that search indexes (`sleep.urbandroid.org/docs/faqs/wake-up_check.html`) now returns **HTTP 404** — treat the current exact behaviour as **unverified**. Secondhand evidence (search snippet "Alarm goes off again after dismiss") and the project's [CAPTCHA anti-cheat post](https://sleep.urbandroid.org/captchas-getting-really-hard-to-skip/) describe a post-dismiss re-ring check rather than a batch cancel. |
| Wake confirmation (re-ring if you fall back asleep) | **AlarmClockXtreme** (FOSS) | Yes — Apache-2.0 | *"Wake confirmation rings again if you dismiss an alarm and fall back asleep."* ([README](https://github.com/SysAdminDoc/AlarmClockXtreme)) — implemented in `WakeConfirmWorker.kt`. |
| Snooze-confirmation / wake-up check | **Chrono** (FOSS) | Yes — GPL-3.0 | Requested, not implemented: [#152](https://github.com/vicolo-dev/chrono/issues/152), [#219](https://github.com/vicolo-dev/chrono/issues/219). |
| Flip/shake to dismiss, power/volume-button dismiss | **BlackyHawky/Clock** (FOSS), **Clock You** (FOSS), **AlarmClockXtreme** (FOSS) | Yes | Sensor/key dismiss, not batch cancel. Clock You's `RingingActivity` responds to face-down + volume keys ([source](https://github.com/you-apps/ClockYou/blob/main/app/src/main/java/com/bnyro/clock/presentation/screens/ringing/RingingActivity.kt)). |
| Automation-based (unlock → cancel alarms) | **MacroDroid / Tasker** | **No** — both closed-source | Could in principle automate "on unlock, cancel upcoming alarms," but there is **no generic Android API to cancel another app's alarms**, so this only works against a specific app's exposed intents. DeskClock-family apps expose `AlarmClock.ACTION_DISMISS_ALARM`, but that targets the *currently firing* alarm. Feasibility for a batch cancel is **unverified** and would be app-specific. |

**Important negative result:** Android provides no public API for app A to enumerate or cancel app B's scheduled alarms. Any "cancel my other alarms" feature must live **inside** the alarm app (or be built as a privileged/root automation).

---

## 5. E-ink theming reality

### Is anything e-ink-first?
**No alarm clock in this candidate set is designed e-ink-first.** Checks performed:
- The main e-reader/F-Droid roundups for Boox/Bigme cover readers, browsers, RSS and podcast apps ([World of eReader, Jul 2026](https://world-of-ereader.com/best-f-droid-apps-android-e-reader/)) — **no alarm clock** is recommended or exists as an e-ink-first app.
- Mudita Kompakt users wanting a better alarm fall back to the **stock Android clock** reached through an e-ink launcher, not to an e-ink-first alarm app: *"If you use Katapult (and I think inkOS) you can access the stock android clock app. I used it for several months with no issues. Multiple alarms, timer, etc."* ([Mudita forum](https://forum.mudita.com/t/alarm-clock-please-urgent/15301/5)).
- E-ink-first *launchers* do exist and are the real e-ink tooling ecosystem: [gezimos/inkOS](https://github.com/gezimos/inkOS) (493★, "Text based & E-ink friendly Android Launcher", AGPL-3.0) and Katapult. These change the home screen, not alarm apps.
- No FOSS alarm app was found that advertises an "e-ink mode", monochrome theme, or Bigme/Boox/Onyx/Dasung/Mudita/Light Phone support.

### Which candidates render acceptably today?

| App | E-ink notes |
|---|---|
| **Fossify Clock** | Best of the set. Pure **Views** (no Compose), inherits Fossify Commons theming including a dark/AMOLED black style; no color-role dependence, no Material-3 motion requirement. Renders acceptably as-is; large-text is a system font-scale matter. |
| **yuriykulikov/AlarmClock** | Plain Kotlin/Views UI, no Compose; old-fashioned but monochrome-tolerant. Functional on e-ink. |
| **BlackyHawky/Clock** | Very feature-rich, but the **ringing activity is animation-driven** (`setAnimatedFractions`, `ValueAnimator`-based `getAlertAnimator`). E-ink panels show this as ghosting/flashing. Usable if animations are suppressed (many e-ink ROMs offer a global "no animation" / refresh-mode setting). |
| **AOSP / LineageOS / GrapheneOS DeskClock** | Same animated dismiss path. Dated but simple UI otherwise. |
| **Chrono** | Flutter: Material transitions, ripples, elevation. Worst fit of the shortlist on e-ink, and stale since Jan 2025. |
| **Clock You** | Explicit Material Design 3 / dynamic color ("You"). Elevation and color-role theming are structural; least e-ink-friendly. |
| **Alarmio** | Simple Views UI, but archived and API-29-era. |

**Practical e-ink guidance (not app-specific, general):** most Bigme/Boox firmware has a system-level refresh/app-optimization mode (A2/regal/"no animation") that suppresses animations for any app. That plus a monochrome theme gets most of the way — but it does not remove color-role dependence in Material 3 apps like Clock You.

---

## 6. Gaps and closest options

**Plainly: no FOSS Android alarm app has both an e-ink-first theme and an "I'm awake" batch-dismiss feature.** No candidate has the batch-dismiss half at all in the sense you asked for (a wake-time button that cancels other alarms within a window or the rest of the day). Chrono has a *manual, pre-emptive, day-granularity* equivalent; everyone else has only per-alarm skip.

The three closest options, with the exact missing piece and rough cost:

| Option | Has | Missing | Rough code work |
|---|---|---|---|
| **1. Fossify Clock** *(recommended base)* | E-ink-acceptable Views UI; `skipNextOccurrence(alarmId)` batch primitive already exists; "upcoming alarm" notification channel + `SkipUpcomingAlarmReceiver` already exist; the feature is an accepted, still-open request ([#307](https://github.com/FossifyOrg/Clock/issues/307)) | The batch action itself: no "dismiss all within N minutes" entry point, and no window query | **Small–moderate, localized.** Add a helper that selects enabled alarms whose next trigger is within ±15 min (or "today"), loops the existing `skipNextOccurrence(alarmId)` over them, and wire it to (a) a button on `AlarmActivity`'s dismiss action and/or (b) the existing notification action set. No scheduling-layer rewrite — the skip primitive and the notification plumbing are already there. The one real design question is that `skipNextOccurrence` currently skips by **day bitmask** for recurring alarms, so a "within 15 minutes" filter needs the computed next-trigger time, not the weekday mask. |
| **2. BlackyHawky/Clock** | Most active, most feature-rich AOSP descendant; per-alarm delete-after-use; huge customization surface | Same batch action; and separately, the ringing UI needs animation suppression for clean e-ink | **Moderate.** AOSP's `AlarmStateManager`/`DataModel` already expose all scheduled instances, so "delete all instances starting within ±15 min" is a short method plus a UI button — comparable in size to option 1. The e-ink animation work is the extra cost. |
| **3. Chrono** | **Already ships** "Skip all filtered alarms" with a "Today" filter — i.e. "skip the rest of today's alarms" as a manual action | Wake-time trigger: it is pre-emptive and manual, not a button on the ringing screen; no ±15-min window; project stale since Jan 2025; Flutter is the worst e-ink fit | **Small logic change, poor strategic fit.** Adding an "I'm awake" button on the ringing screen that calls the existing `_handleSkipChangeMultiple` over all alarms is small — but you would be investing in a stalled Flutter app whose whole UI stack fights e-ink. |

**If the goal is a working phone rather than a fork:** the pragmatic combination is **Fossify Clock** (or BlackyHawky/Clock) on the HiBreak Pro with the ROM's animation suppression enabled, accepting that the "I'm awake" behavior is currently a ~30-line local fork away rather than an installable feature.

---

## Direct answer

**No — there is no existing FOSS Android alarm app that has both an e-ink-friendly theme and an "I'm awake" batch-dismiss feature; the closest is Fossify Clock (e-ink-acceptable Views UI, already has the `skipNextOccurrence` primitive and notification plumbing, needs only a small localized batch-action patch), while Chrono is the only app that ships any batch skip at all — but only as a manual pre-emptive "skip all filtered / today" action, with no wake-time trigger or ±15-minute window.**
