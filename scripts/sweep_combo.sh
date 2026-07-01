#!/usr/bin/env bash
# Reset the ride to a known page, then walk the data-field layouts for one sweep combo.
#
# The Karoo ride pager only swipes forward reliably (backward swipes drop on dense
# pages), so this force-stops and relaunches the rideapp to snap back to page 1 (the
# map), steps forward once to the first data page (1x1n), then runs walk_layouts.sh.
#
# Usage: OUTDIR=screencaps/sweeps/<combo> scripts/sweep_combo.sh
#   OUTDIR  where captures land (passed through to walk_layouts.sh; required)
#
# Set the combo's design state FIRST (Karoo Label Size + Key Button Icons, and the
# matching Barberfish SET_DESIGN broadcast). This script only positions and captures.

set -euo pipefail

cd "$(dirname "$0")/.."

OUTDIR="${OUTDIR:?set OUTDIR to the combo capture dir, e.g. screencaps/sweeps/2_large_keysoff}"

RIDE=io.hammerhead.rideapp/.views.ride.RideActivity

size=$(adb shell wm size | awk -F'[ x]' '/Physical size/ {print $(NF-1), $NF}')
W=$(echo "$size" | awk '{print $1}')
H=$(echo "$size" | awk '{print $2}')

wake() { adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true; }
# Forward = next page (finger moves left); the only reliable swipe direction.
swipe_next() { adb shell input swipe $((W * 8 / 10)) $((H / 2)) $((W * 2 / 10)) $((H / 2)) 200; }

echo "reset ride to page 1 (map)"
adb shell am force-stop io.hammerhead.rideapp
sleep 1
wake
adb shell am start -n "$RIDE" >/dev/null
sleep 3

echo "step to first data page (1x1n)"
wake
swipe_next
sleep 1

echo "walk layouts -> $OUTDIR"
OUTDIR="$OUTDIR" scripts/walk_layouts.sh
