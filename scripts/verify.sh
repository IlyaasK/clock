#!/usr/bin/env bash
# Runs the build-hygiene and performance gates from the brief against the phone
# and prints one pasteable block. Nothing here changes device state that it does
# not restore, except the stock-clock check which is read-only.
#
# Usage: scripts/verify.sh [path/to/app-debug.apk]
set -uo pipefail

PKG=com.ilyaas.einkclock
ACT=$PKG/.ui.MainActivity
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
ADB=(adb)
if [ -n "${ANDROID_SERIAL:-}" ]; then ADB=(adb -s "$ANDROID_SERIAL"); fi
r() { "${ADB[@]}" "$@"; }

echo "### device"
r shell getprop ro.product.model
r shell getprop ro.build.version.sdk
r shell wm size
r shell wm density
echo

echo "### APK"
ls -l "$APK"
echo "-- entries (expect exactly one classes.dex, no .so, small resources.arsc)"
unzip -l "$APK" | grep -E "\.so$|classes.*\.dex|resources\.arsc" || true
echo "-- total uncompressed/compressed"
unzip -l "$APK" | tail -2
echo

echo "### cold start (force-stop each time)"
for i in 1 2 3 4 5; do
  r shell am force-stop "$PKG"
  sleep 0.4
  r shell am start -W -n "$ACT" 2>/dev/null | grep -E "TotalTime|WaitTime" | tr '\n' ' '
  echo
done
echo

echo "### memory (PSS)"
r shell am force-stop "$PKG"
r shell am start -n "$ACT" >/dev/null 2>&1
sleep 3
r shell dumpsys meminfo "$PKG" | grep -E "TOTAL PSS|TOTAL RSS" || r shell dumpsys meminfo "$PKG" | grep -E "TOTAL"
echo

echo "### frames while idle on the Clock tab (60 s)"
r shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
sleep 60
r shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|90th percentile"
echo

echo "### 6 tab switches"
r shell input tap 300 1600; sleep 1
r shell input tap 500 1600; sleep 1
r shell input tap 700 1600; sleep 1
r shell input tap 100 1600; sleep 1
r shell input tap 300 1600; sleep 1
r shell input tap 500 1600; sleep 1
r shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|90th percentile"
echo

echo "### alarm registration"
r shell dumpsys alarm | grep -c "com.ilyaas.einkclock"
echo

echo "### default alarm app resolution"
r shell cmd package resolve-activity --brief -a android.intent.action.SHOW_ALARMS
echo

echo "### stock clock state"
r shell pm list packages -d | grep deskclock || echo "com.android.deskclock is enabled"
