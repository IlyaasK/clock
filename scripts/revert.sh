#!/usr/bin/env bash
# Put the phone back the way it was: stock clock enabled, Clock removed.
#
# Usage: scripts/revert.sh [--keep-app]
set -euo pipefail

PKG=com.ilyaas.einkclock
STOCK=com.android.deskclock
ADB=(adb)
if [ -n "${ANDROID_SERIAL:-}" ]; then ADB=(adb -s "$ANDROID_SERIAL"); fi

echo "== re-enable the stock clock"
"${ADB[@]}" shell pm enable "$STOCK"

if [ "${1:-}" != "--keep-app" ]; then
  echo "== uninstall Clock"
  "${ADB[@]}" uninstall "$PKG" || true
else
  echo "== keeping $PKG installed"
fi

echo "== verify"
"${ADB[@]}" shell cmd package resolve-activity --brief -a android.intent.action.SHOW_ALARMS
