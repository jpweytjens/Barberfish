#!/usr/bin/env bash
# Walk the 9 Barberfish data pages on an active ride, capturing each with
# scripts/capture_layout.sh.
#
# Defaults assume the profile pages are ordered as set up:
#   1x1, 2x1, 3x1, 4x1, 5x1, 2x2, 3x2, 4x2, 5x2
# (rows x cols, matching how the user labeled them on-device).
#
# Usage: scripts/walk_layouts.sh [page1 page2 ...]
#
# Before running:
#   - Start a ride on the Karoo with the Barberfish profile active.
#   - Navigate to the FIRST page in the list (default: 1x1).
#   - Then run this script. It swipes right-to-left between captures.

set -euo pipefail

cd "$(dirname "$0")/.."

# Page order matches the user's Karoo profile (10 pages). 1x1 is split into two
# pages because a single cell can't show native + Barberfish side-by-side:
#   1x1n  = 1-cell page with native field
#   1x1b  = 1-cell page with Barberfish field
DEFAULT_PAGES=(1x1n 1x1b 2x1 3x1 4x1 5x1 2x2 3x2 4x2 5x2)
pages=("$@")
if [[ ${#pages[@]} -eq 0 ]]; then
    pages=("${DEFAULT_PAGES[@]}")
fi

# Get screen size so swipe coords work on both K2 (landscape) and K3 (portrait).
size=$(adb shell wm size | awk -F'[ x]' '/Physical size/ {print $(NF-1), $NF}')
W=$(echo "$size" | awk '{print $1}')
H=$(echo "$size" | awk '{print $2}')

if [[ -z "${W:-}" || -z "${H:-}" ]]; then
    echo "could not detect screen size from 'wm size'" >&2
    exit 1
fi

echo "screen: ${W}x${H}"

# Wake screen if dimmed/off. KEYCODE_WAKEUP is a no-op if already awake.
wake_screen() {
    adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
}

# Swipe from 80% width to 20% width along middle row (next page on Karoo ride).
swipe_next() {
    local x1=$((W * 8 / 10))
    local x2=$((W * 2 / 10))
    local y=$((H / 2))
    adb shell input swipe "$x1" "$y" "$x2" "$y" 200
}

# Quick stabilization helper between swipes and captures.
settle() { sleep 0.8; }

for i in "${!pages[@]}"; do
    page="${pages[$i]}"
    wake_screen
    if [[ $i -gt 0 ]]; then
        echo "-> swipe to next ($page)"
        swipe_next
        settle
    else
        echo "-> assuming you're already on $page"
        settle
    fi
    wake_screen
    scripts/capture_layout.sh "$page"
done

echo
echo "done. ${#pages[@]} pages captured under screencaps/"
echo "next: review dumpsys/logcat per page, then we tabulate."
