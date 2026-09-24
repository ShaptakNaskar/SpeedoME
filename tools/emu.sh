#!/usr/bin/env bash
# Emulator helper for verifying SpeedoME builds (used during development; see docs/progress.md).
#   tools/emu.sh start              boot the headless AVD (backgrounded) and wait for it
#   tools/emu.sh wait               wait for boot, disable animations, unlock
#   tools/emu.sh install [apk]      install the debug APK (or the given one)
#   tools/emu.sh launch             start SpeedoME Dev
#   tools/emu.sh shot NAME          screenshot to $SHOTS/NAME.png
#   tools/emu.sh tap "Text"         tap the first node whose text is exactly "Text"
#   tools/emu.sh hold "Text" [ms]   long-press a node
#   tools/emu.sh tapdesc "Desc"     tap a node by content description
#   tools/emu.sh rotate 0|1|2|3     force rotation (1 = landscape)
#   tools/emu.sh texts              list visible texts
#   tools/emu.sh crashes            recent crash-buffer lines
#   tools/emu.sh feed KMH SECONDS   drive the emulator GPS north-east at KMH (1 Hz geo fixes with speed)
#   tools/emu.sh setup-dev          grant permissions, unlock developer options, turn on the simulator
set -uo pipefail
E=${E:-emulator-5584}
AVD=${AVD:-Phone_67_A16}
PKG=${PKG:-com.sappy.SpeedoMe.debug}
SHOTS=${SHOTS:-/tmp/speedome-shots}
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK_DEFAULT="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
mkdir -p "$SHOTS"

a() { adb -s "$E" "$@"; }
dump() { a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; a shell cat /sdcard/ui.xml; }
center_of() {
  local attr=${2:-text}
  dump | grep -oE "$attr=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 \
    | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/' \
    | awk '{printf "%d %d\n", ($1+$3)/2, ($2+$4)/2}'
}
wait_boot() {
  timeout 480 bash -c "until [ \"\$(adb -s $E shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')\" = 1 ]; do sleep 3; done" || { echo "boot timeout"; exit 1; }
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do a shell settings put global $k 0; done
  a shell input keyevent KEYCODE_WAKEUP; a shell wm dismiss-keyguard >/dev/null 2>&1; echo booted
}

cmd=${1:-}; shift || true
case "$cmd" in
  start)
    port=${E#emulator-}
    nohup "$HOME/Android/Sdk/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot-save \
      -gpu swiftshader_indirect -port "$port" >"$SHOTS/emulator.log" 2>&1 &
    sleep 5; wait_boot ;;
  wait) wait_boot ;;
  install) a install -r "${1:-$APK_DEFAULT}" | tail -1 ;;
  launch) a shell am start -W -n "$PKG/com.sappy.speedome.MainActivity" | grep -E "Status" ;;
  shot) a exec-out screencap -p >"$SHOTS/$1.png"; echo "$SHOTS/$1.png" ;;
  tap) c=$(center_of "$1"); [ -z "$c" ] && { echo "not found: $1"; exit 1; }; a shell input tap $c ;;
  tapdesc) c=$(center_of "$1" content-desc); [ -z "$c" ] && { echo "not found: $1"; exit 1; }; a shell input tap $c ;;
  rotate) a shell settings put system accelerometer_rotation 0; a shell settings put system user_rotation "${1:-1}" ;;
  hold) c=$(center_of "$1"); [ -z "$c" ] && { echo "not found: $1"; exit 1; }; set -- $c "${2:-3000}"; a shell input swipe "$1" "$2" "$1" "$2" "$3" ;;
  texts) dump | grep -oE 'text="[^"]+"' | sed -E 's/text="(.*)"/\1/' ;;
  crashes) a logcat -d -b crash | tail -"${1:-20}" ;;
  swipe-up) a shell input swipe 540 2200 540 900 400 ;;
  swipe-down) a shell input swipe 540 900 540 2200 400 ;;
  feed)
    kmh=${1:-50}; n=${2:-60}
    python3 - "$kmh" "$n" <<'PY' | while read -r lon lat kn; do a emu geo fix "$lon" "$lat" 40 10 "$kn" >/dev/null; sleep 1; done
import math, sys
kmh, n = float(sys.argv[1]), int(sys.argv[2])
lat, lon, v = 35.6812, 139.7671, kmh / 3.6
for i in range(n):
    print(f"{lon:.7f} {lat:.7f} {v*1.943844:.2f}")
    lat += v * math.cos(math.radians(45)) / 111320
    lon += v * math.sin(math.radians(45)) / (111320 * math.cos(math.radians(lat)))
PY
    ;;
  setup-dev)
    for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS ACTIVITY_RECOGNITION; do a shell pm grant "$PKG" "android.permission.$p"; done
    "$0" launch >/dev/null; sleep 2; "$0" tap Settings >/dev/null
    for i in 1 2 3 4 5 6 7; do "$0" tap Version >/dev/null; done
    "$0" tap Simulator >/dev/null; "$0" tap Speed >/dev/null; echo "developer options + simulator on" ;;
  *) sed -n '2,14p' "$0"; exit 1 ;;
esac
