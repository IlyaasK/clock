#!/usr/bin/env bash
# Install Clock and make it the device's alarm app.
#
# Steps 1-2 and 4 are non-interactive. Step 3 (the "which app?" chooser) needs one
# human tap the first time, because the ROM has no android.app.role.ALARM_CLOCK
# role to grant:
#
#   adb shell am start -a android.intent.action.SET_ALARM \
#     --ei android.intent.extra.alarm.HOUR 8 --ei android.intent.extra.alarm.MINUTES 0
#
# Usage: scripts/install.sh [path/to/app-debug.apk]
set -euo pipefail

PKG=com.ilyaas.einkclock
STOCK=com.android.deskclock
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
ADB=(adb)
if [ -n "${ANDROID_SERIAL:-}" ]; then ADB=(adb -s "$ANDROID_SERIAL"); fi

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK" >&2
  echo "Build it first: ./gradlew :app:assembleDebug" >&2
  exit 1
fi

echo "== 1. install"
"${ADB[@]}" install -r "$APK"

echo "== 2. runtime permissions + stock clock disabled"
"${ADB[@]}" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
# Android 14 refuses a background activity start without this appop, which would
# leave the ring screen as a notification when the phone is unlocked.
"${ADB[@]}" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow || true
"${ADB[@]}" shell pm disable-user --user 0 "$STOCK"

echo "== 3. (one human tap) make Clock the default for SET_ALARM"
echo "   run: ${ADB[*]} shell am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR 8 --ei android.intent.extra.alarm.MINUTES 0"
echo "   and pick \"Always\" for Clock"

echo "== 4. resolution check"
"${ADB[@]}" shell cmd package resolve-activity --brief -a android.intent.action.SHOW_ALARMS

echo
echo "Revert with: scripts/revert.sh"
